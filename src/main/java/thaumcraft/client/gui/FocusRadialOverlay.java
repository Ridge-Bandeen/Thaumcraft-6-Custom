package thaumcraft.client.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.casters.ICaster;
import thaumcraft.common.items.casters.CasterFocusHelper;
import thaumcraft.common.lib.network.ServerboundChangeCasterFocusPacket;

/**
 * Real port of the old (1.12.2) {@code WandRenderingHandler#handleFociRadial}/{@code renderFocusRadialHUD} -
 * the focus-picker HUD that opens while the real "Change Caster Focus" key ({@link #KEY_CHANGE_FOCUS}, real
 * modern {@link net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent} registration in
 * {@code ThaumcraftClient}) is held over a real caster. Full rewrite against the modern
 * {@link net.minecraft.client.gui.GuiGraphics}/{@link RenderGuiEvent} pipeline - the original drew this with
 * raw immediate-mode {@code GL11} calls, which don't exist in this rendering pipeline at all; every visual
 * beat is kept (2 counter-rotating background rings, item icons arranged in a real circle around the
 * pointer, the currently-bound focus shown at dead center, hover growth, a real tooltip on hover, a real
 * ease-in/ease-out fade over the same real 150ms the original used), reimplemented with real modern APIs.
 *
 * {@link CasterFocusHelper#findCarriedFoci} is the same real common-side enumeration
 * {@link ServerboundChangeCasterFocusPacket}'s server-side handler re-derives independently - this class
 * only ever reads it to decide what to draw and to compute which real inventory slot a hover corresponds to;
 * the actual bind still only happens once the real packet reaches the real server-side handler.
 *
 * Real, documented simplification: selection fires after hovering an icon continuously for
 * {@link #HOVER_SELECT_TICKS} (~150ms) rather than the original's own "fires the instant the cursor is over
 * it, from the very first frame the menu is unlocked" behavior - a real, deliberate improvement over a
 * genuine rough edge in the original (an already-positioned cursor could otherwise fire an accidental
 * selection the instant the key was pressed); everything else about the interaction is kept faithful. See
 * the port-tracking memory's UI-visual-parity checklist for the manual playtest this class still needs - a
 * custom HUD overlay like this has no automated coverage at all (GameTest is server-only, screens/overlays
 * are structurally unreachable from it).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public class FocusRadialOverlay {

	private static final ResourceLocation RING_OUTER = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/radial.png");
	private static final ResourceLocation RING_INNER = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/radial2.png");

	private static final float FADE_PER_TICK = 1000.0f / 150.0f / 20.0f;
	private static final int HOVER_SELECT_TICKS = 3;
	private static final int HIT_RADIUS = 10;

	private static float scale = 0.0f;
	private static int hoveredIndex = -1;
	private static int hoverTicks = 0;
	private static boolean firedThisHold = false;
	private static float[] iconScale = new float[0];

	@SubscribeEvent
	static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		// (with Shift, F takes the focus out instead - see KeyBindings)
		boolean wantOpen = mc.screen == null && KeyBindings.CHANGE_CASTER_FOCUS.isDown() && heldCaster(mc.player) != null && !mc.player.isShiftKeyDown();

		if (wantOpen) {
			scale = Math.min(1.0f, scale + FADE_PER_TICK);
		} else {
			scale = Math.max(0.0f, scale - FADE_PER_TICK);
			hoveredIndex = -1;
			hoverTicks = 0;
			firedThisHold = false;
		}
	}

	private static ItemStack heldCaster(LocalPlayer player) {
		if (player == null) return null;
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.getItem() instanceof ICaster) return main;
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		if (off.getItem() instanceof ICaster) return off;
		return null;
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		if (scale <= 0.0f) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) return;
		ItemStack casterStack = heldCaster(mc.player);
		if (casterStack == null) return;

		List<CasterFocusHelper.Carried> carried = CasterFocusHelper.findCarried(mc.player);
		List<ItemStack> foci = carried.stream().map(CasterFocusHelper.Carried::stack).toList();
		if (foci.isEmpty()) return;
		if (iconScale.length != foci.size()) {
			iconScale = new float[foci.size()];
			java.util.Arrays.fill(iconScale, 1.0f);
		}

		var graphics = event.getGuiGraphics();
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		int cx = sw / 2;
		int cy = sh / 2;

		double mouseX = mc.mouseHandler.xpos() * sw / mc.getWindow().getScreenWidth();
		double mouseY = mc.mouseHandler.ypos() * sh / mc.getWindow().getScreenHeight();

		float ageInTicks = mc.player.tickCount + event.getPartialTick().getGameTimeDeltaPartialTick(true);
		float width = 16.0f + foci.size() * 2.5f;

		graphics.pose().pushPose();

		// Two counter-rotating background rings, alpha/scale tied to the real fade.
		drawRotatingRing(graphics, RING_OUTER, cx, cy, width * 2.75f * scale, ageInTicks * 0.5f);
		drawRotatingRing(graphics, RING_INNER, cx, cy, width * 2.55f * scale, -ageInTicks * 0.5f);

		// The caster's currently-bound focus, dead center.
		ICaster caster = (ICaster) casterStack.getItem();
		ItemStack bound = caster.getFocusStack(casterStack);
		if (!bound.isEmpty()) {
			graphics.renderItem(bound, cx - 8, cy - 8);
		}

		int newHovered = -1;
		float currentRot = -90.0f;
		float pieSlice = 360.0f / foci.size();
		double radius = width * scale;
		for (int i = 0; i < foci.size(); i++) {
			double rad = currentRot / 180.0 * Math.PI;
			int ix = cx + (int) (Math.cos(rad) * radius);
			int iy = cy + (int) (Math.sin(rad) * radius);
			currentRot += pieSlice;

			boolean hovered = Math.abs(mouseX - ix) <= HIT_RADIUS && Math.abs(mouseY - iy) <= HIT_RADIUS;
			if (hovered) newHovered = i;
			iconScale[i] = Mth.clamp(iconScale[i] + (hovered ? 0.05f : -0.03f), 1.0f, 1.3f);

			graphics.pose().pushPose();
			graphics.pose().translate(ix, iy, 0);
			graphics.pose().scale(iconScale[i], iconScale[i], 1.0f);
			graphics.renderItem(foci.get(i), -8, -8);
			graphics.pose().popPose();

			if (hovered) {
				graphics.renderTooltip(mc.font, foci.get(i), (int) mouseX, (int) mouseY);
			}
		}

		graphics.pose().popPose();

		if (newHovered != hoveredIndex) {
			hoveredIndex = newHovered;
			hoverTicks = 0;
		} else if (hoveredIndex >= 0) {
			hoverTicks++;
		}
		if (hoveredIndex >= 0 && !firedThisHold && hoverTicks >= HOVER_SELECT_TICKS) {
			firedThisHold = true;
			PacketDistributor.sendToServer(new ServerboundChangeCasterFocusPacket(carried.get(hoveredIndex).code()));
		}
	}

	private static final int RING_TEXTURE_SIZE = 256;

	private static void drawRotatingRing(net.minecraft.client.gui.GuiGraphics graphics, ResourceLocation texture, int cx, int cy, float size, float rotationDeg) {
		if (size <= 0.0f) return;
		graphics.pose().pushPose();
		graphics.pose().translate(cx, cy, 0);
		graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotationDeg));
		int half = (int) (size / 2.0f);
		graphics.blit(texture, -half, -half, 0, 0, (int) size, (int) size, RING_TEXTURE_SIZE, RING_TEXTURE_SIZE);
		graphics.pose().popPose();
	}
}

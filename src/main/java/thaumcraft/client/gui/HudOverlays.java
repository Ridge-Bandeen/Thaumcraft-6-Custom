package thaumcraft.client.gui;

import java.text.DecimalFormat;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.ICaster;
import thaumcraft.common.config.ThaumcraftConfig;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.items.tools.ItemSanityChecker;
import thaumcraft.common.items.tools.ItemThaumometer;
import thaumcraft.common.lib.network.ClientboundAuraPacket;

/**
 * The original {@code HudHandler#renderHuds}: the gauges in the top left corner while a caster, a Thaumometer or a
 * Sanity Checker is held (main hand first, then off hand, each kind once, stacked downwards), drawn from
 * {@code textures/gui/hud.png} exactly as there:
 * <ul>
 *   <li>the caster's vis dial - the aura's vis against its base, the focus in the middle (or the block a picking focus
 *       holds, with how many of it you carry); sneak to read the numbers and the focus's vis cost. The
 *       {@code dialBottom} option moves it to the bottom left.</li>
 *   <li>the Thaumometer's aura gauge - vis and flux bars and the base marker; sneak for the numbers.</li>
 *   <li>the Sanity Checker's warp gauge - temporary, normal and permanent warp, a shaking skull at 100.</li>
 * </ul>
 * The aura comes from the server ({@link ClientboundAuraPacket}) while a caster or Thaumometer is held.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class HudOverlays {

	private static final ResourceLocation HUD = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/gui/hud.png");
	private static final DecimalFormat FORMAT = new DecimalFormat("#######.#");

	private static short auraBase;
	private static float auraVis;
	private static float auraFlux;

	private static ItemStack lastPicked = ItemStack.EMPTY;
	private static int lastCount;

	private HudOverlays() {
	}

	public static void handleAura(ClientboundAuraPacket packet) {
		auraBase = packet.base();
		auraVis = packet.vis();
		auraFlux = packet.flux();
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.screen != null || mc.options.hideGui) return;
		GuiGraphics g = event.getGuiGraphics();
		float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		boolean rC = false, rT = false, rS = false;
		int start = 0;
		ItemStack hand = player.getMainHandItem();
		for (int a = 0; a < 2; ++a) {
			if (!hand.isEmpty()) {
				if (!rC && hand.getItem() instanceof ICaster) {
					renderCaster(mc, g, player, hand);
					rC = true;
					if (!ThaumcraftConfig.client(ThaumcraftConfig.DIAL_BOTTOM)) start += 33;
				} else if (!rT && hand.getItem() instanceof ItemThaumometer) {
					renderThaumometer(mc, g, player, partial, start);
					rT = true;
					start += 80;
				} else if (!rS && hand.getItem() instanceof ItemSanityChecker) {
					renderSanity(g, player, start);
					rS = true;
					start += 75;
				}
			}
			hand = player.getOffhandItem();
		}
	}

	/** The original's {@code UtilsFX.drawTexturedQuad}: a {@code w}x{@code h} piece of the 256x256 HUD sheet at (u, v),
	 * floats throughout - several gauges draw fractional heights. */
	private static void quad(GuiGraphics g, float x, float y, float u, float v, float w, float h) {
		float s = 1.0f / 256.0f;
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, HUD);
		RenderSystem.enableBlend();
		Matrix4f m = g.pose().last().pose();
		BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		b.addVertex(m, x, y + h, 0).setUv(u * s, (v + h) * s);
		b.addVertex(m, x + w, y + h, 0).setUv((u + w) * s, (v + h) * s);
		b.addVertex(m, x + w, y, 0).setUv((u + w) * s, v * s);
		b.addVertex(m, x, y, 0).setUv(u * s, v * s);
		BufferUploader.drawWithShader(b.buildOrThrow());
	}

	private static void color(float r, float g, float b, float a) {
		RenderSystem.setShaderColor(r, g, b, a);
	}

	// ---- caster ----

	private static void renderCaster(Minecraft mc, GuiGraphics g, LocalPlayer player, ItemStack wandstack) {
		ICaster wand = (ICaster) wandstack.getItem();
		var pose = g.pose();
		pose.pushPose();
		// the original reset the matrix here, so the dial ignores the stacking offset: always the corner
		pose.translate(0.0f, ThaumcraftConfig.client(ThaumcraftConfig.DIAL_BOTTOM) ? mc.getWindow().getGuiScaledHeight() - 32 : 0, 0.0f);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		color(1f, 1f, 1f, 1f);
		pose.pushPose();
		pose.scale(0.5f, 0.5f, 0.5f);
		quad(g, 0, 0, 0, 0, 64, 64);
		pose.popPose();
		pose.translate(16.0f, 16.0f, 0.0f);
		int max = auraBase;
		int amt = (int) auraVis;
		ItemStack focusStack = wand.getFocusStack(wandstack);
		ItemFocus focus = focusStack != null && focusStack.getItem() instanceof ItemFocus f ? f : null;

		pose.pushPose();
		pose.translate(16.0f, -10.0f, 0.0f);
		pose.scale(0.5f, 0.5f, 0.5f);
		int loc = max > 0 ? (int) (30.0f * amt / max) : 0;
		int ac = Aspect.ENERGY.getColor();
		color((ac >> 16 & 255) / 255f, (ac >> 8 & 255) / 255f, (ac & 255) / 255f, 0.8f);
		quad(g, -4, 35 - loc, 104, 0, 8, loc);
		color(1f, 1f, 1f, 1f);
		quad(g, -8, -3, 72, 0, 16, 42);
		if (player.isShiftKeyDown()) {
			pose.pushPose();
			pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90.0f));
			g.drawString(mc.font, FORMAT.format(amt), -32, -4, 0xFFFFFF);
			pose.popPose();
			if (focus != null && focus.getVisCost(focusStack) > 0.0f) {
				float mod = wand.getConsumptionModifier(wandstack, player, false);
				String msg = FORMAT.format(focus.getVisCost(focusStack) * mod);
				g.drawString(mc.font, msg, -32 - mc.font.width(msg) / 2, 32, 0xFFFFFF);
			}
		}
		pose.popPose();

		if (focus != null) {
			ItemStack picked = wand.getPickedBlock(player.getInventory().getSelected());
			if (picked != null && !picked.isEmpty()) {
				renderPicked(mc, g, player, picked);
			} else {
				g.renderItem(focusStack, -8, -8);
			}
		}
		RenderSystem.disableBlend();
		pose.popPose();
	}

	/** The original's {@code renderWandTradeHud}: the picked block and how many of it you carry. */
	private static void renderPicked(Minecraft mc, GuiGraphics g, LocalPlayer player, ItemStack picked) {
		int amount = lastCount;
		if (lastPicked.isEmpty() || player.tickCount % 5 == 0 || !ItemStack.isSameItem(picked, lastPicked)) {
			amount = 0;
			for (ItemStack is : player.getInventory().items) {
				if (!is.isEmpty() && ItemStack.isSameItem(is, picked)) amount += is.getCount();
			}
			lastPicked = picked.copy();
		}
		lastCount = amount;
		g.renderItem(picked, -8, -8);
		var pose = g.pose();
		pose.pushPose();
		String am = String.valueOf(amount);
		int sw = mc.font.width(am);
		pose.translate(0.0f, -mc.font.lineHeight, 500.0f);
		pose.scale(0.5f, 0.5f, 0.5f);
		for (int a = -1; a <= 1; ++a) {
			for (int b = -1; b <= 1; ++b) {
				if ((a == 0 || b == 0) && (a != 0 || b != 0)) g.drawString(mc.font, am, a + 16 - sw, b + 24, 0, false);
			}
		}
		g.drawString(mc.font, am, 16 - sw, 24, 0xFFFFFF, false);
		pose.popPose();
	}

	// ---- thaumometer ----

	private static void renderThaumometer(Minecraft mc, GuiGraphics g, LocalPlayer player, float partial, int shifty) {
		float base = Mth.clamp(auraBase / 525.0f, 0.0f, 1.0f);
		float vis = Mth.clamp(auraVis / 525.0f, 0.0f, 1.0f);
		float flux = Mth.clamp(auraFlux / 525.0f, 0.0f, 1.0f);
		float count = player.tickCount + partial;
		float count2 = player.tickCount / 3.0f + partial;
		if (flux + vis > 1.0f) {
			float m = 1.0f / (flux + vis);
			base *= m;
			vis *= m;
			flux *= m;
		}
		var pose = g.pose();
		pose.pushPose();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		pose.translate(2.0f, shifty, 0.0f);
		float start = 10.0f + (1.0f - vis) * 64.0f;
		if (vis > 0.0f) {
			pose.pushPose();
			color(0.7f, 0.4f, 0.9f, 1.0f);
			pose.translate(5.0f, start, 0.0f);
			pose.scale(1.0f, vis, 1.0f);
			quad(g, 0, 0, 88, 56, 8, 64);
			pose.popPose();
			pose.pushPose();
			RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
			color(1f, 1f, 1f, 0.5f);
			pose.translate(5.0f, start, 0.0f);
			quad(g, 0, 0, 96, 56 + count % 64.0f, 8, vis * 64.0f);
			RenderSystem.defaultBlendFunc();
			pose.popPose();
			color(1f, 1f, 1f, 1f);
			if (player.isShiftKeyDown()) {
				pose.pushPose();
				pose.translate(16.0f, start, 0.0f);
				pose.scale(0.5f, 0.5f, 0.5f);
				g.drawString(mc.font, FORMAT.format(auraVis), 0, 0, 15641343);
				pose.popPose();
			}
		}
		if (flux > 0.0f) {
			start = 10.0f + (1.0f - flux - vis) * 64.0f;
			pose.pushPose();
			color(0.25f, 0.1f, 0.3f, 1.0f);
			pose.translate(5.0f, start, 0.0f);
			pose.scale(1.0f, flux, 1.0f);
			quad(g, 0, 0, 88, 56, 8, 64);
			pose.popPose();
			pose.pushPose();
			RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
			color(0.7f, 0.4f, 1.0f, 0.5f);
			pose.translate(5.0f, start, 0.0f);
			quad(g, 0, 0, 104, 120 - count2 % 64.0f, 8, flux * 64.0f);
			RenderSystem.defaultBlendFunc();
			pose.popPose();
			color(1f, 1f, 1f, 1f);
			if (player.isShiftKeyDown()) {
				pose.pushPose();
				pose.translate(16.0f, start - 4.0f, 0.0f);
				pose.scale(0.5f, 0.5f, 0.5f);
				g.drawString(mc.font, FORMAT.format(auraFlux), 0, 0, 11145659);
				pose.popPose();
			}
		}
		color(1f, 1f, 1f, 1f);
		quad(g, 1, 1, 72, 48, 16, 80);
		start = 8.0f + (1.0f - base) * 64.0f;
		quad(g, 2, start, 117, 61, 14, 5);
		RenderSystem.disableBlend();
		pose.popPose();
	}

	// ---- sanity checker ----

	private static void renderSanity(GuiGraphics g, LocalPlayer player, int shifty) {
		var pose = g.pose();
		pose.pushPose();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		pose.translate(0.0f, shifty, 0.0f);
		color(1f, 1f, 1f, 1f);
		quad(g, 1, 1, 152, 0, 20, 76);
		IPlayerWarp warp = ThaumcraftCapabilities.getWarp(player);
		int p = warp.get(IPlayerWarp.EnumWarpType.PERMANENT);
		int s = warp.get(IPlayerWarp.EnumWarpType.NORMAL);
		int t = warp.get(IPlayerWarp.EnumWarpType.TEMPORARY);
		float tw = p + s + t;
		float mod = 1.0f;
		if (tw > 100.0f) {
			mod = 100.0f / tw;
			tw = 100.0f;
		}
		int gap = (int) ((100.0f - tw) / 100.0f * 48.0f);
		int wt = (int) (t / 100.0f * 48.0f * mod);
		int ws = (int) (s / 100.0f * 48.0f * mod);
		// the original passed these as (u, v, w, h) where the last is really a bottom edge - kept, as drawn there
		if (t > 0) {
			color(1.0f, 0.5f, 1.0f, 1.0f);
			quad(g, 7, 21 + gap, 200, gap, 8, wt + gap);
		}
		if (s > 0) {
			color(0.75f, 0.0f, 0.75f, 1.0f);
			quad(g, 7, 21 + wt + gap, 200, wt + gap, 8, wt + ws + gap);
		}
		if (p > 0) {
			color(0.5f, 0.0f, 0.5f, 1.0f);
			quad(g, 7, 21 + wt + ws + gap, 200, wt + ws + gap, 8, 48);
		}
		color(1f, 1f, 1f, 1f);
		quad(g, 1, 1, 176, 0, 20, 76);
		if (tw >= 100.0f) {
			pose.pushPose();
			pose.scale(0.75f, 0.75f, 1.0f);
			pose.translate(player.getRandom().nextInt(2), player.getRandom().nextInt(2), 0.0f);
			quad(g, 3, 3, 216, 0, 20, 16);
			pose.popPose();
		}
		RenderSystem.disableBlend();
		pose.popPose();
	}
}

package thaumcraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.items.tools.ItemSanityChecker;

/**
 * Real port of the old (1.12.2) {@code HudHandler#renderSanityHud} - a small readout of the real Warp
 * breakdown (Permanent/Normal/Temporary, via {@link IPlayerWarp}, the same real source
 * {@link thaumcraft.common.lib.events.WarpEvents}/{@code GearTooltipEvents} already read) shown while holding
 * a real {@link ItemSanityChecker}. Real, deliberate simplification: the original drew a textured vertical
 * gauge (a background frame texture plus 3 colored fill segments); this draws 3 plain colored bars plus
 * numeric labels instead via {@link net.minecraft.client.gui.GuiGraphics#fill}/{@code drawString} - same
 * "drop the custom-textured widget, keep the real data" precedent {@link FocusRadialOverlay}'s own doc
 * comment already established for HUD elements this session. See the port-tracking memory's UI-visual-parity
 * checklist - like every custom HUD overlay, this has no automated coverage (GameTest is server-only).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public class SanityCheckerOverlay {

	private static final int BAR_WIDTH = 60;
	private static final int BAR_HEIGHT = 8;
	private static final int MARGIN = 10;

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.screen != null) return;
		if (!heldChecker(player)) return;

		IPlayerWarp warp = ThaumcraftCapabilities.getWarp(player);
		int permanent = warp.get(IPlayerWarp.EnumWarpType.PERMANENT);
		int normal = warp.get(IPlayerWarp.EnumWarpType.NORMAL);
		int temporary = warp.get(IPlayerWarp.EnumWarpType.TEMPORARY);
		int total = permanent + normal + temporary;

		var graphics = event.getGuiGraphics();
		int x = MARGIN;
		int y = mc.getWindow().getGuiScaledHeight() / 2 - (BAR_HEIGHT * 3 + 6) / 2;

		drawWarpBar(graphics, x, y, permanent, total, 0x8B00FF, "warp.text.permanent");
		drawWarpBar(graphics, x, y + BAR_HEIGHT + 3, normal, total, 0xB266FF, "warp.text.normal");
		drawWarpBar(graphics, x, y + (BAR_HEIGHT + 3) * 2, temporary, total, 0xD9B3FF, "warp.text.temporary");
	}

	private static void drawWarpBar(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int value, int total, int color, String labelKey) {
		graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0x80000000);
		int filled = total <= 0 ? 0 : Math.round((float) value / Math.max(total, 100) * BAR_WIDTH);
		if (filled > 0) {
			graphics.fill(x, y, x + Math.min(filled, BAR_WIDTH), y + BAR_HEIGHT, 0xFF000000 | color);
		}
		Minecraft mc = Minecraft.getInstance();
		graphics.drawString(mc.font, Component.translatable(labelKey).append(": " + value), x + BAR_WIDTH + 4, y, 0xFFFFFF);
	}

	private static boolean heldChecker(LocalPlayer player) {
		ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (main.getItem() instanceof ItemSanityChecker) return true;
		ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
		return off.getItem() instanceof ItemSanityChecker;
	}
}

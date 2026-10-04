package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import thaumcraft.common.container.FocusPouchMenu;

/**
 * Replaces the old (1.12.2) {@code GuiFocusPouch}, as the original: the {@code gui_focuspouch.png} background (175x232)
 * with its 18 focus slots and no labels, a lock icon over the hotbar slot the pouch sits in, and the screen closes if
 * that slot empties.
 */
public class GuiFocusPouch extends AbstractContainerScreen<FocusPouchMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_focuspouch.png");

	public GuiFocusPouch(FocusPouchMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 175;
		imageHeight = 232;
		blockSlot = playerInventory.selected;
	}

	private final int blockSlot;

	@Override
	protected void containerTick() {
		super.containerTick();
		if (minecraft != null && minecraft.player != null && minecraft.player.getInventory().getItem(blockSlot).isEmpty()) onClose();
	}

	/** No labels (the original drew none) - only the lock icon over the pouch's hotbar slot, above the item. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 300);
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		graphics.blit(TEXTURE, 8 + blockSlot * 18, 209, 240, 0, 16, 16);
		com.mojang.blaze3d.systems.RenderSystem.disableBlend();
		graphics.pose().popPose();
	}

	/** {@code AbstractContainerScreen#render} doesn't draw item tooltips by itself in 1.21 - vanilla's own container
	 * screens call {@code renderTooltip} at the end of their own {@code render}. Missing here until the 2026-09-25
	 * playtest found no tooltips on this screen. */
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
	}
}

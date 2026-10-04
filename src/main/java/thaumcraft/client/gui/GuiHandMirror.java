package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import thaumcraft.common.container.HandMirrorMenu;

/**
 * Replaces the old (1.12.2) {@code GuiHandMirror}, as the original: the {@code gui_handmirror.png} background with its
 * single send slot and no labels, and a lock icon over the hotbar slot the mirror sits in.
 */
public class GuiHandMirror extends AbstractContainerScreen<HandMirrorMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_handmirror.png");

	public GuiHandMirror(HandMirrorMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 176;
		imageHeight = 166;
		heldSlot = playerInventory.selected;
	}

	private final int heldSlot;

	/** No labels (the original drew none) - only the lock icon over the mirror's hotbar slot, above the item. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 300);
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		graphics.blit(TEXTURE, 8 + heldSlot * 18, 142, 240, 0, 16, 16);
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

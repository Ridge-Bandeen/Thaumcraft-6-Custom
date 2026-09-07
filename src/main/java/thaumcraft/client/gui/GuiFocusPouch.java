package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import thaumcraft.common.container.FocusPouchMenu;

/**
 * Replaces the old (1.12.2) {@code GuiFocusPouch}. Same "lighter, real simplification" treatment every
 * client-only screen this session has gotten - see {@code GuiPechTrade}'s own doc comment. Real background
 * texture ({@code gui_focuspouch.png}, 175x232) and real 18 focus slots; the original's own extra "which
 * hotbar slot is this pouch in" icon overlay is dropped - a purely decorative readout of state the player
 * already knows (they just clicked the item), same "drop the dynamic overlay, keep the real mechanic"
 * precedent as every other device screen this session.
 */
public class GuiFocusPouch extends AbstractContainerScreen<FocusPouchMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_focuspouch.png");

	public GuiFocusPouch(FocusPouchMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 175;
		imageHeight = 232;
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
	}
}

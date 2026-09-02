package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.container.FocalManipulatorMenu;

/**
 * Replaces the old (1.12.2) {@code GuiFocalManipulator} - reuses the original's background texture and its
 * 231x231 size, same minimal "background + slots only" scope {@code GuiArcaneWorkbench} already established
 * for its own device. The original's node-graph canvas (drag-to-place/connect nodes, a settings spinner,
 * two more texture sheets for its widgets) isn't ported yet - see {@code TileFocalManipulator}'s own doc
 * comment for the rest of the arc this feeds into.
 */
public class GuiFocalManipulator extends AbstractContainerScreen<FocalManipulatorMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_wandtable.png");

	public GuiFocalManipulator(FocalManipulatorMenu menu, Inventory playerInventory, net.minecraft.network.chat.Component title) {
		super(menu, playerInventory, title);
		imageWidth = 231;
		imageHeight = 231;
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
	}
}

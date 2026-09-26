package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.container.SmelterMenu;

/**
 * Replaces the old (1.12.2) {@code GuiSmelter}, at the original's layout: the fuel flame above the fuel slot burns
 * down, a vertical bar beside the input fills as the current item cooks, and a tall vial on the left shows how much
 * essentia (vis) the furnace is holding waiting for an alembic. No labels, like the original.
 */
public class SmelterScreen extends AbstractContainerScreen<SmelterMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_smelter.png");

	public SmelterScreen(SmelterMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		int burn = menu.getBurnTimeScaled(20);
		if (burn > 0) {
			graphics.blit(TEXTURE, leftPos + 80, topPos + 26 + 20 - burn, 176, 20 - burn, 16, burn);
		}
		int cook = menu.getCookProgressScaled(46);
		graphics.blit(TEXTURE, leftPos + 106, topPos + 13 + 46 - cook, 216, 46 - cook, 9, cook);
		int vis = menu.getVisScaled(48);
		graphics.blit(TEXTURE, leftPos + 61, topPos + 12 + 48 - vis, 200, 48 - vis, 8, vis);
		graphics.blit(TEXTURE, leftPos + 60, topPos + 8, 232, 0, 10, 55);
	}
}

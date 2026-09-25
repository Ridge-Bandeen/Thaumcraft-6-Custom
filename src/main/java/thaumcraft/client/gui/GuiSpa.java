package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import thaumcraft.common.container.SpaMenu;

/**
 * Replaces the old (1.12.2) {@code GuiSpa}. Same "lighter, real simplification" treatment every client-only
 * screen this session has gotten - see {@link GuiPechTrade}'s own doc comment. Real background texture, real
 * Bath Salts slot; the original's own animated tank-fill gauge is dropped (a visual-only readout, same
 * "drop the dynamic render, keep the real mechanic" precedent as every other device this session).
 */
public class GuiSpa extends AbstractContainerScreen<SpaMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_spa.png");

	public GuiSpa(SpaMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 176;
		imageHeight = 166;
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

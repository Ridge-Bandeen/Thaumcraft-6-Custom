package thaumcraft.client.gui;

import java.awt.Color;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.container.PotionSprayerMenu;
import thaumcraft.common.tiles.devices.TilePotionSprayer;

/**
 * Replaces the old (1.12.2) {@code GuiPotionSprayer}, at the original's layout (192x233): a tall glass tube on the
 * right whose liquid - tinted with the loaded potion's colour and scrolling - stands one segment high per stored charge
 * (up to 8); and on the left the essentia one charge costs, as aspect icons with their amounts, each over a small
 * vertical bar showing how much of it the sprayer has drawn in so far.
 */
public class PotionSprayerScreen extends AbstractContainerScreen<PotionSprayerMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_potion_sprayer.png");

	private final TilePotionSprayer tile;

	public PotionSprayerScreen(PotionSprayerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.tile = menu.getTile();
		imageWidth = 192;
		imageHeight = 233;
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
		if (tile == null) return;

		int charges = tile.getCharges();
		if (charges > 0) {
			Color c = new Color(tile.getColor());
			graphics.setColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1f);
			int height = charges * 9;
			int scroll = (int) ((minecraft != null && minecraft.player != null ? minecraft.player.tickCount : 0) % (256 - height));
			graphics.blit(TEXTURE, leftPos + 128, topPos + 36 + (8 - charges) * 9, 232, scroll, 8, height);
			graphics.setColor(1f, 1f, 1f, 1f);
		}
		drawAspects(graphics);
		graphics.blit(TEXTURE, leftPos + 125, topPos + 28, 205, 28, 14, 88);
	}

	private void drawAspects(GuiGraphics graphics) {
		AspectList recipe = tile.getRecipe();
		AspectList progress = tile.getRecipeProgress();
		int pos = 0;
		for (Aspect aspect : recipe.getAspectsSortedByName()) {
			int x = leftPos + 96 + 22 * (pos % 2);
			int y = topPos + 46 + 16 * (pos / 2);
			graphics.setColor(0.2f, 0.2f, 0.2f, 1f);
			graphics.blit(TEXTURE, x, y - 14, 192, 56, 2, 14);
			int filled = Math.min(14, (int) (progress.getAmount(aspect) / (float) recipe.getAmount(aspect) * 14.0f));
			Color c = new Color(aspect.getColor());
			graphics.setColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1f);
			graphics.blit(TEXTURE, x, y - filled, 192, 56, 2, filled);
			graphics.setColor(1f, 1f, 1f, 1f);
			pos++;
		}
		pos = 0;
		for (Aspect aspect : recipe.getAspectsSortedByName()) {
			drawTag(graphics, leftPos + 79 + 22 * (pos % 2), topPos + 31 + 16 * (pos / 2), aspect, recipe.getAmount(aspect));
			pos++;
		}
	}

	private void drawTag(GuiGraphics graphics, int x, int y, Aspect aspect, int amount) {
		ResourceLocation image = aspect.getImage();
		if (image != null) {
			Color c = new Color(aspect.getColor());
			graphics.setColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1f);
			graphics.blit(image, x, y, 0, 0, 16, 16, 16, 16);
			graphics.setColor(1f, 1f, 1f, 1f);
		}
		ClientAspectsTooltip.drawAmount(graphics, font, x, y, 200, amount);
	}
}

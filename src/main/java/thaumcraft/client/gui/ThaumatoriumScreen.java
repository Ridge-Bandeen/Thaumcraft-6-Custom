package thaumcraft.client.gui;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.common.container.ThaumatoriumMenu;
import thaumcraft.common.registry.ThaumcraftSounds;
import thaumcraft.common.tiles.crafting.TileThaumatorium;

/**
 * Replaces the old (1.12.2) {@code GuiThaumatorium}, at the original's layout: the catalyst slot, a scrollable
 * 2-wide list of the recipes on offer (three rows visible; the arrows scroll), the ones you have selected
 * highlighted, and beside them the aspects of the selected recipe it is currently working on - cycling once a
 * second when several are selected - as icons with their amounts over small fill bars showing how much essentia
 * the device already holds. When brain-box upgrades allow more than one recipe, a "selected/allowed" counter sits
 * under the slot. Clicking a recipe icon selects or deselects it (a menu-button click the server validates).
 */
public class ThaumatoriumScreen extends AbstractContainerScreen<ThaumatoriumMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_thaumatorium.png");
	private static final int VISIBLE = 6;

	private final TileThaumatorium tile;
	private List<RecipeHolder<CrucibleRecipe>> listed = List.of();
	private long lastRefresh;
	private int row;

	public ThaumatoriumScreen(ThaumatoriumMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.tile = menu.getTile();
		imageWidth = 175;
		imageHeight = 216;
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		// the original drew no labels on this screen
	}

	private void refresh() {
		long now = System.currentTimeMillis();
		if (tile == null || minecraft == null || minecraft.level == null || now < lastRefresh) return;
		lastRefresh = now + 500L;
		listed = tile.listedRecipes(minecraft.level, minecraft.player);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		refresh();
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
		int index = firstVisible();
		for (int i = 0; i < VISIBLE && index + i < listed.size(); i++) {
			int x = leftPos + 48 + (i % 2) * 16;
			int y = topPos + 56 + (i / 2) * 16;
			if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
				graphics.renderTooltip(font, listed.get(index + i).value().getRecipeOutput(), mouseX, mouseY);
				break;
			}
		}
	}

	private int firstVisible() {
		int maxRow = Math.max(0, (int) Math.ceil(listed.size() / 2.0) - 3);
		row = Math.max(0, Math.min(row, maxRow));
		return row * 2;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		if (tile == null) return;
		long time = System.currentTimeMillis();

		int maxRow = Math.max(0, (int) Math.ceil(listed.size() / 2.0) - 3);
		if (listed.size() > VISIBLE) {
			if (row > 0) graphics.blit(TEXTURE, leftPos + 82, topPos + 56, 176, 56, 8, 11);
			if (row < maxRow) graphics.blit(TEXTURE, leftPos + 82, topPos + 93, 176, 93, 8, 11);
		}

		drawAspects(graphics, time);

		if (tile.getMaxRecipes() > 1) {
			graphics.pose().pushPose();
			graphics.pose().translate(leftPos + 64, topPos + 48, 0);
			graphics.pose().scale(0.5f, 0.5f, 1.0f);
			String text = tile.getSelected().size() + "/" + tile.getMaxRecipes();
			graphics.drawString(font, text, -font.width(text) / 2, 0, 0xFFFFFF, false);
			graphics.pose().popPose();
		}

		drawOutputs(graphics, time);
	}

	private Optional<CrucibleRecipe> cyclingRecipe(long time) {
		List<ResourceLocation> sel = tile.getSelected();
		if (sel.isEmpty() || minecraft == null || minecraft.level == null) return Optional.empty();
		ResourceLocation id = sel.get((int) (time / 1000L % sel.size()));
		return minecraft.level.getRecipeManager().byKey(id).map(h -> h.value() instanceof CrucibleRecipe r ? r : null);
	}

	private void drawAspects(GuiGraphics graphics, long time) {
		Optional<CrucibleRecipe> recipe = cyclingRecipe(time);
		if (recipe.isEmpty()) return;
		Aspect[] aspects = recipe.get().getAspects().getAspectsSortedByName();
		int count = 0;
		int px = 0;
		int py = 0;
		for (Aspect aspect : aspects) {
			int have = tile.getAspects().getAmount(aspect);
			int need = recipe.get().getAspects().getAmount(aspect);
			graphics.blit(TEXTURE, leftPos + 98 + 16 * px, topPos + 40 + 20 * py, 176, 4, 12, 3);
			int fill = Math.min(12, (int) (have / (float) need * 12.0f));
			Color c = new Color(aspect.getColor());
			graphics.setColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1f);
			graphics.blit(TEXTURE, leftPos + 98 + 16 * px, topPos + 40 + 20 * py, 176, 0, fill, 3);
			graphics.setColor(1f, 1f, 1f, 1f);
			if (++px > 1) {
				px = 0;
				py++;
			}
			if (++count >= 8) break;
		}
		count = 0;
		px = 0;
		py = 0;
		for (Aspect aspect : aspects) {
			drawTag(graphics, leftPos + 96 + 16 * px, topPos + 24 + 20 * py, aspect, recipe.get().getAspects().getAmount(aspect));
			if (++px > 1) {
				px = 0;
				py++;
			}
			if (++count >= 8) break;
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

	private void drawOutputs(GuiGraphics graphics, long time) {
		int index = firstVisible();
		List<ResourceLocation> sel = tile.getSelected();
		ResourceLocation cycling = sel.isEmpty() ? null : sel.get((int) (time / 1000L % sel.size()));
		for (int i = 0; i < VISIBLE && index + i < listed.size(); i++) {
			RecipeHolder<CrucibleRecipe> holder = listed.get(index + i);
			int x = leftPos + 48 + (i % 2) * 16;
			int y = topPos + 56 + (i / 2) * 16;
			if (sel.contains(holder.id())) {
				graphics.setColor(1f, 1f, 1f, 0.7f);
				graphics.blit(TEXTURE, x, y, 176, 8, 16, 16);
				if (sel.size() > 1 && holder.id().equals(cycling)) {
					graphics.blit(TEXTURE, x, y, 176, 8, 16, 16);
				}
				graphics.setColor(1f, 1f, 1f, 1f);
			}
			ItemStack out = holder.value().getRecipeOutput();
			graphics.renderItem(out, x, y);
			graphics.renderItemDecorations(font, out, x, y);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && minecraft != null && minecraft.level != null && minecraft.gameMode != null) {
			int index = firstVisible();
			for (int i = 0; i < VISIBLE && index + i < listed.size(); i++) {
				int x = leftPos + 48 + (i % 2) * 16;
				int y = topPos + 56 + (i / 2) * 16;
				if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
					int recipeIndex = TileThaumatorium.allRecipes(minecraft.level).indexOf(listed.get(index + i));
					if (recipeIndex >= 0) {
						minecraft.gameMode.handleInventoryButtonClick(menu.containerId, recipeIndex);
						play(ThaumcraftSounds.HHON.get(), 0.3f);
						lastRefresh = 0L;
					}
					return true;
				}
			}
			int maxRow = Math.max(0, (int) Math.ceil(listed.size() / 2.0) - 3);
			if (listed.size() > VISIBLE) {
				if (row > 0 && inside(mouseX, mouseY, leftPos + 82, topPos + 56, 8, 11)) {
					row--;
					play(ThaumcraftSounds.CLACK.get(), 0.4f);
					return true;
				}
				if (row < maxRow && inside(mouseX, mouseY, leftPos + 82, topPos + 93, 8, 11)) {
					row++;
					play(ThaumcraftSounds.CLACK.get(), 0.4f);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	private void play(net.minecraft.sounds.SoundEvent sound, float volume) {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, volume));
	}
}

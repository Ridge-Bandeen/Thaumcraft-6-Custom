package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import thaumcraft.common.container.PechMenu;
import thaumcraft.common.entities.monster.EntityPech;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * Replaces the old (1.12.2) {@code GuiPech}. Restored to the original's own layout 2026-09-25 after the first
 * playtest (the plain {@code Button} stand-in sat over the panel art, and no item tooltips showed):
 * <ul>
 * <li>There is no button widget - the trade control is the purple "?" swirl printed in the panel texture at
 * (67, 24). Once the input slot holds a valued item and all 4 output slots are empty, the texture's 25x25
 * highlight at (176, 0) is drawn over it and clicking that region sends {@code clickMenuButton} id 0 and plays
 * the {@code pech_dice} sound (the original's own client-side {@code playButton}).</li>
 * <li>{@code AbstractContainerScreen#render} does not draw item tooltips by itself in 1.21 - the screen has to
 * call {@code renderTooltip} at the end, as vanilla's own container screens do (the original's own
 * {@code renderHoveredToolTip}).</li>
 * </ul>
 */
public class GuiPechTrade extends AbstractContainerScreen<PechMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_pech.png");

	private static final int ICON_X = 67;
	private static final int ICON_Y = 24;
	private static final int ICON_SIZE = 25;

	public GuiPechTrade(PechMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 175;
		imageHeight = 232;
	}

	/** As the original: no title or inventory label on this screen. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
	}

	/** The original's own enable condition: a valued item in the input and nothing in any output slot. */
	private boolean canTrade() {
		ItemStack input = menu.slots.get(0).getItem();
		return EntityPech.isValuedItem(input)
				&& menu.slots.get(1).getItem().isEmpty() && menu.slots.get(2).getItem().isEmpty()
				&& menu.slots.get(3).getItem().isEmpty() && menu.slots.get(4).getItem().isEmpty();
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		if (canTrade()) {
			graphics.blit(TEXTURE, leftPos + ICON_X, topPos + ICON_Y, 176, 0, ICON_SIZE, ICON_SIZE);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && canTrade()) {
			double dx = mouseX - (leftPos + ICON_X);
			double dy = mouseY - (topPos + ICON_Y);
			if (dx >= 0 && dy >= 0 && dx < ICON_SIZE && dy < ICON_SIZE) {
				if (minecraft != null && minecraft.gameMode != null) {
					minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ThaumcraftSounds.PECH_DICE.get(),
							0.95f + minecraft.level.random.nextFloat() * 0.1f, 0.5f));
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
}

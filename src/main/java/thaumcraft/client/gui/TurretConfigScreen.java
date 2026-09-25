package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.container.TurretConfigMenu;

/**
 * Replaces the old (1.12.2) {@code GuiTurretAdvanced} - reuses the original's own real {@code
 * gui_turret_advanced.png} background and its real {@code GuiToggleButton} icon regions (off at {@code
 * (192,16)}, an "on" overlay at {@code (192,24)}, both 8x8 - taken directly from that class's own real
 * {@code drawButton}) rather than inventing new art. Real health bar overlay at the original's own real
 * {@code (192,48)} UV, width scaled 0-39px by {@link TurretConfigMenu#getHealthFraction}, same real math
 * as the original's own {@code (int)(39f * health/maxHealth)}.
 *
 * <p>No player-inventory slots to lay out at all - see {@code TurretConfigMenu}'s own doc comment for why.
 */
public class TurretConfigScreen extends AbstractContainerScreen<TurretConfigMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_turret_advanced.png");

	public TurretConfigScreen(TurretConfigMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 175;
		imageHeight = 232;
		inventoryLabelY = -1000; // no player-inventory slots in this menu at all
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(new ToggleButton(leftPos + 90, topPos + 13, menu::isTargetAnimal,
				() -> click(TurretConfigMenu.BUTTON_TARGET_ANIMAL), Component.translatable("button.turretfocus.1")));
		addRenderableWidget(new ToggleButton(leftPos + 90, topPos + 27, menu::isTargetMob,
				() -> click(TurretConfigMenu.BUTTON_TARGET_MOB), Component.translatable("button.turretfocus.2")));
		addRenderableWidget(new ToggleButton(leftPos + 90, topPos + 41, menu::isTargetPlayer,
				() -> click(TurretConfigMenu.BUTTON_TARGET_PLAYER), Component.translatable("button.turretfocus.3")));
		addRenderableWidget(new ToggleButton(leftPos + 90, topPos + 55, menu::isTargetFriendly,
				() -> click(TurretConfigMenu.BUTTON_TARGET_FRIENDLY), Component.translatable("button.turretfocus.4")));
	}

	private void click(int buttonId) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
		}
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
		int barWidth = (int) (39.0f * menu.getHealthFraction());
		if (barWidth > 0) {
			graphics.blit(TEXTURE, leftPos + 30, topPos + 59, 192, 48, barWidth, 6);
		}
	}

	private static class ToggleButton extends AbstractWidget {

		private final java.util.function.BooleanSupplier active;
		private final Runnable onClick;

		ToggleButton(int x, int y, java.util.function.BooleanSupplier active, Runnable onClick, Component label) {
			super(x, y, 8, 8, label);
			this.active = active;
			this.onClick = onClick;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.blit(TEXTURE, getX(), getY(), 192, 16, 8, 8);
			if (active.getAsBoolean()) {
				graphics.blit(TEXTURE, getX(), getY(), 192, 24, 8, 8);
			}
			if (isHovered) {
				graphics.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, getMessage(), mouseX, mouseY);
			}
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			onClick.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}

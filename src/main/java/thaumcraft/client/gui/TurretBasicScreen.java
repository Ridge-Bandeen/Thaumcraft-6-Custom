package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.container.TurretBasicMenu;
import thaumcraft.common.entities.construct.EntityTurretCrossbow;

/** Replaces the old (1.12.2) {@code GuiTurretBasic}: the ammo slot over {@code gui_turret_basic.png} and the turret's health bar. */
public class TurretBasicScreen extends AbstractContainerScreen<TurretBasicMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_turret_basic.png");

	public TurretBasicScreen(TurretBasicMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 175;
		imageHeight = 232;
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
		EntityTurretCrossbow turret = menu.getTurret();
		if (turret != null) {
			int health = (int) (39.0f * (turret.getHealth() / turret.getMaxHealth()));
			graphics.blit(TEXTURE, leftPos + 68, topPos + 59, 192, 48, health, 6);
		}
	}
}

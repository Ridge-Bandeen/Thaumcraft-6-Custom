package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import thaumcraft.common.container.SpaMenu;

/**
 * Replaces the old (1.12.2) {@code GuiSpa}, as the original: the Bath Salts slot, the mix toggle (an 8x8 switch with
 * its "mix with ingredient"/"use just the fluid" tooltip, clicking sends button 1) and the tank gauge (the fluid's
 * texture filling it, its name and amount on hover).
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
		int mx = mouseX - (leftPos + 104);
		int my = mouseY - (topPos + 10);
		net.minecraft.world.level.material.Fluid fluid = menu.tankFluid();
		if (mx >= 0 && my >= 0 && mx < 10 && my < 55 && fluid != null && menu.tankAmount() > 0) {
			graphics.renderComponentTooltip(font, java.util.List.of(fluid.getFluidType().getDescription(),
					Component.literal(menu.tankAmount() + " mb")), mouseX, mouseY);
		}
		mx = mouseX - (leftPos + 88);
		my = mouseY - (topPos + 34);
		if (mx >= 0 && my >= 0 && mx < 10 && my < 10) {
			graphics.renderTooltip(font, Component.translatable(menu.isMix() ? "text.spa.mix.true" : "text.spa.mix.false"), mouseX, mouseY);
		}
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		graphics.blit(TEXTURE, leftPos + 89, topPos + 35, 208, menu.isMix() ? 16 : 32, 8, 8);
		net.minecraft.world.level.material.Fluid fluid = menu.tankFluid();
		if (fluid != null && menu.tankAmount() > 0) {
			var ext = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid);
			var sprite = net.minecraft.client.Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture());
			int tint = ext.getTintColor();
			float r = ((tint >> 16) & 255) / 255.0f, g = ((tint >> 8) & 255) / 255.0f, b = (tint & 255) / 255.0f;
			// as the original: six half-size tiles of the fluid, the empty part covered back up from the top
			for (int a = 0; a < 6; a++) {
				graphics.blit(leftPos + 107, topPos + 15 + a * 8, 0, 8, 8, sprite, r, g, b, 1.0f);
			}
			float bar = menu.tankAmount() / 5000.0f;
			graphics.blit(TEXTURE, leftPos + 107, topPos + 15, 107, 15, 10, (int) (48.0f - 48.0f * bar));
		}
		graphics.blit(TEXTURE, leftPos + 106, topPos + 11, 232, 0, 10, 55);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		double mx = mouseX - (leftPos + 89);
		double my = mouseY - (topPos + 35);
		if (mx >= 0 && my >= 0 && mx < 8 && my < 8 && minecraft != null && minecraft.gameMode != null && minecraft.player != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
			minecraft.player.playSound(thaumcraft.common.registry.ThaumcraftSounds.CLACK.get(), 0.4f, 1.0f);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
}

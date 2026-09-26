package thaumcraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import thaumcraft.common.container.ArcaneWorkbenchMenu;

/**
 * Replaces the old (1.12.2) {@code GuiArcaneWorkbench}. Reuses the original background texture and its
 * xSize/ySize (190/234), and (restored 2026-09-25) the original's vis readout above the result socket - "N available"
 * and "N vis (X% discount)" in half-size text - the greyed-out result preview while the vis is short, and the slowly
 * turning coloured glow behind each crystal-ring slot the current recipe needs.
 */
public class GuiArcaneWorkbench extends AbstractContainerScreen<ArcaneWorkbenchMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/arcaneworkbench.png");

	public GuiArcaneWorkbench(ArcaneWorkbenchMenu menu, Inventory playerInventory, net.minecraft.network.chat.Component title) {
		super(menu, playerInventory, title);
		imageWidth = 190;
		imageHeight = 234;
		inventoryLabelY = imageHeight - 94;
	}

	/** {@code AbstractContainerScreen#render} doesn't draw item tooltips by itself in 1.21 - vanilla's own container
	 * screens call {@code renderTooltip} at the end of their own {@code render}. Missing here until the 2026-09-25
	 * playtest found no tooltips on this screen. */
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	/** The original drew no title or "Inventory" label - the texture has no room for them. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		if (minecraft == null || minecraft.level == null) return;

		int cost = menu.getShownCost();
		int available = menu.getAvailableVis();

		AspectList crystals = menu.findCrystalNeeds(minecraft.level);
		if (crystals != null) {
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
			float spin = (minecraft.player != null ? minecraft.player.tickCount % 360 : 0) + partialTick;
			for (Aspect aspect : crystals.getAspects()) {
				int ring = java.util.Arrays.asList(ArcaneWorkbenchMenu.RING_ASPECTS).indexOf(aspect);
				if (ring < 0) continue;
				int rgb = aspect.getColor();
				RenderSystem.setShaderColor((rgb >> 16 & 255) / 255.0f, (rgb >> 8 & 255) / 255.0f, (rgb & 255) / 255.0f, 0.33f);
				graphics.pose().pushPose();
				graphics.pose().translate(leftPos + menu.slots.get(10 + ring).x + 7.5f, topPos + menu.slots.get(10 + ring).y + 8.0f, 0.0f);
				graphics.pose().mulPose(Axis.ZP.rotationDegrees(ring * 60 + spin));
				graphics.pose().scale(0.5f, 0.5f, 1.0f);
				graphics.blit(TEXTURE, -32, -32, 192, 0, 64, 64);
				graphics.pose().popPose();
			}
			RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableBlend();
		}

		graphics.pose().pushPose();
		graphics.pose().translate(leftPos + 168, topPos + 46, 0.0f);
		graphics.pose().scale(0.5f, 0.5f, 1.0f);
		String text = available + " " + Component.translatable("workbench.available").getString();
		graphics.drawString(font, text, -font.width(text) / 2, 0, available < cost ? 0xEE6E6E : 0x6E6E6E, false);
		graphics.pose().popPose();

		if (cost > 0) {
			if (available < cost) {
				ItemStack preview = menu.previewResult(minecraft.level);
				if (!preview.isEmpty()) {
					graphics.renderItem(preview, leftPos + 160, topPos + 64);
					// a translucent slot-grey wash over the item dims it, standing in for the original's grey tint
					graphics.fill(leftPos + 160, topPos + 64, leftPos + 176, topPos + 80, 0xAA555555);
				}
			}
			graphics.pose().pushPose();
			graphics.pose().translate(leftPos + 168, topPos + 38, 0.0f);
			graphics.pose().scale(0.5f, 0.5f, 1.0f);
			text = cost + " " + Component.translatable("workbench.cost").getString();
			int discount = menu.getShownDiscount();
			if (discount > 0) {
				text += " (" + discount + "% " + Component.translatable("workbench.discount").getString() + ")";
			} else if (discount < 0) {
				text += " (+" + -discount + "%)";
			}
			graphics.drawString(font, text, -font.width(text) / 2, 0, 0xC0C0FF, false);
			graphics.pose().popPose();
		}
	}
}

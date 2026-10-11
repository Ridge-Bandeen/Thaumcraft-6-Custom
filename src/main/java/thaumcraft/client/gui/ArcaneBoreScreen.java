package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.common.container.ArcaneBoreMenu;
import thaumcraft.common.entities.construct.EntityArcaneBore;

/**
 * Replaces the old (1.12.2) {@code GuiArcaneBore}, at the original's layout: the pickaxe slot with a warning icon over it
 * once the tool is about to break, the bore's health bar, and - in half-size text at the top right - its dig width, depth
 * and speed bonus plus any Refining, Fortune or Silk Touch the pickaxe grants.
 */
public class ArcaneBoreScreen extends AbstractContainerScreen<ArcaneBoreMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_arcanebore.png");

	private final EntityArcaneBore bore;

	public ArcaneBoreScreen(ArcaneBoreMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.bore = menu.getBore();
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
		if (bore == null) return;

		int health = (int) (39.0f * (bore.getHealth() / bore.getMaxHealth()));
		graphics.blit(TEXTURE, leftPos + 68, topPos + 59, 192, 48, health, 6);

		ItemStack tool = bore.getMainHandItem();
		if (!tool.isEmpty() && tool.isDamageableItem() && tool.getDamageValue() + 1 >= tool.getMaxDamage()) {
			graphics.blit(TEXTURE, leftPos + 80, topPos + 29, 240, 0, 16, 16);
		}

		graphics.pose().pushPose();
		graphics.pose().translate(leftPos + 124, topPos + 18, 505.0f);
		graphics.pose().scale(0.5f, 0.5f, 1.0f);
		graphics.drawString(font, "Width: " + (1 + bore.getDigRadius() * 2), 0, 0, 0xFFFFFF, true);
		graphics.drawString(font, "Depth: " + bore.getDigDepth(), 64, 0, 0xFFFFFF, true);
		graphics.drawString(font, "Speed: +" + bore.getDigSpeed(Blocks.STONE.defaultBlockState()), 0, 10, 0xFFFFFF, true);
		int base = 0;
		int refining = bore.getRefining();
		int fortune = bore.getFortune();
		if (bore.hasSilkTouch() || refining > 0 || fortune > 0) {
			graphics.drawString(font, "Other properties:", 0, 24, 0xFFFFFF, true);
		}
		if (refining > 0) {
			graphics.drawString(font, "Refining " + refining, 4, 34 + base, 0xC0C0C0, true);
			base += 9;
		}
		if (fortune > 0) {
			graphics.drawString(font, "Fortune " + fortune, 4, 34 + base, 0xEEC64A, true);
			base += 9;
		}
		if (bore.hasSilkTouch()) {
			graphics.drawString(font, "Silk Touch", 4, 34 + base, 0x8080FF, true);
		}
		graphics.pose().popPose();
	}
}

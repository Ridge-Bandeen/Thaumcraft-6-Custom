package thaumcraft.client.gui;

import java.awt.Color;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/**
 * Draws an {@link AspectsTooltip}: one 16x16 aspect icon per aspect, tinted with its colour, 18 pixels apart, largest amount first, each with its
 * amount in small type at the icon's lower right - the original's {@code HudHandler#renderAspectsInGui} / {@code UtilsFX#drawTag}, which drew
 * into blank lines the tooltip reserved for it. Here the tooltip simply gets an 18-pixel-high component.
 */
public class ClientAspectsTooltip implements ClientTooltipComponent {

	private static final int SPACING = 18;

	private final Aspect[] aspects;
	private final AspectList list;

	public ClientAspectsTooltip(AspectsTooltip tooltip) {
		this.list = tooltip.aspects();
		this.aspects = tooltip.aspects().getAspectsSortedByAmount();
	}

	@Override
	public int getHeight() {
		return 18;
	}

	@Override
	public int getWidth(Font font) {
		return aspects.length * SPACING;
	}

	@Override
	public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
		int index = 0;
		for (Aspect aspect : aspects) {
			if (aspect == null) continue;
			drawTag(graphics, font, x + index * SPACING, y + 1, aspect, list.getAmount(aspect));
			index++;
		}
	}

	/** An aspect icon with its amount; shared look with the sprayer's own tag drawing. */
	public static void drawTag(GuiGraphics graphics, Font font, int x, int y, Aspect aspect, int amount) {
		ResourceLocation image = aspect.getImage();
		if (image != null) {
			Color c = new Color(aspect.getColor());
			graphics.setColor(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1f);
			graphics.blit(image, x, y, 0, 0, 16, 16, 16, 16);
			graphics.setColor(1f, 1f, 1f, 1f);
		}
		if (amount > 0) {
			String s = String.valueOf(amount);
			graphics.pose().pushPose();
			graphics.pose().translate(x + 16 - font.width(s) * 0.6f, y + 10, 10);
			graphics.pose().scale(0.6f, 0.6f, 1f);
			graphics.drawString(font, s, 0, 0, 0xFFFFFFFF);
			graphics.pose().popPose();
		}
	}
}

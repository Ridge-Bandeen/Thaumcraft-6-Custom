package thaumcraft.client.gui;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The original's own {@code UtilsFX#drawCustomTooltip}, which the Thaumonomicon uses in place of vanilla's tooltip
 * everywhere (research hover, requirement popups, recipe tooltips): the same purple-edged box, but a line starting
 * with {@code "@@"} is drawn at half size (the stage / "missing research" sub-lines under a research name), and a
 * {@code subTipColor} other than -99 colours the first line with that {@code §} code and the rest grey. Placed at
 * (x + 12, y - 12) like the original, clamped to the bottom of the screen and flipped left of the cursor when it
 * wouldn't fit on the right.
 */
final class ResearchTooltip {

	private ResearchTooltip() {
	}

	static void draw(GuiGraphics graphics, Font font, List<?> lines, int x, int y, int subTipColor, int screenWidth, int screenHeight) {
		if (lines.isEmpty()) return;
		int max = 240;
		boolean flip = false;
		if (max + 24 + x > screenWidth) {
			max = screenWidth - x - 24;
			if (max < 120) {
				max = 240;
				flip = true;
			}
		}
		int widest = 0;
		int totalHeight = -2;
		for (Object o : lines) {
			if (o instanceof Component c) {
				widest = Math.max(widest, font.width(c));
				totalHeight += 10;
				continue;
			}
			String line = String.valueOf(o);
			boolean small = line.startsWith("@@");
			int w = font.width(small ? line.substring(2) : line);
			if (small) w /= 2;
			widest = Math.max(widest, w);
			totalHeight += small ? 7 : 10;
		}
		int sX = x + 12;
		int sY = y - 12;
		if (lines.size() > 1) totalHeight += 2;
		if (sY + totalHeight > screenHeight) sY = screenHeight - totalHeight - 5;
		if (flip) sX -= widest + 24;

		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 400);
		int bg = 0xF0100010;
		graphics.fill(sX - 3, sY - 4, sX + widest + 3, sY - 3, bg);
		graphics.fill(sX - 3, sY + totalHeight + 3, sX + widest + 3, sY + totalHeight + 4, bg);
		graphics.fill(sX - 3, sY - 3, sX + widest + 3, sY + totalHeight + 3, bg);
		graphics.fill(sX - 4, sY - 3, sX - 3, sY + totalHeight + 3, bg);
		graphics.fill(sX + widest + 3, sY - 3, sX + widest + 4, sY + totalHeight + 3, bg);
		int border = 0x505000FF;
		int borderDark = (border & 0xFEFEFE) >> 1 | (border & 0xFF000000);
		graphics.fillGradient(sX - 3, sY - 3 + 1, sX - 3 + 1, sY + totalHeight + 3 - 1, border, borderDark);
		graphics.fillGradient(sX + widest + 2, sY - 3 + 1, sX + widest + 3, sY + totalHeight + 3 - 1, border, borderDark);
		graphics.fill(sX - 3, sY - 3, sX + widest + 3, sY - 3 + 1, border);
		graphics.fill(sX - 3, sY + totalHeight + 2, sX + widest + 3, sY + totalHeight + 3, borderDark);

		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i) instanceof Component c) {
				// an item tooltip line keeps its own colours; unstyled text takes the sub-tip colour like the original's prefix
				Component shown = subTipColor == -99 ? c : Component.empty()
						.withStyle(i == 0 ? ChatFormatting.getById(subTipColor) : ChatFormatting.GRAY).append(c);
				graphics.pose().pushPose();
				graphics.pose().translate(sX, sY, 1);
				graphics.drawString(font, shown, 0, 0, 0xFFFFFFFF, true);
				graphics.pose().popPose();
				sY += 10;
				if (i == 0) sY += 2;
				continue;
			}
			String line = String.valueOf(lines.get(i));
			boolean small = line.startsWith("@@");
			line = line.replace("@@", "");
			if (subTipColor != -99) line = (i == 0 ? "§" + Integer.toHexString(subTipColor) : "§7") + line;
			graphics.pose().pushPose();
			graphics.pose().translate(sX, sY, 1);
			if (small) {
				graphics.pose().scale(0.5f, 0.5f, 1f);
				graphics.drawString(font, line, 0, 3, 0xFFFFFFFF, true);
				sY += 7;
			} else {
				graphics.drawString(font, line, 0, 0, 0xFFFFFFFF, true);
				sY += 10;
			}
			graphics.pose().popPose();
			if (i == 0) sY += 2;
		}
		graphics.pose().popPose();
	}
}

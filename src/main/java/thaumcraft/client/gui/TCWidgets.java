package thaumcraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The original's small screen widgets from {@code client/gui/plugins}, drawn from {@code gui_base.png} as they were:
 * {@code GuiScrollButton}, {@code GuiPlusMinusButton}, {@code GuiSliderTC} and {@code GuiImageButton}.
 */
public final class TCWidgets {

	public static final ResourceLocation BASE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_base.png");

	private TCWidgets() {
	}

	/** A 10x10 button drawn at 0.9 brightness, full when hovered. */
	private abstract static class TintedButton extends AbstractWidget {
		private final Runnable onPress;

		TintedButton(int x, int y, int w, int h, Runnable onPress) {
			super(x, y, w, h, Component.empty());
			this.onPress = onPress;
		}

		abstract int u();

		abstract int v();

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			float c = isHovered() ? 1.0f : 0.9f;
			RenderSystem.enableBlend();
			graphics.setColor(c, c, c, c);
			graphics.blit(BASE, getX(), getY(), u(), v(), 10, 10);
			graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
			RenderSystem.disableBlend();
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			onPress.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	/** {@code GuiScrollButton}: an arrow; vertical ones point up ({@code minus}) or down. */
	public static final class ScrollButton extends TintedButton {
		private final boolean minus;
		private final boolean vertical;

		public ScrollButton(int x, int y, int w, int h, boolean minus, boolean vertical, Runnable onPress) {
			super(x, y, w, h, onPress);
			this.minus = minus;
			this.vertical = vertical;
		}

		@Override
		int u() {
			return vertical ? 67 : (minus ? 20 : 30);
		}

		@Override
		int v() {
			return vertical ? (minus ? 0 : 10) : 0;
		}
	}

	/** {@code GuiPlusMinusButton}: a minus or plus sign. */
	public static final class PlusMinusButton extends TintedButton {
		private final boolean minus;

		public PlusMinusButton(int x, int y, int w, int h, boolean minus, Runnable onPress) {
			super(x, y, w, h, onPress);
			this.minus = minus;
		}

		@Override
		int u() {
			return minus ? 0 : 10;
		}

		@Override
		int v() {
			return 0;
		}
	}

	/**
	 * {@code GuiSliderTC}: a thin track with an 8x8 thumb. Pressing it sets the position from the mouse, and the thumb
	 * follows the mouse until the button is released ({@link #release}). As the original, {@link #setMax} puts the thumb
	 * back at the start.
	 */
	public static final class Slider extends AbstractWidget {
		private final boolean vertical;
		private float min;
		private float max;
		private float position;
		private boolean mouseDown;

		public Slider(int x, int y, int w, int h, float min, float max, float value, boolean vertical) {
			super(x, y, w, h, Component.empty());
			this.min = min;
			this.max = max;
			this.vertical = vertical;
			this.position = max == min ? 0.0f : (value - min) / (max - min);
		}

		public float getMax() {
			return max;
		}

		public void setMax(float max) {
			this.max = max;
			position = 0.0f;
		}

		public float getValue() {
			return min + (max - min) * position;
		}

		public void setValue(float value) {
			position = max == min ? 0.0f : (value - min) / (max - min);
		}

		public void release() {
			mouseDown = false;
		}

		private void follow(double mouseX, double mouseY) {
			position = vertical ? (float) (mouseY - (getY() + 4)) / (height - 8) : (float) (mouseX - (getX() + 4)) / (width - 8);
			position = Math.max(0.0f, Math.min(1.0f, position));
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			follow(mouseX, mouseY);
			mouseDown = true;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			RenderSystem.enableBlend();
			graphics.pose().pushPose();
			if (vertical) {
				graphics.pose().translate(getX() + 2, getY(), 0);
				graphics.pose().scale(1.0f, height / 32.0f, 1.0f);
				graphics.blit(BASE, 0, 0, 240, 176, 4, 32);
			} else {
				graphics.pose().translate(getX(), getY() + 2, 0);
				graphics.pose().scale(width / 32.0f, 1.0f, 1.0f);
				graphics.blit(BASE, 0, 0, 208, 176, 32, 4);
			}
			graphics.pose().popPose();
			if (mouseDown) follow(mouseX, mouseY);
			if (vertical) {
				graphics.blit(BASE, getX(), getY() + (int) (position * (height - 8)), 20, 20, 8, 8);
			} else {
				graphics.blit(BASE, getX() + (int) (position * (width - 8)), getY(), 20, 20, 8, 8);
			}
			RenderSystem.disableBlend();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}

	/**
	 * {@code GuiImageButton}: a plate from a texture, tinted (0.9, full when hovered, 0.5 when inactive) and placed by its
	 * centre, with its label in half-size text across the middle.
	 */
	public static final class ImageButton extends AbstractWidget {
		private final ResourceLocation texture;
		private final int u, v, texWidth, texHeight;
		private final int centreX, centreY;
		private final Runnable onPress;
		public int color = 0xFFFFFF;

		public ImageButton(int centreX, int centreY, int w, int h, Component label, ResourceLocation texture, int u, int v, int texWidth, int texHeight,
				Runnable onPress) {
			super(centreX - w / 2, centreY - h / 2, w, h, label);
			this.centreX = centreX;
			this.centreY = centreY;
			this.texture = texture;
			this.u = u;
			this.v = v;
			this.texWidth = texWidth;
			this.texHeight = texHeight;
			this.onPress = onPress;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			float cc = isHovered() ? 1.0f : 0.9f;
			float ac = 1.0f;
			if (!active) {
				cc = 0.5f;
				ac = 0.9f;
			}
			RenderSystem.enableBlend();
			graphics.setColor(cc * ((color >> 16) & 255) / 255.0f, cc * ((color >> 8) & 255) / 255.0f, cc * (color & 255) / 255.0f, ac);
			graphics.blit(texture, centreX - texWidth / 2, centreY - texHeight / 2, u, v, texWidth, texHeight);
			graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
			RenderSystem.disableBlend();
			int textColour = !active ? 0xA0A0A0 : isHovered() ? 0xFFFFA0 : 0xFFFFFF;
			graphics.pose().pushPose();
			graphics.pose().translate(centreX, centreY, 0);
			graphics.pose().scale(0.5f, 0.5f, 1.0f);
			graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), 0, -4, textColour);
			graphics.pose().popPose();
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			onPress.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}

package thaumcraft.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.seals.ISealGui;
import thaumcraft.common.container.SealConfigMenu;

/**
 * Replaces the old (1.12.2) {@code SealBaseGUI} - a faithful recreation (the user explicitly asked for this,
 * overriding this session's own usual "simplify client screens, GameTest can't verify them anyway" default -
 * see {@code SealConfigMenu}'s own doc comment for that server-side half's real precedent) of the original's
 * radial category wheel, all against real synced {@link SealConfigMenu} state - no client-only cache class
 * needed, same "the menu already IS the synced copy" shape {@link GuiGolemBuilder} already established.
 *
 * <p>{@code category} is real but purely client-local (no server round trip - see {@link SealConfigMenu}'s
 * own doc comment for why): switching tabs just clears and rebuilds this screen's own widgets against
 * whichever section's data the menu already has synced. The original's half-dozen single-purpose button
 * classes ({@code GuiGolemCategoryButton}/{@code GuiGolemRedstoneButton}/{@code GuiGolemLockButton}/
 * {@code GuiGolemBWListButton}/{@code GuiGolemPropButton}) are consolidated into one real, parameterized
 * {@link IconButton} (same {@code gui_base.png} icon regions, same on/off/hover behavior) plus one
 * {@link PlusMinusButton} and one hover-only {@link TraitIcon} - a real simplification of class *count*, not
 * of any visible behavior.
 */
public class SealConfigScreen extends AbstractContainerScreen<SealConfigMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_base.png");

	private int category = -1;
	private int middleX;
	private int middleY;

	public SealConfigScreen(SealConfigMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 176;
		imageHeight = 232;
		inventoryLabelY = -1000; // the wheel background already covers this area - no separate "Inventory" label
		middleX = imageWidth / 2;
		middleY = (imageHeight - 72) / 2 - 8;
	}

	@Override
	protected void init() {
		super.init();
		if (category < 0 || !isRealCategory(category)) {
			category = menu.getCategories()[0];
		}
		rebuild();
	}

	private boolean isRealCategory(int cat) {
		for (int c : menu.getCategories()) if (c == cat) return true;
		return false;
	}

	private void switchTo(int cat) {
		category = cat;
		rebuild();
	}

	/** Clears and re-adds every non-slot widget - the ghost/player-inventory {@link net.minecraft.world.inventory.Slot}s
	 * themselves are real menu slots, untouched by this. */
	private void rebuild() {
		clearWidgets();

		int[] categories = menu.getCategories();
		float slice = Math.max(12.0f, Math.min(24.0f, 60.0f / categories.length));
		float start = -180.0f + (categories.length - 1) * slice / 2.0f;
		int c = 0;
		if (categories.length > 1) {
			for (int cat : categories) {
				int xx = wheelX(start, slice, c);
				int yy = wheelY(start, slice, c);
				boolean active = cat == category;
				addRenderableWidget(new IconButton(leftPos + middleX + xx - 8, topPos + middleY + yy - 8, 16, cat * 16, 120, -1, -1,
						() -> active, () -> switchTo(cat), () -> Component.translatable("button.category." + cat)));
				c++;
			}
		}
		int rxx = wheelX(start, slice, c);
		int ryy = wheelY(start, slice, c);
		addRenderableWidget(new IconButton(leftPos + middleX + rxx - 8, topPos + middleY + ryy - 8, 16, 80, 136, 64, 136,
				() -> menu.isRedstoneSensitive(), this::toggleRedstone,
				() -> Component.translatable(menu.isRedstoneSensitive() ? "golem.prop.redon" : "golem.prop.redoff")));

		switch (category) {
			case ISealGui.CAT_PRIORITY -> addPriorityWidgets();
			case ISealGui.CAT_FILTER -> addFilterWidgets();
			case ISealGui.CAT_AREA -> addAreaWidgets();
			case ISealGui.CAT_TOGGLES -> addToggleWidgets();
			case ISealGui.CAT_TAGS -> addTagWidgets();
			default -> {
			}
		}
	}

	private int wheelX(float start, float slice, int index) {
		return (int) (Math.cos(Math.toRadians(start - index * slice)) * 86.0);
	}

	private int wheelY(float start, float slice, int index) {
		return (int) (Math.sin(Math.toRadians(start - index * slice)) * 86.0);
	}

	private void toggleRedstone() {
		click(SealConfigMenu.BUTTON_REDSTONE);
	}

	private void addPriorityWidgets() {
		addRenderableWidget(new PlusMinusButton(leftPos + middleX - 5 - 14, topPos + middleY - 17, true, () -> click(SealConfigMenu.BUTTON_PRIORITY_DOWN)));
		addRenderableWidget(new PlusMinusButton(leftPos + middleX - 5 + 14, topPos + middleY - 17, false, () -> click(SealConfigMenu.BUTTON_PRIORITY_UP)));
		addRenderableWidget(new PlusMinusButton(leftPos + middleX + 18 - 12, topPos + middleY + 4, true, () -> click(SealConfigMenu.BUTTON_COLOR_DOWN)));
		addRenderableWidget(new PlusMinusButton(leftPos + middleX + 18 + 11, topPos + middleY + 4, false, () -> click(SealConfigMenu.BUTTON_COLOR_UP)));
		addRenderableWidget(new IconButton(leftPos + middleX - 32, topPos + middleY, 16, 48, 136, 32, 136,
				() -> menu.isLocked(), () -> click(SealConfigMenu.BUTTON_LOCK),
				() -> Component.translatable(menu.isLocked() ? "golem.prop.lock" : "golem.prop.unlock")));
	}

	private void addFilterWidgets() {
		if (!menu.hasFilter()) return;
		int s = Math.max(menu.getFilterSize(), 1);
		int sy = 16 + (s - 1) / 3 * 12;
		addRenderableWidget(new IconButton(leftPos + middleX - 8, topPos + middleY + (s - 1) / 3 * 24 - sy + 27, 16, 16, 136, 0, 136,
				() -> menu.isBlacklist(), () -> click(SealConfigMenu.BUTTON_BLACKLIST),
				() -> Component.translatable(menu.isBlacklist() ? "button.bl" : "button.wl")));
	}

	private void addAreaWidgets() {
		if (!menu.hasArea()) return;
		addRenderableWidget(new PlusMinusButton(leftPos + middleX - 5 - 14, topPos + middleY - 25, true, () -> click(SealConfigMenu.BUTTON_AREA_Y_DOWN)));
		addRenderableWidget(new PlusMinusButton(leftPos + middleX - 5 + 14, topPos + middleY - 25, false, () -> click(SealConfigMenu.BUTTON_AREA_Y_UP)));
		addRenderableWidget(new PlusMinusButton(leftPos + middleX - 5 - 14, topPos + middleY, true, () -> click(SealConfigMenu.BUTTON_AREA_X_DOWN)));
		addRenderableWidget(new PlusMinusButton(leftPos + middleX - 5 + 14, topPos + middleY, false, () -> click(SealConfigMenu.BUTTON_AREA_X_UP)));
		addRenderableWidget(new PlusMinusButton(leftPos + middleX - 5 - 14, topPos + middleY + 25, true, () -> click(SealConfigMenu.BUTTON_AREA_Z_DOWN)));
		addRenderableWidget(new PlusMinusButton(leftPos + middleX - 5 + 14, topPos + middleY + 25, false, () -> click(SealConfigMenu.BUTTON_AREA_Z_UP)));
	}

	private void addToggleWidgets() {
		String[] keys = menu.getToggleKeys();
		String[] names = menu.getToggleNames();
		if (keys.length == 0) return;
		int s2 = keys.length < 4 ? 8 : keys.length < 6 ? 7 : keys.length < 9 ? 6 : 5;
		int h = (keys.length - 1) * s2;
		int w = 12;
		for (String name : names) {
			int ww = 12 + Math.min(100, font.width(Component.translatable(name)));
			ww /= 2;
			if (ww > w) w = ww;
		}
		for (int p = 0; p < keys.length; p++) {
			int index = p;
			int y = topPos + middleY - 5 - h + p * (s2 * 2);
			addRenderableWidget(new IconButton(leftPos + middleX - w - 2, y - 2, 12, 2, 18, 18, 18,
					() -> menu.isToggleOn(index), () -> click(SealConfigMenu.BUTTON_TOGGLE_BASE + index), () -> Component.translatable(names[index])));
			int labelX = leftPos + middleX - w + 12;
			int labelY = y;
			addRenderableWidget(new StaticLabel(labelX, labelY, Component.translatable(names[p])));
		}
	}

	private void addTagWidgets() {
		int[] required = menu.getRequiredTagOrdinals();
		for (int i = 0; i < required.length; i++) {
			EnumGolemTrait trait = EnumGolemTrait.values()[required[i]];
			int x = leftPos + middleX + i * 18 - (required.length - 1) * 9 - 8;
			addRenderableWidget(new TraitIcon(x, topPos + middleY - 16, trait));
		}
		int[] forbidden = menu.getForbiddenTagOrdinals();
		for (int i = 0; i < forbidden.length; i++) {
			EnumGolemTrait trait = EnumGolemTrait.values()[forbidden[i]];
			int x = leftPos + middleX + i * 18 - (forbidden.length - 1) * 9 - 8;
			addRenderableWidget(new TraitIcon(x, topPos + middleY + 16, trait));
		}
	}

	private void click(int buttonId) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
		}
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos + middleX - 80, topPos + middleY - 80, 96, 0, 160, 160);
		graphics.blit(TEXTURE, leftPos, topPos + 143, 0, 167, 176, 89);

		if (category == ISealGui.CAT_PRIORITY) {
			graphics.blit(TEXTURE, leftPos + middleX + 17, topPos + middleY + 3, 2, 18, 12, 12);
			int color = menu.getColor();
			if (color >= 1 && color <= 16) {
				net.minecraft.world.item.DyeColor dye = net.minecraft.world.item.DyeColor.byId(16 - color);
				int rgb = dye.getTextureDiffuseColor();
				float r = ((rgb >> 16) & 0xFF) / 255.0f;
				float g = ((rgb >> 8) & 0xFF) / 255.0f;
				float b = (rgb & 0xFF) / 255.0f;
				graphics.setColor(r, g, b, 1.0f);
				graphics.blit(TEXTURE, leftPos + middleX + 20, topPos + middleY + 6, 74, 31, 6, 6);
				graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
			}
		} else if (category == ISealGui.CAT_FILTER && menu.hasFilter()) {
			int s = Math.max(menu.getFilterSize(), 1);
			int sx = 16 + (s - 1) % 3 * 12;
			int sy = 16 + (s - 1) / 3 * 12;
			for (int a = 0; a < menu.getFilterSize(); a++) {
				int x = a % 3;
				int y = a / 3;
				graphics.blit(TEXTURE, leftPos + middleX + x * 24 - sx, topPos + middleY + y * 24 - sy, 0, 56, 32, 32);
			}
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);

		graphics.drawCenteredString(font, Component.translatable("button.category." + category), leftPos + middleX, topPos + middleY - 64, 0xFFFFFF);

		switch (category) {
			case ISealGui.CAT_PRIORITY -> {
				graphics.drawCenteredString(font, Component.translatable("golem.prop.priority"), leftPos + middleX, topPos + middleY - 28, 0xBBBBFF);
				graphics.drawCenteredString(font, String.valueOf(menu.getPriority()), leftPos + middleX, topPos + middleY - 16, 0xFFFFFF);
			}
			case ISealGui.CAT_AREA -> {
				graphics.drawCenteredString(font, Component.translatable("button.caption.y"), leftPos + middleX, topPos + middleY - 33, 0xDDDDDD);
				graphics.drawCenteredString(font, Component.translatable("button.caption.x"), leftPos + middleX, topPos + middleY - 9, 0xDDDDDD);
				graphics.drawCenteredString(font, Component.translatable("button.caption.z"), leftPos + middleX, topPos + middleY + 15, 0xDDDDDD);
				graphics.drawCenteredString(font, String.valueOf(menu.getAreaY()), leftPos + middleX, topPos + middleY - 24, 0xFFFFFF);
				graphics.drawCenteredString(font, String.valueOf(menu.getAreaX()), leftPos + middleX, topPos + middleY, 0xFFFFFF);
				graphics.drawCenteredString(font, String.valueOf(menu.getAreaZ()), leftPos + middleX, topPos + middleY + 24, 0xFFFFFF);
			}
			case ISealGui.CAT_TAGS -> {
				graphics.drawCenteredString(font, Component.translatable("button.caption.required"), leftPos + middleX, topPos + middleY - 26, 0xDDDDDD);
				graphics.drawCenteredString(font, Component.translatable("button.caption.forbidden"), leftPos + middleX, topPos + middleY + 6, 0xDDDDDD);
			}
			default -> {
			}
		}

		if (category == ISealGui.CAT_FILTER && menu.hasFilter() && !menu.isBlacklist()) {
			int s = Math.max(menu.getFilterSize(), 1);
			int sx = 16 + (s - 1) % 3 * 12;
			int sy = 16 + (s - 1) / 3 * 12;
			for (int a = 0; a < menu.getFilterSize(); a++) {
				if (menu.getGhostItem(a).isEmpty()) continue;
				int x = a % 3;
				int y = a / 3;
				int size = menu.hasStacksizeLimiters() ? menu.getFilterSlotSize(a) : 0;
				Component text = size == 0 ? Component.literal("*").withStyle(net.minecraft.ChatFormatting.GOLD) : Component.literal(String.valueOf(size));
				int px = leftPos + middleX + x * 24 - sx + 19 - font.width(text) - 2;
				int py = topPos + middleY + y * 24 - sy + 6 + 3;
				graphics.drawString(font, text, px, py, 0xFFFFFF);
			}
		}
	}


	private static class PlusMinusButton extends AbstractWidget {
		private final boolean minus;
		private final Runnable onClick;

		PlusMinusButton(int x, int y, boolean minus, Runnable onClick) {
			super(x, y, 10, 10, Component.empty());
			this.minus = minus;
			this.onClick = onClick;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.blit(TEXTURE, getX(), getY(), minus ? 0 : 10, 0, 10, 10);
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

	/** One real, parameterized replacement for the original's own half-dozen single-purpose icon-button
	 * classes - see this class's own doc comment. {@code onU/onV} of {@code -1} means "no distinct on-state
	 * art, just brighten when active" (the category wheel's own behavior). */
	private static class IconButton extends AbstractWidget {
		private final int offU;
		private final int offV;
		private final int onU;
		private final int onV;
		private final BooleanSupplier active;
		private final Runnable onClick;
		private final Supplier<Component> tooltip;

		IconButton(int x, int y, int size, int offU, int offV, int onU, int onV, BooleanSupplier active, Runnable onClick, Supplier<Component> tooltip) {
			super(x, y, size, size, Component.empty());
			this.offU = offU;
			this.offV = offV;
			this.onU = onU;
			this.onV = onV;
			this.active = active;
			this.onClick = onClick;
			this.tooltip = tooltip;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			boolean on = active.getAsBoolean();
			if (onU >= 0) {
				graphics.blit(TEXTURE, getX(), getY(), on ? onU : offU, on ? onV : offV, width, height);
			} else {
				if (!on && !isHovered) graphics.setColor(0.7f, 0.7f, 0.7f, 0.7f);
				graphics.blit(TEXTURE, getX(), getY(), offU, offV, width, height);
				graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
			}
			if (isHovered) {
				graphics.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, tooltip.get(), mouseX, mouseY);
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

	/** Read-only hover icon for a required/forbidden trait tag - real translation of the original's own
	 * {@code GuiHoverButton} usage for this one case (item/aspect icon hovering is real elsewhere in this
	 * port too, but tags only ever show a plain {@link EnumGolemTrait#icon} texture here). */
	private static class TraitIcon extends AbstractWidget {
		private final EnumGolemTrait trait;

		TraitIcon(int x, int y, EnumGolemTrait trait) {
			super(x, y, 16, 16, trait.getLocalizedName());
			this.trait = trait;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.blit(trait.icon, getX(), getY(), 0, 0, 16, 16, 16, 16);
			if (isHovered) {
				graphics.renderTooltip(net.minecraft.client.Minecraft.getInstance().font,
						java.util.List.of(trait.getLocalizedName(), trait.getLocalizedDescription()), java.util.Optional.empty(), mouseX, mouseY);
			}
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	/** Plain text label - used for toggle names next to their checkbox. */
	private static class StaticLabel extends AbstractWidget {
		private final Component text;

		StaticLabel(int x, int y, Component text) {
			super(x, y, Math.max(1, net.minecraft.client.Minecraft.getInstance().font.width(text)), 8, text);
			this.text = text;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, text, getX(), getY(), 0xFFFFFF, false);
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}

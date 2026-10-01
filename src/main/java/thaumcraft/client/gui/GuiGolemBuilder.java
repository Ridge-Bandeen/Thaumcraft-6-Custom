package thaumcraft.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemProperties;
import thaumcraft.api.golems.parts.GolemAddon;
import thaumcraft.api.golems.parts.GolemArm;
import thaumcraft.api.golems.parts.GolemHead;
import thaumcraft.api.golems.parts.GolemLeg;
import thaumcraft.api.golems.parts.GolemMaterial;
import thaumcraft.common.container.GolemBuilderMenu;
import thaumcraft.common.golems.GolemProperties;
import thaumcraft.common.lib.network.ServerboundGolemBuilderPacket;

/**
 * The Golem Press screen, a port of the original's {@code GuiGolemBuilder}: material, head, arms, legs and addon each
 * picked from the parts the player has researched with the little scroll arrows (the choice is remembered between
 * openings, as the original's statics were); the golem's traits as icons; its health/armour/damage; the Machina
 * cost and the components, those neither beside the press nor carried dimmed; a progress bar while it builds; and the
 * craft button, enabled once everything is to hand. Every icon has the original's hover tooltip (name, then the
 * description in blue italics - the Machina icon shows the aspect's name and description).
 *
 * (A stand-in of cycle buttons and a "cost / total" line until 2026-10-01.)
 */
public class GuiGolemBuilder extends AbstractContainerScreen<GolemBuilderMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_golembuilder.png");
	private static final ResourceLocation GUI_BASE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_base.png");
	private static final ResourceLocation MAT_ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/items/golem.png");

	private static int headIndex;
	private static int matIndex;
	private static int armIndex;
	private static int legIndex;
	private static int addonIndex;

	private final List<GolemHead> valHeads = new ArrayList<>();
	private final List<GolemMaterial> valMats = new ArrayList<>();
	private final List<GolemArm> valArms = new ArrayList<>();
	private final List<GolemLeg> valLegs = new ArrayList<>();
	private final List<GolemAddon> valAddons = new ArrayList<>();

	private IGolemProperties props = GolemProperties.fromLong(0L);
	private float hearts;
	private float armor;
	private float damage;
	private CraftButton craftButton;
	private int cost;
	private boolean allfound;
	private ItemStack[] components;
	private boolean[] owns;
	private boolean disableAll;
	private int lastSerial = -1;
	private final List<AbstractWidget> compWidgets = new ArrayList<>();

	public GuiGolemBuilder(GolemBuilderMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 208;
		imageHeight = 224;
	}

	private boolean knows(String[] research) {
		return research == null || research.length == 0 || ThaumcraftCapabilities.knowsResearchStrict(minecraft.player, research);
	}

	@Override
	protected void init() {
		super.init();
		valHeads.clear();
		for (GolemHead head : GolemHead.getHeads()) if (head != null && knows(head.research)) valHeads.add(head);
		valMats.clear();
		for (GolemMaterial mat : GolemMaterial.getMaterials()) if (mat != null && knows(mat.research)) valMats.add(mat);
		valArms.clear();
		for (GolemArm arm : GolemArm.getArms()) if (arm != null && knows(arm.research)) valArms.add(arm);
		valLegs.clear();
		for (GolemLeg leg : GolemLeg.getLegs()) if (leg != null && knows(leg.research)) valLegs.add(leg);
		valAddons.clear();
		for (GolemAddon addon : GolemAddon.getAddons()) if (addon != null && knows(addon.research)) valAddons.add(addon);
		if (headIndex >= valHeads.size()) headIndex = 0;
		if (matIndex >= valMats.size()) matIndex = 0;
		if (armIndex >= valArms.size()) armIndex = 0;
		if (legIndex >= valLegs.size()) legIndex = 0;
		if (addonIndex >= valAddons.size()) addonIndex = 0;
		gatherInfo();
	}

	private static int step(int index, int size, int by) {
		if (size <= 0) return 0;
		return ((index + by) % size + size) % size;
	}

	private void gatherInfo() {
		clearWidgets();
		compWidgets.clear();
		if (valHeads.isEmpty() || valMats.isEmpty() || valArms.isEmpty() || valLegs.isEmpty() || valAddons.isEmpty()) return;
		int gl = leftPos;
		int gt = topPos;
		craftButton = addRenderableWidget(new CraftButton(gl + 120, gt + 104));
		if (valHeads.size() > 1) scrollPair(gl + 112, gt + 16, by -> headIndex = step(headIndex, valHeads.size(), by));
		if (valMats.size() > 1) scrollPair(gl + 16, gt + 16, by -> matIndex = step(matIndex, valMats.size(), by));
		if (valArms.size() > 1) scrollPair(gl + 112, gt + 40, by -> armIndex = step(armIndex, valArms.size(), by));
		if (valLegs.size() > 1) scrollPair(gl + 112, gt + 64, by -> legIndex = step(legIndex, valLegs.size(), by));
		if (valAddons.size() > 1) scrollPair(gl + 16, gt + 64, by -> addonIndex = step(addonIndex, valAddons.size(), by));

		GolemHead head = valHeads.get(headIndex);
		GolemMaterial mat = valMats.get(matIndex);
		GolemArm arm = valArms.get(armIndex);
		GolemLeg leg = valLegs.get(legIndex);
		GolemAddon addon = valAddons.get(addonIndex);
		addRenderableWidget(new HoverButton(gl + 120, gt + 24, head.getLocalizedName(), head.getLocalizedDescription(), head.icon, 0xFFFFFF));
		addRenderableWidget(new HoverButton(gl + 24, gt + 24, mat.getLocalizedName(), mat.getLocalizedDescription(), MAT_ICON, mat.itemColor));
		addRenderableWidget(new HoverButton(gl + 120, gt + 48, arm.getLocalizedName(), arm.getLocalizedDescription(), arm.icon, 0xFFFFFF));
		addRenderableWidget(new HoverButton(gl + 120, gt + 72, leg.getLocalizedName(), leg.getLocalizedDescription(), leg.icon, 0xFFFFFF));
		if (!addon.key.equalsIgnoreCase("none")) {
			addRenderableWidget(new HoverButton(gl + 24, gt + 72, addon.getLocalizedName(), addon.getLocalizedDescription(), addon.icon, 0xFFFFFF));
		}

		props = GolemProperties.fromLong(0L);
		props.setHead(head);
		props.setMaterial(mat);
		props.setArms(arm);
		props.setLegs(leg);
		props.setAddon(addon);
		PacketDistributor.sendToServer(new ServerboundGolemBuilderPacket(props.toLong(), false));
		redoComps();

		EnumGolemTrait[] tags = props.getTraits().toArray(new EnumGolemTrait[0]);
		if (tags.length > 0) {
			int yy = tags.length <= 4 ? (tags.length - 1) % 4 * 8 : 24;
			int xx = (tags.length - 1) / 4 % 4 * 8;
			int i = 0;
			int q = 0;
			for (EnumGolemTrait tag : tags) {
				addRenderableWidget(new HoverButton(gl + 72 + q * 16 - xx, gt + 48 + 16 * i - yy, tag.getLocalizedName(), tag.getLocalizedDescription(), tag.icon, 0xFFFFFF));
				if (++i > 3) {
					i = 0;
					++q;
				}
			}
		}
		// the original's figures, its quirk kept: "x *= (int) 0.75" multiplies by 0, so a fragile golem shows 0 health/armour
		int hh = 10 + props.getMaterial().healthMod;
		if (props.hasTrait(EnumGolemTrait.FRAGILE)) hh *= (int) 0.75;
		hearts = hh / 2.0f;
		int aa = props.getMaterial().armor;
		if (props.hasTrait(EnumGolemTrait.ARMORED)) aa = (int) Math.max(aa * 1.5, aa + 1);
		if (props.hasTrait(EnumGolemTrait.FRAGILE)) aa *= (int) 0.75;
		armor = aa / 2.0f;
		double dd = props.hasTrait(EnumGolemTrait.FIGHTER) ? props.getMaterial().damage : 0.0;
		if (props.hasTrait(EnumGolemTrait.BRUTAL)) dd = Math.max(dd * 1.5, dd + 1.0);
		damage = (float) (dd / 2.0);
	}

	private void scrollPair(int x, int y, java.util.function.IntConsumer move) {
		addRenderableWidget(new ScrollButton(x - 5 - 6, y - 5 + 8, true, () -> { move.accept(-1); gatherInfo(); }));
		addRenderableWidget(new ScrollButton(x - 5 + 22, y - 5 + 8, false, () -> { move.accept(1); gatherInfo(); }));
	}

	/** The original's redoComps: the cost, which components are to hand (beside the press, per the server's last answer,
	 * or carried), the Machina icon and component icons, and which buttons work. */
	private void redoComps() {
		for (AbstractWidget w : compWidgets) removeWidget(w);
		compWidgets.clear();
		allfound = true;
		cost = props.getTraits().size() * 2;
		components = props.generateComponents();
		owns = new boolean[components.length];
		for (int a = 0; a < components.length; a++) {
			cost += components[a].getCount();
			owns[a] = menu.pressHas(a) || carrying(components[a]);
			if (!owns[a]) allfound = false;
		}
		if (components.length > 0) {
			compWidgets.add(addRenderableWidget(new HoverButton(leftPos + 152, topPos + 24, Component.literal(Aspect.MECHANISM.getName()),
					Aspect.MECHANISM.getLocalizedDescription(), Aspect.MECHANISM, 0xFFFFFF)));
			int i = 1;
			int q = 0;
			for (ItemStack stack : components) {
				compWidgets.add(addRenderableWidget(new HoverButton(leftPos + 152 + q * 16, topPos + 24 + 16 * i, stack.getHoverName(), null, stack, 0xFFFFFF)));
				if (++i > 3) {
					i = 0;
					++q;
				}
			}
		}
		for (var child : children()) {
			if (child instanceof AbstractWidget w) w.active = !disableAll;
		}
		if (!disableAll && craftButton != null) craftButton.active = allfound;
	}

	private boolean carrying(ItemStack wanted) {
		int count = 0;
		for (ItemStack s : minecraft.player.getInventory().items) {
			if (!s.isEmpty() && ItemStack.isSameItem(wanted, s)) count += s.getCount();
		}
		return count >= wanted.getCount();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		// the original redid its list after the server's "stuff" answer and after any slot change
		if (menu.stuffSerial() != lastSerial) {
			lastSerial = menu.stuffSerial();
			if (components != null) redoComps();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		RenderSystem.enableBlend();
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		if (components != null && components.length > 0) {
			int i = 1;
			int q = 0;
			graphics.setColor(1f, 1f, 1f, 0.5f);
			for (int a = 0; a < components.length; a++) {
				if (!owns[a]) graphics.blit(TEXTURE, leftPos + 144 + q * 16, topPos + 16 + 16 * i, 240, 0, 16, 16);
				if (++i > 3) {
					i = 0;
					++q;
				}
			}
			graphics.setColor(1f, 1f, 1f, 1f);
		}
		if (menu.getCost() > 0) {
			int max = Math.max(1, menu.getMaxCost());
			graphics.blit(TEXTURE, leftPos + 145, topPos + 89, 209, 89, (int) (46.0f * (1.0f - menu.getCost() / (float) max)), 6);
			if (!disableAll) {
				disableAll = true;
				redoComps();
			}
		} else if (disableAll) {
			disableAll = false;
			redoComps();
		}
		graphics.drawCenteredString(font, "" + hearts, leftPos + 48, topPos + 108, 0xFFFFFF);
		graphics.drawCenteredString(font, "" + armor, leftPos + 72, topPos + 108, 0xFFFFFF);
		graphics.drawCenteredString(font, "" + damage, leftPos + 97, topPos + 108, 0xFFFFFF);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		if (components != null && components.length > 0) {
			graphics.drawString(font, "" + cost, 162 - font.width("" + cost), 24, 0xFFFFFF);
		}
		// the frames round the five part slots, drawn over the part icons as the original's foreground layer was
		RenderSystem.enableBlend();
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.blit(TEXTURE, 12, 12, 228, 124, 24, 24);
		graphics.blit(TEXTURE, 12, 60, 228, 124, 24, 24);
		graphics.blit(TEXTURE, 108, 12, 228, 124, 24, 24);
		graphics.blit(TEXTURE, 108, 36, 228, 124, 24, 24);
		graphics.blit(TEXTURE, 108, 60, 228, 124, 24, 24);
		graphics.pose().popPose();
		for (var child : children()) {
			if (child instanceof HoverButton b && b.isHovered()) {
				graphics.pose().pushPose();
				graphics.pose().translate(-leftPos, -topPos, 300);
				graphics.renderComponentTooltip(font, b.tooltipLines(), mouseX + 4, mouseY + (b.description == null ? 8 : 0));
				graphics.pose().popPose();
				break;
			}
		}
	}

	// ---- the original's little widgets ----

	/** GuiScrollButton: a 10x10 arrow from gui_base.png, brighter when hovered. */
	private final class ScrollButton extends AbstractWidget {
		private final boolean minus;
		private final Runnable action;

		ScrollButton(int x, int y, boolean minus, Runnable action) {
			super(x, y, 10, 10, Component.empty());
			this.minus = minus;
			this.action = action;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			RenderSystem.enableBlend();
			float c = active && isHovered() ? 1.0f : 0.9f;
			graphics.setColor(c, c, c, c);
			graphics.blit(GUI_BASE, getX(), getY(), minus ? 20 : 30, 0, 10, 10);
			graphics.setColor(1f, 1f, 1f, 1f);
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			action.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}

	/** GuiGolemCraftButton: the press button from gui_golembuilder.png, greyed out until everything is to hand. */
	private final class CraftButton extends AbstractWidget {
		CraftButton(int x, int y) {
			super(x, y, 24, 16, Component.empty());
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			RenderSystem.enableBlend();
			float c = active && isHovered() ? 1.0f : 0.9f;
			graphics.setColor(c, c, c, c);
			graphics.blit(TEXTURE, getX(), getY(), 216, 64, 24, 16);
			if (!active) graphics.blit(TEXTURE, getX(), getY(), 216, 40, 24, 16);
			graphics.setColor(1f, 1f, 1f, 1f);
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			if (!allfound) return;
			PacketDistributor.sendToServer(new ServerboundGolemBuilderPacket(props.toLong(), true));
			disableAll = true;
			redoComps();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}

	/** GuiHoverButton: a 16x16 icon centred on (x, y) - an aspect (in its colour, dimmer until hovered), a texture
	 * (tinted, e.g. the material's colour), or an item (lifted a pixel when hovered) - with a name/description tooltip.
	 * Not clickable, as the original's. */
	private final class HoverButton extends AbstractWidget {
		private final Component name;
		private final Component description;
		private final Object icon;
		private final int color;

		HoverButton(int cx, int cy, Component name, Component description, Object icon, int color) {
			super(cx - 8, cy - 8, 16, 16, name);
			this.name = name;
			this.description = description;
			this.icon = icon;
			this.color = color;
		}

		List<Component> tooltipLines() {
			List<Component> text = new ArrayList<>();
			if (icon instanceof ItemStack stack) {
				text.addAll(stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player,
						minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL));
			} else {
				text.add(name);
			}
			if (description != null) text.add(description.copy().withStyle(ChatFormatting.ITALIC, ChatFormatting.BLUE));
			return text;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			RenderSystem.enableBlend();
			boolean hot = isHovered();
			float r = ((color >> 16) & 255) / 255f;
			float g = ((color >> 8) & 255) / 255f;
			float b = (color & 255) / 255f;
			if (icon instanceof Aspect aspect) {
				int c = aspect.getColor();
				float d = hot ? 255f : 290f;
				graphics.setColor(((c >> 16) & 255) / d, ((c >> 8) & 255) / d, (c & 255) / d, hot ? 1.0f : 0.9f);
				graphics.blit(aspect.getImage(), getX(), getY(), 0, 0, 16, 16, 16, 16);
			} else if (icon instanceof ResourceLocation tex) {
				float m = hot ? 1.0f : 0.9f;
				graphics.setColor(r * m, g * m, b * m, m);
				graphics.blit(tex, getX(), getY(), 0, 0, 16, 16, 16, 16);
			} else if (icon instanceof ItemStack stack) {
				graphics.setColor(1f, 1f, 1f, 1f);
				graphics.renderItem(stack, getX(), getY() - (hot ? 1 : 0));
				graphics.renderItemDecorations(font, stack, getX(), getY() - (hot ? 1 : 0));
			}
			graphics.setColor(1f, 1f, 1f, 1f);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return false;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}

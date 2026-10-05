package thaumcraft.client.gui;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector2i;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusMedium;
import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.FocusModSplit;
import thaumcraft.api.casters.FocusNode;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.common.container.FocalManipulatorMenu;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.lib.network.ServerboundSaveFocusGraphPacket;
import thaumcraft.common.registry.ThaumcraftSounds;
import thaumcraft.common.tiles.crafting.FocusElementNode;
import thaumcraft.common.tiles.crafting.TileFocalManipulator;

/**
 * Replaces the old (1.12.2) {@code GuiFocalManipulator}, as the original: the spell is a tree on a canvas you drag to
 * scroll. The root sits at the top with an empty socket under it; click a socket (or a placed part) to select it and
 * click a part in the palette on the left to put it there (only parts the selected spot's parent can feed, and
 * researched, are offered - a Split forks the tree into two branches). Right-click a placed part to clear it and
 * everything after it. A selected part's settings get spinners on the right. The right-hand panel shows the total
 * complexity against the focus's limit, the XP and vis cost, the vis per cast and the crystals needed (hover them);
 * Start Crafting is lit once the tree is complete, within the limit and the crystals are carried. Every change goes to
 * the server at once, as the original did. The player inventory sits in the side panel on the left.
 */
public class GuiFocalManipulator extends AbstractContainerScreen<FocalManipulatorMenu> {

	private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_wandtable.png");
	private static final ResourceLocation TEX2 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_wandtable2.png");
	private static final ResourceLocation TEX3 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_wandtable3.png");
	private static final ResourceLocation TEXBASE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_base.png");
	private static final ResourceLocation I_MEDIUM = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/foci/_medium.png");
	private static final ResourceLocation I_EFFECT = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/foci/_effect.png");
	private static final ResourceLocation[] INFO_ICONS = {
			ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/complex.png"),
			ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/costxp.png"),
			ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/costvis.png") };
	private static final String[] INFO_TEXT = { "wandtable.text5", "wandtable.text2", "wandtable.text1" };
	private static final DecimalFormat DECIMAL = new DecimalFormat("#.##");

	private final TileFocalManipulator table;
	private final Map<Integer, FocusElementNode> data;
	private EditBox nameField;
	private int dragState;
	private int lastMouseX;
	private int lastMouseY;
	private int totalComplexity;
	private int maxComplexity;
	private int lastNodeHover = -1;
	private final DecimalFormat myFormatter = new DecimalFormat("#######.##");
	private final List<String> shownParts = new ArrayList<>();
	private final Map<String, FocusNode> partShown = new HashMap<>();
	private int partsStart;
	private ItemStack[] components;
	private boolean valid;
	private int nodeID;
	private int sMinX;
	private int sMinY;
	private int sMaxX;
	private int sMaxY;
	private int selectedNode = -1;
	private float costCast;
	private int costXp;
	private int costVis;
	private int scrollX;
	private int scrollY;
	private boolean doGuiReset;
	private ItemStack lastFocus = ItemStack.EMPTY;

	private Slider scrollbarParts;
	private Slider scrollbarMainSide;
	private Slider scrollbarMainBottom;
	private final List<Spinner> spinners = new ArrayList<>();

	public GuiFocalManipulator(FocalManipulatorMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.table = menu.getTile();
		this.data = table != null ? table.nodes : new HashMap<>();
		imageWidth = 231;
		imageHeight = 231;
	}

	@Override
	protected void init() {
		super.init();
		nameField = new EditBox(font, leftPos + 30, topPos + 11, 170, 12, Component.translatable("wandtable.name"));
		nameField.setTextColor(-1);
		nameField.setTextColorUneditable(-1);
		nameField.setBordered(false);
		nameField.setMaxLength(50);
		lastFocus = menu.getSlot(0).getItem().copy();
		// the original kept a doGuiReset flag on the tile, so a focus changed while the screen was shut still reset it
		if (!lastFocus.isEmpty() && data.isEmpty()) doGuiReset = true;
		gatherInfo(false);
		if (table != null) {
			if (table.focusName.isEmpty() && !focus().isEmpty()) table.focusName = focus().getHoverName().getString();
			nameField.setValue(table.focusName);
		}
		nameField.setResponder(s -> {
			if (table != null && !s.equals(table.focusName)) {
				table.focusName = s;
				send();
			}
		});
	}

	private ItemStack focus() {
		return menu.getSlot(0).getItem();
	}

	private float tableVis() {
		return table != null ? table.vis : 0.0f;
	}

	/** The original reset the canvas whenever the focus slot's item changed (TileFocalManipulator.setInventorySlotContents). */
	@Override
	protected void containerTick() {
		super.containerTick();
		ItemStack now = focus();
		if (!ItemStack.matches(now, lastFocus)) {
			lastFocus = now.copy();
			doGuiReset = true;
		}
	}

	// ------------------------------------------------------------------------------------------------ rendering

	@Override
	public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
		super.render(graphics, mx, my, partialTick);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		// the original's drag-to-scroll of the canvas
		if (GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
			if ((dragState == 0 || dragState == 1) && inRect(leftPos + 63, topPos + 31, 136, 160, mx, my)) {
				if (dragState == 0) {
					dragState = 1;
				} else {
					boolean b = scrollbarParts != null && scrollbarParts.over(mx, my);
					if (scrollbarMainBottom != null && !scrollbarMainBottom.over(mx, my) && !b) {
						scrollX -= mx - lastMouseX;
						scrollX = (int) Math.max(scrollbarMainBottom.min, Math.min(scrollbarMainBottom.max, scrollX));
						scrollbarMainBottom.setValue(scrollX);
					}
					if (scrollbarMainSide != null && !scrollbarMainSide.over(mx, my) && !b) {
						scrollY -= my - lastMouseY;
						scrollY = (int) Math.max(scrollbarMainSide.min, Math.min(scrollbarMainSide.max, scrollY));
						scrollbarMainSide.setValue(scrollY);
					}
				}
			}
			lastMouseX = mx;
			lastMouseY = my;
		} else {
			dragState = 0;
		}
		int gx = leftPos;
		int gy = topPos;
		// the palette, drawn over everything
		int count = 0;
		int index = 0;
		for (String sk : shownParts) {
			if (++count - 1 < partsStart) continue;
			FocusNode node = partShown.get(sk);
			graphics.pose().pushPose();
			graphics.pose().translate(0, 0, 305);
			if (node != null) {
				drawPart(graphics, node, gx + 38, 43 + gy + 25 * index, node.getType() == IFocusElement.EnumUnitType.MOD ? 24.0f : 32.0f,
						inRect(gx + 38 - 10, 32 + gy + 24 * index, 20, 20, mx, my));
			}
			graphics.pose().popPose();
			if (++index > 5) break;
		}
		count = 0;
		index = 0;
		for (String sk : shownParts) {
			if (++count - 1 < partsStart) continue;
			if (inRect(gx + 38 - 10, 33 + gy + 25 * index, 20, 20, mx, my)) {
				drawHoveringTextFixed(graphics, genPartText(partShown.get(sk), -1), gx + 44, 46 + gy + 24 * index, width - (leftPos + imageWidth - 16));
			}
			if (++index > 5) break;
		}
		if (lastNodeHover >= 0) {
			FocusElementNode fn = data.get(lastNodeHover);
			if (fn != null && fn.node != null) {
				int xx = leftPos + 140 - scrollX + fn.x * 24;
				int yy = topPos + 50 - scrollY + fn.y * 32;
				drawHoveringTextFixed(graphics, genPartText(fn.node, lastNodeHover), xx, yy, width - (leftPos + imageWidth - 16));
			}
		}
		if (!data.isEmpty() && nameField != null) {
			graphics.pose().pushPose();
			graphics.pose().translate(0, 0, 310);
			nameField.render(graphics, mx, my, partialTick);
			graphics.pose().popPose();
		}
		renderControlTooltips(graphics, mx, my);
		renderTooltip(graphics, mx, my);
	}

	private boolean confirmActive() {
		return tableVis() <= 0.0f && valid;
	}

	private List<Component> genPartText(FocusNode node, int idx) {
		List<Component> list = new ArrayList<>();
		if (node == null) return list;
		FocusElementNode placed = idx >= 0 ? data.get(idx) : null;
		list.add(Component.translatable(node.getUnlocalizedName()));
		list.add(Component.translatable(node.getUnlocalizedText()).withStyle(ChatFormatting.DARK_PURPLE));
		int c = node.getComplexity();
		if (placed != null) c = (int) (node.getComplexity() * placed.complexityMultiplier);
		MutableComponent com = Component.translatable("focuspart.com").withStyle(ChatFormatting.GOLD);
		com.append(Component.literal(" " + c).withStyle(placed != null && placed.complexityMultiplier > 1.0f ? ChatFormatting.RED : ChatFormatting.GOLD));
		list.add(com);
		float p = node.getPowerMultiplier();
		if (placed != null) p = placed.getPower(data);
		if (p != 1.0f) {
			list.add(Component.translatable("focuspart.eff").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(" x" + DECIMAL.format(p)).withStyle(p < 1.0f ? ChatFormatting.RED : ChatFormatting.GREEN)));
		}
		if (node instanceof FocusEffect fe) {
			float d = fe.getDamageForDisplay(placed == null ? 1.0f : placed.getPower(data));
			if (d > 0.0f) {
				list.add(Component.translatable("attribute.modifier.equals.0", DECIMAL.format(d),
						Component.translatable("attribute.name.generic.attack_damage")).withStyle(ChatFormatting.DARK_RED));
			} else if (d < 0.0f) {
				list.add(Component.translatable("attribute.modifier.equals.0", DECIMAL.format(-d),
						Component.translatable("focus.heal.power")).withStyle(ChatFormatting.DARK_GREEN));
			}
		}
		return list;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mx, int my) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		int k = leftPos;
		int l = topPos;
		graphics.blit(TEX2, k, l, 0, 0, imageWidth, imageHeight);
		graphics.blit(TEX3, k - 71, l - 3, 0, 0, 71, 239);
		if (table != null && (focus().isEmpty() || doGuiReset)) {
			if (doGuiReset) {
				resetNodes();
			} else {
				data.clear();
				gatherInfo(false);
			}
			table.focusName = "";
			if (!focus().isEmpty()) {
				table.focusName = focus().getHoverName().getString();
				nameField.setValue(table.focusName);
			}
			doGuiReset = false;
		}
		drawNodes(graphics, leftPos + 132 - scrollX, topPos + 48 - scrollY, mx, my);
		RenderSystem.enableBlend();
		graphics.blit(TEX, k, l, 0, 0, imageWidth, imageHeight);
		if (maxComplexity > 0) {
			graphics.drawString(font, totalComplexity + "/" + maxComplexity, k + 242, l + 36, totalComplexity > maxComplexity ? 16151160 : 16761407, true);
		}
		graphics.drawString(font, "" + costXp, k + 242, l + 50, costXp > minecraft.player.experienceLevel ? 16151160 : 10092429, true);
		int v = costVis;
		if (tableVis() > 0.0f) v = (int) tableVis();
		graphics.drawString(font, Component.literal("" + v).withStyle(ChatFormatting.AQUA), k + 242, l + 64, 10092429, true);
		if (costCast > 0.0f) {
			graphics.drawString(font, Component.translatable("item.Focus.cost1").append(": " + myFormatter.format(costCast)).withStyle(ChatFormatting.AQUA),
					k + 230, l + 80, 10092429, true);
		}
		if (components != null && components.length > 0) {
			graphics.drawString(font, Component.translatable("wandtable.text4").withStyle(ChatFormatting.GOLD), k + 230, l + 92, 10092429, true);
		}
		if (table != null && !data.isEmpty()) {
			graphics.blit(TEXBASE, leftPos + 24, topPos + 8, 192, 224, 8, 14);
			int a;
			for (a = 1; a < 22; ++a) graphics.blit(TEXBASE, leftPos + 24 + a * 8, topPos + 8, 200, 224, 8, 14);
			graphics.blit(TEXBASE, leftPos + 24 + a * 8, topPos + 8, 208, 224, 8, 14);
		}
		renderControls(graphics, mx, my);
	}

	/** The original's buttons: Start Crafting, the three cost icons, the setting spinners, the crystals and the scroll bars. */
	private void renderControls(GuiGraphics graphics, int mx, int my) {
		// Start Crafting (GuiImageButton, centred on its x/y)
		int bx = leftPos + 242;
		int by = topPos + 18;
		boolean hover = mx >= bx - 12 && my >= by - 8 && mx < bx + 12 && my < by + 8;
		float cc = hover ? 1.0f : 0.9f;
		float ac = 1.0f;
		if (!confirmActive()) {
			cc = 0.5f;
			ac = 0.9f;
		}
		RenderSystem.setShaderColor(cc, cc, cc, ac);
		graphics.blit(TEXBASE, bx - 12, by - 8, 232, 240, 24, 16);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		graphics.pose().pushPose();
		graphics.pose().translate(bx, by, 0);
		graphics.pose().scale(0.5f, 0.5f, 1);
		graphics.drawCenteredString(font, Component.translatable("wandtable.text3"), 0, -4, hover ? 16777120 : 16777215);
		graphics.pose().popPose();
		// the cost icons (GuiHoverButton)
		for (int i = 0; i < 3; i++) {
			int x = leftPos + 241;
			int y = topPos + 39 + i * 14;
			boolean h = mx >= x - 16 && my >= y - 8 && mx < x + 16 && my < y + 8;
			float c = h ? 1.0f : 0.9f;
			RenderSystem.setShaderColor(c, c, c, h ? 1.0f : 0.9f);
			graphics.blit(INFO_ICONS[i], x - 16, y - 8, 0, 0, 16, 16, 16, 16);
			RenderSystem.setShaderColor(1, 1, 1, 1);
		}
		for (Spinner s : spinners) s.render(graphics, mx, my);
		// the crystals
		if (components != null) {
			for (int z = 0; z < components.length; z++) {
				int x = crystalX(z);
				int y = crystalY(z);
				boolean h = mx >= x - 8 && my >= y - 8 && mx < x + 8 && my < y + 8;
				graphics.renderItem(components[z], x - 8, y - 8 - (h ? 1 : 0));
				graphics.renderItemDecorations(font, components[z], x - 8, y - 8 - (h ? 1 : 0));
			}
		}
		for (Slider s : new Slider[] { scrollbarParts, scrollbarMainSide, scrollbarMainBottom }) {
			if (s != null) s.render(graphics, mx, my);
		}
		// the original's foreground step: the scroll positions follow the bars
		if (scrollbarMainSide != null) scrollY = Math.round(scrollbarMainSide.value());
		if (scrollbarMainBottom != null) scrollX = Math.round(scrollbarMainBottom.value());
		if (scrollbarParts != null) partsStart = Math.round(scrollbarParts.value());
	}

	private int crystalX(int z) {
		return leftPos + 234 + (z % 5) * 16;
	}

	private int crystalY(int z) {
		return topPos + 110 + 16 * (z / 5);
	}

	private void renderControlTooltips(GuiGraphics graphics, int mx, int my) {
		int bx = leftPos + 242;
		int by = topPos + 18;
		if (mx >= bx - 12 && my >= by - 8 && mx < bx + 12 && my < by + 8) {
			graphics.renderTooltip(font, Component.translatable("wandtable.text3"), mx + 4, my + 8);
			return;
		}
		for (int i = 0; i < 3; i++) {
			int x = leftPos + 241;
			int y = topPos + 39 + i * 14;
			if (mx >= x - 16 && my >= y - 8 && mx < x + 16 && my < y + 8) {
				graphics.renderTooltip(font, Component.translatable(INFO_TEXT[i]), mx + 4, my + 8);
				return;
			}
		}
		if (components != null) {
			for (int z = 0; z < components.length; z++) {
				int x = crystalX(z);
				int y = crystalY(z);
				if (mx >= x - 8 && my >= y - 8 && mx < x + 8 && my < y + 8) {
					graphics.renderTooltip(font, components[z], mx + 4, my + 8);
					return;
				}
			}
		}
	}

	/** No labels: the original drew none. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
	}

	private boolean clipped(int rx, int ry, int w, int h, int ax, int ay) {
		return inRect(leftPos + rx, topPos + ry, w, h, ax, ay);
	}

	private void drawClippedRect(GuiGraphics graphics, int x, int y, int sx, int sy, int w, int h) {
		if (clipped(48, 26, 166, 174, x + w / 2, y + h / 2)) graphics.blit(TEX, x, y, sx, sy, w, h);
	}

	private void drawNodes(GuiGraphics graphics, int x, int y, int mx, int my) {
		if (data.isEmpty()) return;
		int hover = -1;
		for (FocusElementNode fn : data.values()) {
			int xx = x + fn.x * 24;
			int yy = y + fn.y * 32;
			boolean mouseover = inRect(leftPos + 63, topPos + 31, 136, 160, mx, my) && inRect(xx - 10, yy - 10, 20, 20, mx, my);
			if (mouseover && fn.parent >= 0) hover = fn.id;
			if (fn.node != null) {
				if (clipped(48, 16, 154, 192, xx - 8, yy - 8)) drawPart(graphics, fn.node, xx, yy, 32.0f, mouseover);
			} else {
				drawClippedRect(graphics, xx - 12, yy - 12, 120, 232, 24, 24);
			}
			if (selectedNode == fn.id || (mouseover && fn.parent >= 0)) drawClippedRect(graphics, xx - 12, yy - 12, 96, 232, 24, 24);
			FocusElementNode parent = data.get(fn.parent);
			if (parent == null) continue;
			drawClippedRect(graphics, xx - 6, yy - 22, 54, 232, 12, 12);
			if (parent.node instanceof FocusModSplit) {
				for (int q = Math.abs(fn.x - parent.x), a = 0; a < q; ++a) {
					if (fn.x < parent.x) {
						if (a == 0) drawClippedRect(graphics, xx - 4, yy - 36, 8, 240, 16, 16);
						else drawClippedRect(graphics, xx - 12 + a * 24, yy - 36, 72, 240, 24, 16);
					} else if (a == 0) {
						drawClippedRect(graphics, xx - 12, yy - 36, 24, 240, 16, 16);
					} else {
						drawClippedRect(graphics, xx - 12 - a * 24, yy - 36, 72, 240, 24, 16);
					}
				}
			}
			if (fn.node != null) continue;
			// an empty socket shows what its parent feeds it: a target and / or a trajectory
			int s = parent.target && parent.trajectory ? 4 : 0;
			if (!clipped(48, 16, 168, 192, xx - 4, yy - 4)) continue;
			if (parent.target) {
				graphics.pose().pushPose();
				graphics.pose().translate(xx - s, yy, 0);
				graphics.pose().scale(0.5f, 0.5f, 1);
				graphics.blit(TEX, -8, -8, 152, 240, 16, 16);
				graphics.pose().popPose();
			}
			if (parent.trajectory) {
				graphics.pose().pushPose();
				graphics.pose().translate(xx + s, yy, 0);
				graphics.pose().scale(0.5f, 0.5f, 1);
				graphics.blit(TEX, -8, -8, 168, 240, 16, 16);
				graphics.pose().popPose();
			}
		}
		if (hover >= 0 && lastNodeHover != hover) {
			playRollover();
			lastNodeHover = hover;
		}
		if (hover < 0) lastNodeHover = -1;
		if (selectedNode >= 0) drawNodeSettings(graphics, selectedNode);
	}

	private void drawNodeSettings(GuiGraphics graphics, int selected) {
		FocusElementNode fn = data.get(selected);
		if (fn == null || fn.node == null || fn.node.getSettingList().isEmpty()) return;
		int a = 0;
		for (String sk : fn.node.getSettingList()) {
			NodeSetting setting = fn.node.getSetting(sk);
			if (setting.getResearch() != null && !ThaumcraftCapabilities.knowsResearchStrict(minecraft.player, setting.getResearch())) continue;
			int x = leftPos + imageWidth;
			int y = topPos + imageHeight - 10 - fn.node.getSettingList().size() * 26 + a * 26;
			graphics.drawString(font, setting.getLocalizedName().copy().withStyle(ChatFormatting.GOLD), x, y, 16777215, true);
			++a;
		}
	}

	/** The original's drawPart: a part's tinted backing shape (round for effects, square for media, none for modifiers and
	 * the root, which are drawn twice as large) and its icon on top. */
	public static void drawPart(GuiGraphics graphics, FocusNode node, int x, int y, float scale, boolean mouseover) {
		boolean root = node.getType() == IFocusElement.EnumUnitType.MOD || node.getKey().equals("thaumcraft.ROOT");
		if (root) scale *= 2.0f;
		int color = FocusEngine.getElementColor(node.getKey());
		float r = ((color >> 16) & 255) / 255.0f;
		float g = ((color >> 8) & 255) / 255.0f;
		float b = (color & 255) / 255.0f;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		if (node.getType() == IFocusElement.EnumUnitType.EFFECT) {
			quad(graphics, I_EFFECT, x, y, scale * 0.9f + (mouseover ? 2 : 0), r, g, b);
		} else if (node.getType() == IFocusElement.EnumUnitType.MEDIUM && !root) {
			quad(graphics, I_MEDIUM, x, y, scale * 0.9f + (mouseover ? 2 : 0), r, g, b);
		}
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 1);
		quad(graphics, FocusEngine.getElementIcon(node.getKey()), x, y, scale / 2.0f + (mouseover ? 2 : 0), 1, 1, 1);
		graphics.pose().popPose();
	}

	private static void quad(GuiGraphics graphics, ResourceLocation tex, int cx, int cy, float size, float r, float g, float b) {
		if (tex == null) return;
		graphics.pose().pushPose();
		graphics.pose().translate(cx, cy, 0);
		graphics.pose().scale(size / 32.0f, size / 32.0f, 1);
		RenderSystem.setShaderColor(r, g, b, 1);
		graphics.blit(tex, -16, -16, 0, 0, 32, 32, 32, 32);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		graphics.pose().popPose();
	}

	/** The original's drawHoveringTextFixed: a tooltip pinned beside the thing it describes, wrapped to the space on
	 * the right. */
	private void drawHoveringTextFixed(GuiGraphics graphics, List<Component> lines, int x, int y, int textWidth) {
		if (lines.isEmpty()) return;
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component c : lines) out.addAll(font.split(c, Math.max(textWidth, 20)));
		graphics.renderTooltip(font, out, (sw, sh, px, py, w, h) -> new Vector2i(x + 12, y - 12), x, y);
	}

	// ------------------------------------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mxd, double myd, int button) {
		int mx = (int) mxd;
		int my = (int) myd;
		// the original's buttons (left click only)
		if (button == 0) {
			int bx = leftPos + 242;
			int by = topPos + 18;
			if (confirmActive() && mx >= bx - 12 && my >= by - 8 && mx < bx + 12 && my < by + 8) {
				clickSound();
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
			}
			for (Slider s : new Slider[] { scrollbarParts, scrollbarMainSide, scrollbarMainBottom }) {
				if (s != null && s.over(mx, my)) {
					s.down = true;
					s.track(mx, my);
					clickSound();
				}
			}
			for (Spinner s : new ArrayList<>(spinners)) {
				if (s.click(mx, my)) {
					clickSound();
					gatherInfo(true);
					break;
				}
			}
		}
		super.mouseClicked(mxd, myd, button);
		if (table != null && tableVis() <= 0.0f && !data.isEmpty()) {
			if (nameField != null) {
				boolean in = nameField.isMouseOver(mxd, myd);
				nameField.setFocused(in);
				if (in) nameField.mouseClicked(mxd, myd, button);
			}
			if (lastNodeHover >= 0) {
				selectedNode = lastNodeHover;
				if (button == 1 && data.get(selectedNode) != null && data.get(selectedNode).node != null) {
					FocusElementNode fn = data.get(selectedNode);
					FocusElementNode parent = data.get(fn.parent);
					if (parent != null && parent.node != null) addNodeAt(parent.node.getClass(), fn.parent, true);
				}
				gatherInfo(false);
				playButtonClick();
			}
			int count = 0;
			int index = 0;
			if (selectedNode >= 0) {
				for (String sk : shownParts) {
					if (++count - 1 < partsStart) continue;
					if (inRect(leftPos + 28, topPos + 32 + 24 * index, 20, 20, mx, my)) {
						addNodeAt(FocusEngine.elements.get(sk), selectedNode, true);
						return true;
					}
					if (++index > 5) break;
				}
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		for (Slider s : new Slider[] { scrollbarParts, scrollbarMainSide, scrollbarMainBottom }) {
			if (s != null) s.down = false;
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (scrollbarParts != null && shownParts.size() > 6 && clipped(24, 24, 32, 157, (int) mx, (int) my)) {
			if (scrollY > 0) {
				if (partsStart > 0) {
					--partsStart;
					scrollbarParts.setValue(partsStart);
				}
			} else if (scrollY < 0 && partsStart < shownParts.size() - 6) {
				++partsStart;
				scrollbarParts.setValue(partsStart);
			}
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (nameField != null && nameField.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
			nameField.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		if (nameField != null && nameField.isFocused()) return nameField.charTyped(c, modifiers);
		return super.charTyped(c, modifiers);
	}

	private void clickSound() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	private void playButtonClick() {
		if (minecraft.player != null) minecraft.player.playSound(ThaumcraftSounds.CLACK.get(), 0.4f, 1.0f);
	}

	private void playRollover() {
		if (minecraft.player != null) minecraft.player.playSound(ThaumcraftSounds.CLACK.get(), 0.4f, 2.0f);
	}

	private static boolean inRect(int x, int y, int w, int h, int px, int py) {
		// GuiContainer#isPointInRegion's one-pixel margin
		return px >= x - 1 && px < x + w + 1 && py >= y - 1 && py < y + h + 1;
	}

	// ------------------------------------------------------------------------------------------------ the tree

	private int getNextId() {
		while (data.containsKey(nodeID)) ++nodeID;
		return nodeID;
	}

	private void cullChildren(int idx) {
		if (data.containsKey(idx)) {
			for (int i : data.get(idx).children) {
				cullChildren(i);
				data.remove(i);
			}
		}
	}

	private List<Integer> addNodeAt(Class<?> nodeClass, int idx, boolean gather) {
		List<Integer> ret = new ArrayList<>();
		boolean same = false;
		FocusElementNode previous = null;
		if (data.containsKey(idx)) {
			cullChildren(idx);
			if (data.get(idx).node != null && data.get(idx).node.getClass() == nodeClass) {
				same = true;
			} else {
				previous = data.remove(idx);
			}
		}
		try {
			FocusElementNode fn;
			FocusNode node;
			if (!same) {
				fn = new FocusElementNode();
				node = (FocusNode) nodeClass.getDeclaredConstructor().newInstance();
				fn.node = node;
				if (previous != null) {
					fn.y = previous.y;
					fn.x = previous.x;
				}
				fn.id = getNextId();
				ret.add(fn.id);
				selectedNode = fn.id;
				if (previous != null && data.containsKey(previous.parent)) {
					fn.parent = previous.parent;
					int[] c = data.get(previous.parent).children;
					for (int a = 0; a < c.length; ++a) {
						if (c[a] == previous.id) {
							c[a] = fn.id;
							break;
						}
					}
				}
				fn.target = node.canSupply(FocusNode.EnumSupplyType.TARGET);
				fn.trajectory = node.canSupply(FocusNode.EnumSupplyType.TRAJECTORY);
				data.put(nodeID, fn);
			} else {
				fn = data.get(idx);
				node = fn.node;
			}
			if (fn.target || fn.trajectory) {
				if (node instanceof FocusModSplit) {
					FocusElementNode blank1 = new FocusElementNode();
					blank1.parent = fn.id;
					blank1.id = getNextId();
					ret.add(nodeID);
					blank1.x = fn.x - 1;
					blank1.y = fn.y + 1;
					data.put(nodeID, blank1);
					selectedNode = nodeID;
					FocusElementNode blank2 = new FocusElementNode();
					blank2.parent = fn.id;
					blank2.x = fn.x + 1;
					blank2.y = fn.y + 1;
					blank2.id = getNextId();
					ret.add(nodeID);
					data.put(nodeID, blank2);
					fn.children = new int[] { blank1.id, blank2.id };
				} else {
					FocusElementNode blank3 = new FocusElementNode();
					blank3.parent = fn.id;
					blank3.x = fn.x;
					blank3.y = fn.y + 1;
					blank3.id = getNextId();
					ret.add(nodeID);
					data.put(nodeID, blank3);
					fn.children = new int[] { blank3.id };
					selectedNode = nodeID;
				}
			}
		} catch (Exception e) {
			thaumcraft.Thaumcraft.LOGGER.debug("Couldn't add focus part {}", nodeClass, e);
		}
		if (gather) {
			calcNodeTreeLayout();
			gatherInfo(true);
		}
		return ret;
	}

	private void processLeftNodes(FocusElementNode start, int[] bounds) {
		if (start.children.length > 0) processLeftNodes(data.get(start.children[0]), bounds);
		int ox = 0;
		if (start.children.length == 1) {
			ox = bounds[0] - 1;
			bounds[0] = data.get(start.children[0]).x;
		}
		start.x = bounds[0];
		if (start.children.length == 1) bounds[0] = ox;
		++bounds[0];
		if (start.children.length > 1) processLeftNodes(data.get(start.children[1]), bounds);
	}

	private void moveNodes(FocusElementNode start, int amt) {
		for (int ci : start.children) moveNodes(data.get(ci), amt);
		start.x -= amt;
	}

	private void calcNodeTreeLayout() {
		int fsi = -1;
		for (FocusElementNode node : data.values()) {
			if (fsi < 0 && node.node instanceof FocusModSplit) fsi = node.id;
		}
		if (fsi >= 0) {
			int[] bounds = { 0 };
			processLeftNodes(data.get(fsi), bounds);
			moveNodes(data.get(fsi), bounds[0] / 2);
		}
		for (FocusElementNode node : data.values()) {
			if (node.node instanceof FocusModSplit) {
				if (data.containsKey(node.parent) && data.get(node.parent).node != null && !(data.get(node.parent).node instanceof FocusModSplit)) {
					node.x = data.get(node.parent).x;
				} else {
					int xx = 0;
					for (int a : node.children) xx += data.get(a).x;
					xx /= node.children.length;
					node.x = xx;
				}
			}
		}
		if (selectedNode >= 0 && !data.containsKey(selectedNode)) selectedNode = -1;
	}

	private void resetNodes() {
		nodeID = 0;
		data.clear();
		List<Integer> r1 = addNodeAt(FocusMediumRoot.class, 0, false);
		selectedNode = r1.size() > 1 ? r1.get(1) : -1;
		calcNodeTreeLayout();
		gatherInfo(true);
	}

	private void calcScrollBounds() {
		sMinX = 0;
		sMinY = 0;
		sMaxX = 0;
		sMaxY = 0;
		for (FocusElementNode fn : data.values()) {
			sMinX = Math.min(sMinX, fn.x);
			sMinY = Math.min(sMinY, fn.y);
			sMaxX = Math.max(sMaxX, fn.x);
			sMaxY = Math.max(sMaxY, fn.y);
		}
	}

	private void gatherPartsList() {
		shownParts.clear();
		partShown.clear();
		if (minecraft == null || minecraft.player == null) return;
		if (selectedNode < 0 || !data.containsKey(selectedNode)) return;
		partsStart = 0;
		List<String> pMed = new ArrayList<>();
		List<String> pEff = new ArrayList<>();
		List<String> pMod = new ArrayList<>();
		List<String> excluded = new ArrayList<>();
		boolean hasExclusive = false;
		boolean hasMedium = false;
		for (FocusElementNode fn : data.values()) {
			if (fn.node instanceof FocusMedium) {
				hasMedium = !(fn.node instanceof FocusMediumRoot);
				if (fn.node.isExclusive()) {
					hasExclusive = true;
					break;
				}
			}
			if (fn.node != null && fn.node.isExclusive()) excluded.add(fn.node.getKey());
		}
		FocusElementNode node = data.get(selectedNode);
		FocusElementNode parent = data.get(node.parent);
		if (parent != null && parent.node != null) {
			for (String key : FocusEngine.elements.keySet()) {
				IFocusElement element = FocusEngine.getElement(key);
				if (element == null || !ThaumcraftCapabilities.knowsResearchStrict(minecraft.player, element.getResearch())) continue;
				if (element.getKey().equals("thaumcraft.ROOT")) continue;
				if (!(element instanceof FocusNode fn2)) continue;
				if (excluded.contains(fn2.getKey())) continue;
				if (fn2.mustBeSupplied() == null) continue;
				for (FocusNode.EnumSupplyType type : fn2.mustBeSupplied()) {
					if (parent.node.canSupply(type)) {
						switch (element.getType()) {
							case EFFECT -> pEff.add(key);
							case MEDIUM -> {
								if (!hasExclusive && (!((FocusMedium) element).isExclusive() || !hasMedium)) pMed.add(key);
							}
							case MOD -> pMod.add(key);
						}
						partShown.put(key, fn2);
						break;
					}
				}
			}
		}
		Collections.sort(pMed);
		Collections.sort(pEff);
		Collections.sort(pMod);
		shownParts.addAll(pMed);
		shownParts.addAll(pEff);
		shownParts.addAll(pMod);
	}

	private void gatherInfo(boolean sync) {
		spinners.clear();
		FocusElementNode sel = data.get(selectedNode);
		if (sel != null && sel.node != null && !sel.node.getSettingList().isEmpty()) {
			int a = 0;
			for (String sk : sel.node.getSettingList()) {
				NodeSetting setting = sel.node.getSetting(sk);
				if (setting.getResearch() != null && !ThaumcraftCapabilities.knowsResearchStrict(minecraft.player, setting.getResearch())) continue;
				int x = leftPos + imageWidth;
				int y = topPos + imageHeight + 3 - sel.node.getSettingList().size() * 26 + a * 26;
				int w = setting.getType() instanceof NodeSetting.NodeSettingIntList ? 72 : 32;
				spinners.add(new Spinner(x, y, w, setting));
				++a;
			}
		}
		shownParts.clear();
		components = null;
		if (focus().isEmpty()) {
			// the original's button list was cleared here, so its scroll bars went with it
			scrollbarParts = null;
			scrollbarMainSide = null;
			scrollbarMainBottom = null;
			return;
		}
		Map<String, Integer> compCount = new HashMap<>();
		totalComplexity = 0;
		maxComplexity = 0;
		if (focus().getItem() instanceof ItemFocus item) maxComplexity = item.getMaxComplexity();
		boolean emptyNodes = false;
		AspectList crystals = new AspectList();
		for (FocusElementNode node : data.values()) {
			if (node.node != null) {
				int a2 = compCount.getOrDefault(node.node.getKey(), 0) + 1;
				node.complexityMultiplier = 0.5f * (a2 + 1);
				compCount.put(node.node.getKey(), a2);
				totalComplexity += (int) (node.node.getComplexity() * node.complexityMultiplier);
				if (node.node.getAspect() != null) crystals.add(node.node.getAspect(), 1);
			} else {
				emptyNodes = true;
			}
		}
		costCast = totalComplexity / 5.0f;
		costVis = totalComplexity * 10 + maxComplexity / 5;
		// as the original's screen (Math.min, so it reads 0 or 1; the levels actually taken are max(1, sqrt))
		costXp = (int) Math.min(1L, Math.round(Math.sqrt(totalComplexity)));
		boolean validCrystals = false;
		if (crystals.getAspects().length > 0) {
			validCrystals = true;
			components = new ItemStack[crystals.getAspects().length];
			int r = 0;
			for (Aspect as : crystals.getAspects()) {
				components[r] = TileFocalManipulator.makeCrystal(as, crystals.getAmount(as));
				if (!TileFocalManipulator.hasCrystal(minecraft.player, as, crystals.getAmount(as))) validCrystals = false;
				++r;
			}
		}
		gatherPartsList();
		scrollbarParts = null;
		if (shownParts.size() > 6) {
			scrollbarParts = new Slider(leftPos + 51, topPos + 30, 8, 149, 0.0f, shownParts.size() - 6, 0.0f, true);
		}
		valid = totalComplexity <= maxComplexity && !emptyNodes && validCrystals;
		calcScrollBounds();
		if (scrollY > (sMaxY - 3) * 32) scrollY = (sMaxY - 3) * 32;
		if (scrollX > sMaxX * 24) scrollX = sMaxX * 24;
		if (scrollX < sMinX * 24) scrollX = sMinX * 24;
		scrollbarMainSide = null;
		scrollbarMainBottom = null;
		if (sMaxY * 32 > 130) {
			scrollbarMainSide = new Slider(leftPos + 203, topPos + 32, 8, 156, 0.0f, (sMaxY - 3) * 32, scrollY, true);
		} else {
			scrollY = 0;
		}
		if (sMinX * 24 < -70 || sMaxX * 24 > 70) {
			scrollbarMainBottom = new Slider(leftPos + 64, topPos + 195, 132, 8, sMinX * 24, sMaxX * 24, scrollX, false);
		} else {
			scrollX = 0;
		}
		if (table != null) {
			if (table.focusName.isEmpty()) table.focusName = focus().getHoverName().getString();
			if (nameField != null && !nameField.getValue().equals(table.focusName)) nameField.setValue(table.focusName);
		}
		if (sync) send();
	}

	/** The original's PacketFocusNodesToServer / PacketFocusNameToServer: the whole tree and the name. */
	private void send() {
		if (table == null) return;
		List<CompoundTag> tags = new ArrayList<>(data.size());
		for (FocusElementNode node : data.values()) tags.add(node.serialize());
		PacketDistributor.sendToServer(new ServerboundSaveFocusGraphPacket(tags, table.focusName));
	}

	// ------------------------------------------------------------------------------------------------ widgets

	/** The original's GuiSliderTC: a thin track with an 8x8 thumb; clicking or dragging sets it. */
	private static final class Slider {
		final int x;
		final int y;
		final int w;
		final int h;
		final float min;
		final float max;
		final boolean vertical;
		float pos;
		boolean down;

		Slider(int x, int y, int w, int h, float min, float max, float value, boolean vertical) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.min = min;
			this.max = max;
			this.vertical = vertical;
			setValue(value);
		}

		float value() {
			return min + (max - min) * pos;
		}

		void setValue(float v) {
			pos = (v - min) / (max - min);
		}

		boolean over(int mx, int my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}

		void track(int mx, int my) {
			pos = vertical ? (my - (y + 4)) / (float) (h - 8) : (mx - (x + 4)) / (float) (w - 8);
			pos = Math.max(0.0f, Math.min(1.0f, pos));
		}

		void render(GuiGraphics graphics, int mx, int my) {
			RenderSystem.enableBlend();
			graphics.pose().pushPose();
			if (vertical) {
				graphics.pose().translate(x + 2, y, 0);
				graphics.pose().scale(1.0f, h / 32.0f, 1.0f);
				graphics.blit(TEXBASE, 0, 0, 240, 176, 4, 32);
			} else {
				graphics.pose().translate(x, y + 2, 0);
				graphics.pose().scale(w / 32.0f, 1.0f, 1.0f);
				graphics.blit(TEXBASE, 0, 0, 208, 176, 32, 4);
			}
			graphics.pose().popPose();
			if (down) track(mx, my);
			if (vertical) graphics.blit(TEXBASE, x, y + (int) (pos * (h - 8)), 20, 20, 8, 8);
			else graphics.blit(TEXBASE, x + (int) (pos * (w - 8)), y, 20, 20, 8, 8);
		}
	}

	/** The original's GuiFocusSettingSpinnerButton: a setting's value between left and right arrows. */
	private final class Spinner {
		final int x;
		final int y;
		final int w;
		final NodeSetting setting;

		Spinner(int x, int y, int w, NodeSetting setting) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.setting = setting;
		}

		void render(GuiGraphics graphics, int mx, int my) {
			boolean hovered = mx >= x && my >= y && mx < x + w + 10 && my < y + 10;
			float c = hovered ? 1.0f : 0.9f;
			RenderSystem.enableBlend();
			RenderSystem.setShaderColor(c, c, c, c);
			graphics.blit(TEXBASE, x, y, 20, 0, 10, 10);
			graphics.blit(TEXBASE, x + w, y, 30, 0, 10, 10);
			RenderSystem.setShaderColor(1, 1, 1, 1);
			Component s = setting.getValueText();
			graphics.drawString(font, s, x + (w + 10) / 2 - font.width(s) / 2, y + 1, 16777215, true);
		}

		boolean click(int mx, int my) {
			if (mx >= x && my >= y && mx < x + 10 && my < y + 10) {
				setting.decrement();
				return true;
			}
			if (mx >= x + w && my >= y && mx < x + w + 10 && my < y + 10) {
				setting.increment();
				return true;
			}
			return false;
		}
	}
}

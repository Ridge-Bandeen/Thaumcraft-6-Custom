package thaumcraft.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.joml.Matrix4f;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.client.ClientPlayerKnowledgeCache;
import thaumcraft.common.container.ThaumonomiconMenu;
import thaumcraft.common.lib.research.ResearchRecipeCatalog;

/**
 * The Thaumonomicon: a port of the original's {@code GuiResearchBrowser}, with its {@code GuiResearchPage} as
 * {@link ResearchPageView} opened on top. Same layout and numbers throughout - the 16px inset map pane with its
 * wooden frame, category tabs down the left (with completion percentage on hover), the node map in 24px cells panned
 * by dragging and zoomed 1x-2x with the wheel, the tiled two-layer parallax background, the connector-line pieces,
 * node frames by meta, the "new" badges, the pulsing violet glow behind nodes that cost warp, and the search mode
 * (categories, known research and the recipes of the stage you're on).
 *
 * <p>This is a container screen only so its clicks have a server round trip ({@link ThaumonomiconMenu}); it has no
 * slots and draws everything itself.
 */
public class ThaumonomiconScreen extends AbstractContainerScreen<ThaumonomiconMenu> {

	static final ResourceLocation TX1 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_browser.png");
	private static final ResourceLocation NODE_TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/auranodes.png");
	private static final ResourceLocation FOCUS_EFFECT = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/foci/_effect.png");
	private static final ResourceLocation FOCUS_MEDIUM = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/foci/_medium.png");
	/** ConfigResearch.TCCategories - these get the left-hand tab column; anything else goes on the right. */
	private static final List<String> TC_CATEGORIES = List.of("BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH");

	// the original's statics: where the map was left, which category, search mode and the addon-tab scroll
	private static double lastX = -9999.0;
	private static double lastY = -9999.0;
	static String selectedCategory;
	private static boolean searching;
	private static int catScrollPos;
	private static int catScrollMax;

	private int boundsLeft, boundsTop, boundsRight, boundsBottom;
	private final int startX = 16;
	private final int startY = 16;
	private int screenX, screenY;
	private float screenZoom = 1.0f;
	private double curMouseX, curMouseY, guiMapX, guiMapY, tempMapX, tempMapY;
	private boolean dragging;
	private double dragMouseX, dragMouseY;
	private int addonShift;

	private final List<ResearchEntry> research = new ArrayList<>();
	private final Set<String> invisible = new HashSet<>();
	private ResearchEntry currentHighlight;
	private final List<CategoryTab> tabs = new ArrayList<>();
	private boolean scrollButtons;
	private EditBox searchField;
	private final List<SearchResult> searchResults = new ArrayList<>();

	/** The open book page, or null while browsing. */
	private ResearchPageView page;

	private record CategoryTab(ResearchCategory category, String key, boolean flip, int x, int y, int completion) {
	}

	private record SearchResult(String label, String key, ResourceLocation recipe, boolean cat) implements Comparable<SearchResult> {
		@Override
		public int compareTo(SearchResult o) {
			int c = label.compareTo(o.label);
			if (c != 0) return c;
			c = key.compareTo(o.key);
			return c == 0 && recipe != null && o.recipe != null ? recipe.compareTo(o.recipe) : c;
		}
	}

	public ThaumonomiconScreen(ThaumonomiconMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		tempMapX = guiMapX = curMouseX = lastX;
		tempMapY = guiMapY = curMouseY = lastY;
	}

	@Override
	protected void init() {
		imageWidth = width;
		imageHeight = height;
		super.init();
		updateResearch();
		if (lastX == -9999.0 || guiMapX > boundsRight || guiMapX < boundsLeft) {
			tempMapX = guiMapX = (boundsLeft + boundsRight) / 2;
		}
		if (lastY == -9999.0 || guiMapY > boundsBottom || guiMapY < boundsTop) {
			tempMapY = guiMapY = (boundsBottom + boundsTop) / 2;
		}
		if (page != null) page.init();
	}

	IPlayerKnowledge knowledge() {
		return ClientPlayerKnowledgeCache.getData();
	}

	/** ThaumcraftCapabilities#knowsResearchStrict against the synced client copy: "KEY@n" needs stage n, a bare key completion. */
	boolean knowsStrict(String key) {
		if (key == null) return true;
		IPlayerKnowledge k = knowledge();
		return key.indexOf('@') >= 0 ? k.isResearchKnown(key) : k.isResearchComplete(key);
	}

	boolean knowsStrict(String[] keys) {
		if (keys == null) return true;
		for (String key : keys) if (!knowsStrict(key)) return false;
		return true;
	}

	void updateResearch() {
		screenX = width - 32;
		screenY = height - 32;
		research.clear();
		if (selectedCategory == null) selectedCategory = ResearchCategories.researchCategories.keySet().iterator().next();
		searchField = new EditBox(font, 20, 20, 89, font.lineHeight, Component.translatable("tc.search"));
		searchField.setMaxLength(15);
		searchField.setTextColor(0xFFFFFF);
		searchField.setResponder(s -> updateSearch());
		clearWidgets();
		if (searching) {
			addWidget(searchField);
			setFocused(searchField);
			searchField.setFocused(true);
			updateSearch();
		}

		int limit = (int) Math.floor((screenY - 28) / 24.0f);
		addonShift = 0;
		int count = 0;
		tabs.clear();
		int tcCount = 0;
		int otherCount = 0;
		for (String key : ResearchCategories.researchCategories.keySet()) {
			ResearchCategory rc = ResearchCategories.getResearchCategory(key);
			int total = 0;
			int known = 0;
			for (ResearchEntry entry : rc.research.values()) {
				if (entry.hasMeta(EnumResearchMeta.AUTOUNLOCK)) continue;
				total++;
				if (knowledge().isResearchKnown(entry.getKey())) known++;
			}
			int completion = (int) (known / (float) total * 100.0f);
			if (rc.researchKey != null && !knowsStrict(rc.researchKey)) continue;
			if (TC_CATEGORIES.contains(key)) {
				tcCount++;
				tabs.add(new CategoryTab(rc, key, false, 1, 10 + tcCount * 24, completion));
				continue;
			}
			if (++count > limit + catScrollPos) continue;
			if (count - 1 < catScrollPos) continue;
			otherCount++;
			tabs.add(new CategoryTab(rc, key, true, width - 17, 10 + otherCount * 24, completion));
		}
		scrollButtons = count > limit || count < catScrollPos;
		if (scrollButtons) addonShift = (screenY - 28) % 24 / 2;
		catScrollMax = count - limit;

		if (selectedCategory == null || selectedCategory.isEmpty()) return;
		ResearchCategory selected = ResearchCategories.getResearchCategory(selectedCategory);
		if (selected == null) return;
		research.addAll(selected.research.values());
		boundsLeft = 99999;
		boundsTop = 99999;
		boundsRight = -99999;
		boundsBottom = -99999;
		for (ResearchEntry entry : research) {
			if (entry == null || !isVisible(entry)) continue;
			boundsLeft = Math.min(boundsLeft, entry.getDisplayColumn() * 24 - screenX + 48);
			boundsRight = Math.max(boundsRight, entry.getDisplayColumn() * 24 - 24);
			boundsTop = Math.min(boundsTop, entry.getDisplayRow() * 24 - screenY + 48);
			boundsBottom = Math.max(boundsBottom, entry.getDisplayRow() * 24 - 24);
		}
	}

	private boolean isVisible(ResearchEntry entry) {
		if (knowledge().isResearchKnown(entry.getKey())) return true;
		if (invisible.contains(entry.getKey()) || (entry.hasMeta(EnumResearchMeta.HIDDEN) && !canUnlockResearch(entry))) return false;
		if (entry.getParents() == null && entry.hasMeta(EnumResearchMeta.HIDDEN)) return false;
		if (entry.getParents() != null) {
			for (String parentKey : entry.getParents()) {
				ResearchEntry parent = ResearchCategories.getResearch(parentKey);
				if (parent != null && !isVisible(parent)) {
					invisible.add(parentKey);
					return false;
				}
			}
		}
		return true;
	}

	/** ResearchManager#doesPlayerHaveRequisites: every parent (stripped of ~ and @) known strictly. */
	boolean canUnlockResearch(ResearchEntry entry) {
		String[] parents = entry.getParentsStripped();
		if (parents == null) return true;
		for (String parent : parents) if (!knowsStrict(parent)) return false;
		return true;
	}

	@Override
	public void removed() {
		super.removed();
		lastX = guiMapX;
		lastY = guiMapY;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Search
	// ------------------------------------------------------------------------------------------------------------

	private void updateSearch() {
		searchResults.clear();
		invisible.clear();
		String s1 = searchField.getValue().toLowerCase();
		for (CategoryTab tab : tabs) {
			if (tab.key().toLowerCase().contains(s1)) {
				searchResults.add(new SearchResult(ResearchCategories.getCategoryName(tab.key()).getString(), tab.key(), null, true));
			}
		}
		List<ResourceLocation> dupCheck = new ArrayList<>();
		IPlayerKnowledge knowledge = knowledge();
		for (String pre : knowledge.getResearchList()) {
			ResearchEntry ri = ResearchCategories.getResearch(pre);
			if (ri == null) continue;
			String name = ri.getLocalizedName().getString();
			if (name.toLowerCase().contains(s1)) searchResults.add(new SearchResult(name, pre, null, false));
			int stage = knowledge.getResearchStage(pre);
			if (ri.getStages() == null) continue;
			int s2 = Math.min(ri.getStages().length - 1, stage + 1);
			if (s2 < 0) continue;
			ResearchStage st = ri.getStages()[s2];
			if (st == null || st.getRecipes() == null || minecraft == null || minecraft.level == null) continue;
			for (ResourceLocation rec : st.getRecipes()) {
				if (dupCheck.contains(rec)) continue;
				dupCheck.add(rec);
				List<RecipeHolder<?>> holders = ResearchRecipeCatalog.resolve(minecraft.level.getRecipeManager(), rec);
				if (holders.isEmpty()) continue;
				ItemStack out = holders.get(0).value().getResultItem(minecraft.level.registryAccess());
				if (!out.isEmpty() && out.getHoverName().getString().toLowerCase().contains(s1)) {
					searchResults.add(new SearchResult(out.getHoverName().getString(), pre, rec, false));
				}
			}
		}
		searchResults.sort(null);
	}

	private void startSearch() {
		selectedCategory = "";
		searching = true;
		updateResearch();
		searchField.setValue("");
		updateSearch();
	}

	private void selectCategory(String key) {
		searching = false;
		selectedCategory = key;
		updateResearch();
		tempMapX = guiMapX = (boundsLeft + boundsRight) / 2;
		tempMapY = guiMapY = (boundsBottom + boundsTop) / 2;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Ticking and input
	// ------------------------------------------------------------------------------------------------------------

	@Override
	protected void containerTick() {
		super.containerTick();
		if (page != null) {
			page.tick();
			return;
		}
		curMouseX = guiMapX;
		curMouseY = guiMapY;
		double dx = tempMapX - guiMapX;
		double dy = tempMapY - guiMapY;
		if (dx * dx + dy * dy < 4.0) {
			guiMapX += dx;
			guiMapY += dy;
		} else {
			guiMapX += dx * 0.85;
			guiMapY += dy * 0.85;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (page != null) return page.mouseClicked(mouseX, mouseY, button);
		int mx = (int) mouseX;
		int my = (int) mouseY;
		if (!searching && button == 0 && mx >= startX && mx < startX + screenX && my >= startY && my < startY + screenY) {
			dragging = true;
			dragMouseX = mouseX;
			dragMouseY = mouseY;
		}
		if (!searching && currentHighlight != null && !knowledge().isResearchKnown(currentHighlight.getKey()) && canUnlockResearch(currentHighlight)) {
			updateResearch();
			sendAction(ThaumonomiconMenu.ACTION_OPEN, currentHighlight.getKey());
			openPage(currentHighlight, null);
			return true;
		} else if (currentHighlight != null && knowledge().isResearchKnown(currentHighlight.getKey())) {
			knowledge().clearResearchFlag(currentHighlight.getKey(), IPlayerKnowledge.EnumResearchFlag.RESEARCH);
			knowledge().clearResearchFlag(currentHighlight.getKey(), IPlayerKnowledge.EnumResearchFlag.PAGE);
			sendAction(ThaumonomiconMenu.ACTION_OPEN, currentHighlight.getKey());
			openPage(currentHighlight, null);
			return true;
		} else if (searching) {
			int q = 0;
			for (SearchResult sr : searchResults) {
				if (mx > 22 && mx < 18 + screenX && my >= 32 + q * 10 && my < 40 + q * 10) {
					if (knowledge().isResearchKnown(sr.key()) && !sr.cat()) {
						openPage(ResearchCategories.getResearch(sr.key()), sr.recipe());
						return true;
					}
					if (sr.cat()) {
						selectCategory(sr.key());
						return true;
					}
				}
				q++;
				if (32 + (q + 1) * 10 > screenY) break;
			}
		}

		// the original's buttons: search, addon-tab scroll arrows, category tabs - vanilla buttons there, so each press
		// played the standard button click (restored 2026-09-29; the port had been silent)
		if (mx >= 1 && my >= height - 17 && mx < 17 && my < height - 1) {
			buttonClick();
			startSearch();
			return true;
		}
		if (scrollButtons) {
			if (mx >= width - 14 && mx < width - 4 && my >= 20 && my < 31) {
				buttonClick();
				if (catScrollPos > 0) {
					catScrollPos--;
					updateResearch();
				}
				return true;
			}
			if (mx >= width - 14 && mx < width - 4 && my >= screenY + 1 && my < screenY + 12) {
				buttonClick();
				if (catScrollPos < catScrollMax) {
					catScrollPos++;
					updateResearch();
				}
				return true;
			}
		}
		for (CategoryTab tab : tabs) {
			if (mx >= tab.x() && my >= tab.y() + addonShift && mx < tab.x() + 16 && my < tab.y() + 16 + addonShift) {
				buttonClick();
				if (!tab.key().equals(selectedCategory)) selectCategory(tab.key());
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (page != null) return true;
		if (dragging && button == 0 && !searching) {
			guiMapX -= (mouseX - dragMouseX) * screenZoom;
			guiMapY -= (mouseY - dragMouseY) * screenZoom;
			curMouseX = tempMapX = guiMapX;
			curMouseY = tempMapY = guiMapY;
			dragMouseX = mouseX;
			dragMouseY = mouseY;
			if (tempMapX < boundsLeft * (double) screenZoom) tempMapX = boundsLeft * (double) screenZoom;
			if (tempMapY < boundsTop * (double) screenZoom) tempMapY = boundsTop * (double) screenZoom;
			if (tempMapX >= boundsRight * (double) screenZoom) tempMapX = boundsRight * screenZoom - 1.0f;
			if (tempMapY >= boundsBottom * (double) screenZoom) tempMapY = boundsBottom * screenZoom - 1.0f;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (page != null) return page.mouseScrolled(scrollY);
		if (!searching) {
			if (scrollY < 0) screenZoom += 0.25f;
			else if (scrollY > 0) screenZoom -= 0.25f;
			screenZoom = Mth.clamp(screenZoom, 1.0f, 2.0f);
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (page != null) return page.keyPressed(keyCode, scanCode) || true;
		if (searching && searchField.keyPressed(keyCode, scanCode, modifiers)) return true;
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode) && !searching)) {
			onClose();
			return true;
		}
		return searching || super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (page == null && searching) return searchField.charTyped(codePoint, modifiers);
		return super.charTyped(codePoint, modifiers);
	}

	void sendAction(int action, String key) {
		int id = ThaumonomiconMenu.buttonId(action, key);
		if (id >= 0 && minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
	}

	void openPage(ResearchEntry entry, ResourceLocation recipe) {
		if (entry == null) return;
		page = new ResearchPageView(this, entry, recipe);
		page.init();
	}

	/** Back from the book to the map - the original rebuilt the browser here, so newly revealed nodes show at once. */
	void closePage() {
		page = null;
		invisible.clear();
		updateResearch();
	}

	/** Vanilla's button press sound, as the original's GuiButtons made. */
	private void buttonClick() {
		if (minecraft != null) {
			minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
		}
	}

	void playSound(SoundEvent sound, float volume, float pitch) {
		if (minecraft != null && minecraft.player != null) minecraft.player.playSound(sound, volume, pitch);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------------------------------------------------

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
	}

	@Override
	public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
		// drawDefaultBackground over a world
		graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
		graphics.flush();
		if (page != null) {
			page.render(graphics, mx, my, partialTick);
			return;
		}
		int locX = Mth.floor(curMouseX + (guiMapX - curMouseX) * partialTick);
		int locY = Mth.floor(curMouseY + (guiMapY - curMouseY) * partialTick);
		if (locX < boundsLeft * screenZoom) locX = (int) (boundsLeft * screenZoom);
		if (locY < boundsTop * screenZoom) locY = (int) (boundsTop * screenZoom);
		if (locX >= boundsRight * screenZoom) locX = (int) (boundsRight * screenZoom - 1.0f);
		if (locY >= boundsBottom * screenZoom) locY = (int) (boundsBottom * screenZoom - 1.0f);

		if (!searching) {
			graphics.pose().pushPose();
			graphics.pose().scale(1.0f / screenZoom, 1.0f / screenZoom, 1.0f);
			drawZoomable(graphics, mx, my, locX, locY);
			graphics.pose().popPose();
		} else {
			currentHighlight = null;
			searchField.render(graphics, mx, my, partialTick);
			int q = 0;
			for (SearchResult sr : searchResults) {
				int color = sr.cat() ? 14527146 : (sr.recipe() == null ? 14540253 : 11184861);
				if (sr.recipe() != null) {
					graphics.pose().pushPose();
					graphics.pose().scale(0.5f, 0.5f, 1f);
					graphics.blit(TX1, 44, (32 + q * 10) * 2, 224, 48, 16, 16);
					graphics.pose().popPose();
				}
				if (mx > 22 && mx < 18 + screenX && my >= 32 + q * 10 && my < 40 + q * 10) {
					color = sr.recipe() == null ? 16777215 : (sr.cat() ? 16764108 : 13421823);
				}
				graphics.drawString(font, sr.label(), 32, 32 + q * 10, color, false);
				q++;
				if (32 + (q + 1) * 10 > screenY) {
					graphics.drawString(font, Component.translatable("tc.search.more"), 22, 34 + q * 10, 11184810, false);
					break;
				}
			}
		}

		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 300);
		drawFrame(graphics);
		drawButtons(graphics, mx, my);
		graphics.pose().popPose();

		if (currentHighlight != null) {
			List<String> text = new ArrayList<>();
			text.add("§6" + currentHighlight.getLocalizedName().getString());
			IPlayerKnowledge knowledge = knowledge();
			if (canUnlockResearch(currentHighlight)) {
				if (!knowledge.isResearchComplete(currentHighlight.getKey()) && currentHighlight.getStages() != null) {
					int stage = knowledge.getResearchStage(currentHighlight.getKey());
					if (stage > 0) {
						text.add("@@" + ChatFormatting.AQUA + Component.translatable("tc.research.stage").getString() + " " + stage + "/"
								+ currentHighlight.getStages().length + ChatFormatting.RESET);
					} else {
						text.add("@@" + ChatFormatting.GREEN + Component.translatable("tc.research.begin").getString() + ChatFormatting.RESET);
					}
				}
			} else {
				text.add("@@§c" + Component.translatable("tc.researchmissing").getString());
				String[] parents = currentHighlight.getParents();
				if (parents != null) {
					String[] clean = currentHighlight.getParentsClean();
					for (int a = 0; a < parents.length; a++) {
						if (knowsStrict(parents[a])) continue;
						ResearchEntry parent = ResearchCategories.getResearch(clean[a]);
						text.add("@@§e - " + (parent != null ? parent.getLocalizedName().getString() : "?"));
					}
				}
			}
			if (knowledge.hasResearchFlag(currentHighlight.getKey(), IPlayerKnowledge.EnumResearchFlag.RESEARCH)) {
				text.add("@@" + Component.translatable("tc.research.newresearch").getString());
			}
			if (knowledge.hasResearchFlag(currentHighlight.getKey(), IPlayerKnowledge.EnumResearchFlag.PAGE)) {
				text.add("@@" + Component.translatable("tc.research.newpage").getString());
			}
			ResearchTooltip.draw(graphics, font, text, mx + 3, my - 3, -99, width, height);
		}
	}

	private void drawZoomable(GuiGraphics graphics, int mx, int my, int locX, int locY) {
		ResearchCategory cat = ResearchCategories.getResearchCategory(selectedCategory);
		if (cat == null) return;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		drawTiled(graphics, cat.background, (startX - 2) * screenZoom, (startY - 2) * screenZoom, locX / 2.0, locY / 2.0,
				(screenX + 4) * screenZoom, (screenY + 4) * screenZoom);
		if (cat.background2 != null) {
			drawTiled(graphics, cat.background2, (startX - 2) * screenZoom, (startY - 2) * screenZoom, locX / 1.5, locY / 1.5,
					(screenX + 4) * screenZoom, (screenY + 4) * screenZoom);
		}

		for (ResearchEntry source : research) {
			String[] parents = source.getParents();
			if (parents != null) {
				String[] clean = source.getParentsClean();
				for (int a = 0; a < parents.length; a++) {
					if (parents[a] == null) continue;
					ResearchEntry parent = ResearchCategories.getResearch(clean[a]);
					if (parent == null || !parent.getCategory().equals(selectedCategory)) continue;
					if (parent.getSiblings() != null && Arrays.asList(parent.getSiblings()).contains(source.getKey())) continue;
					boolean knowsParent = knowsStrict(parents[a]);
					if (!isVisible(source) || parents[a].startsWith("~")) continue;
					boolean reverse = source.hasMeta(EnumResearchMeta.REVERSE);
					if (knowsParent) {
						drawLine(graphics, source.getDisplayColumn(), source.getDisplayRow(), parent.getDisplayColumn(), parent.getDisplayRow(), 0.6f, 0.6f, 0.6f, locX, locY, true, reverse);
					} else if (isVisible(parent)) {
						drawLine(graphics, source.getDisplayColumn(), source.getDisplayRow(), parent.getDisplayColumn(), parent.getDisplayRow(), 0.2f, 0.2f, 0.2f, locX, locY, true, reverse);
					}
				}
			}
			String[] siblings = source.getSiblings();
			if (siblings != null) {
				for (String siblingKey : siblings) {
					if (siblingKey == null) continue;
					ResearchEntry sibling = ResearchCategories.getResearch(siblingKey);
					if (sibling == null || !sibling.getCategory().equals(selectedCategory)) continue;
					boolean knowsSibling = knowsStrict(sibling.getKey());
					if (!isVisible(source) || siblingKey.startsWith("~")) continue;
					boolean reverse = source.hasMeta(EnumResearchMeta.REVERSE);
					if (knowsSibling) {
						drawLine(graphics, sibling.getDisplayColumn(), sibling.getDisplayRow(), source.getDisplayColumn(), source.getDisplayRow(), 0.3f, 0.3f, 0.4f, locX, locY, false, reverse);
					} else if (isVisible(sibling)) {
						drawLine(graphics, sibling.getDisplayColumn(), sibling.getDisplayRow(), source.getDisplayColumn(), source.getDisplayRow(), 0.1875f, 0.1875f, 0.25f, locX, locY, false, reverse);
					}
				}
			}
		}

		currentHighlight = null;
		IPlayerKnowledge knowledge = knowledge();
		for (ResearchEntry entry : research) {
			boolean hasWarp = false;
			if (entry.getStages() != null) {
				for (ResearchStage stage : entry.getStages()) {
					if (stage.getWarp() > 0) {
						hasWarp = true;
						break;
					}
				}
			}
			int var25 = entry.getDisplayColumn() * 24 - locX;
			int var26 = entry.getDisplayRow() * 24 - locY;
			if (var25 < -24 || var26 < -24 || var25 > screenX * screenZoom || var26 > screenY * screenZoom) continue;
			int iconX = startX + var25;
			int iconY = startY + var26;
			if (!isVisible(entry)) continue;
			if (hasWarp) drawForbidden(graphics, iconX + 8, iconY + 8);
			boolean unlockable = canUnlockResearch(entry);
			float shade;
			if (knowledge.isResearchComplete(entry.getKey())) shade = 1.0f;
			else if (unlockable) shade = (float) Math.sin(System.currentTimeMillis() % 600L / 600.0 * Math.PI * 2.0) * 0.25f + 0.75f;
			else shade = 0.3f;
			RenderSystem.enableBlend();
			graphics.setColor(shade, shade, shade, 1.0f);
			int hiddenV = entry.hasMeta(EnumResearchMeta.HIDDEN) ? 32 : 0;
			if (entry.hasMeta(EnumResearchMeta.ROUND)) {
				graphics.blit(TX1, iconX - 8, iconY - 8, 144, 48 + hiddenV, 32, 32);
			} else {
				graphics.blit(TX1, iconX - 8, iconY - 8, entry.hasMeta(EnumResearchMeta.HEX) ? 112 : 80, 48 + hiddenV, 32, 32);
			}
			if (entry.hasMeta(EnumResearchMeta.SPIKY)) graphics.blit(TX1, iconX - 8, iconY - 8, 176, 48 + hiddenV, 32, 32);
			boolean bw = !unlockable;
			if (knowledge.hasResearchFlag(entry.getKey(), IPlayerKnowledge.EnumResearchFlag.RESEARCH)) {
				graphics.setColor(1f, 1f, 1f, 1f);
				graphics.pose().pushPose();
				graphics.pose().translate(iconX - 9, iconY - 9, 0);
				graphics.pose().scale(0.5f, 0.5f, 1f);
				graphics.blit(TX1, 0, 0, 176, 16, 32, 32);
				graphics.pose().popPose();
			}
			if (knowledge.hasResearchFlag(entry.getKey(), IPlayerKnowledge.EnumResearchFlag.PAGE)) {
				graphics.setColor(1f, 1f, 1f, 1f);
				graphics.pose().pushPose();
				graphics.pose().translate(iconX - 9, iconY + 9, 0);
				graphics.pose().scale(0.5f, 0.5f, 1f);
				graphics.blit(TX1, 0, 0, 208, 16, 32, 32);
				graphics.pose().popPose();
			}
			graphics.setColor(1f, 1f, 1f, 1f);
			drawResearchIcon(graphics, entry, iconX, iconY, bw);
			if (mx >= startX && my >= startY && mx < startX + screenX && my < startY + screenY
					&& mx >= (iconX - 2) / screenZoom && mx <= (iconX + 18) / screenZoom
					&& my >= (iconY - 2) / screenZoom && my <= (iconY + 18) / screenZoom) {
				currentHighlight = entry;
			}
			graphics.setColor(1f, 1f, 1f, 1f);
		}
	}

	/** drawTexturedModalRectWithDoubles: a quad whose UVs are in 1/256ths of the texture and run past its edge, so the
	 * (1024px) background repeats every 256 GUI pixels and scrolls with the map - needs the texture set to repeat. */
	private void drawTiled(GuiGraphics graphics, ResourceLocation texture, float x, float y, double u, double v, float w, float h) {
		if (minecraft == null) return;
		AbstractTexture tex = minecraft.getTextureManager().getTexture(texture);
		tex.bind();
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
		RenderSystem.setShaderTexture(0, tex.getId());
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		float f = 1f / 256f;
		Matrix4f m = graphics.pose().last().pose();
		BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		b.addVertex(m, x, y + h, 0).setUv((float) (u * f), (float) ((v + h) * f));
		b.addVertex(m, x + w, y + h, 0).setUv((float) ((u + w) * f), (float) ((v + h) * f));
		b.addVertex(m, x + w, y, 0).setUv((float) ((u + w) * f), (float) (v * f));
		b.addVertex(m, x, y, 0).setUv((float) (u * f), (float) (v * f));
		BufferUploader.drawWithShader(b.buildOrThrow());
	}

	private void drawLine(GuiGraphics graphics, int x, int y, int x2, int y2, float r, float g, float b, int locX, int locY, boolean arrow, boolean flipped) {
		boolean bigCorner;
		int xd, yd, xm, ym, xx, yy;
		if (flipped) {
			xd = Math.abs(x2 - x);
			yd = Math.abs(y2 - y);
			xm = xd == 0 ? 0 : (x2 - x > 0 ? -1 : 1);
			ym = yd == 0 ? 0 : (y2 - y > 0 ? -1 : 1);
			xx = x2 * 24 - 4 - locX + startX;
			yy = y2 * 24 - 4 - locY + startY;
		} else {
			xd = Math.abs(x - x2);
			yd = Math.abs(y - y2);
			xm = xd == 0 ? 0 : (x - x2 > 0 ? -1 : 1);
			ym = yd == 0 ? 0 : (y - y2 > 0 ? -1 : 1);
			xx = x * 24 - 4 - locX + startX;
			yy = y * 24 - 4 - locY + startY;
		}
		bigCorner = xd > 1 && yd > 1;
		RenderSystem.enableBlend();
		graphics.setColor(r, g, b, 1.0f);
		if (arrow) {
			if (flipped) {
				int xx2 = x * 24 - 8 - locX + startX;
				int yy2 = y * 24 - 8 - locY + startY;
				if (xm < 0) graphics.blit(TX1, xx2, yy2, 160, 112, 32, 32);
				else if (xm > 0) graphics.blit(TX1, xx2, yy2, 128, 112, 32, 32);
				else if (ym > 0) graphics.blit(TX1, xx2, yy2, 64, 112, 32, 32);
				else if (ym < 0) graphics.blit(TX1, xx2, yy2, 96, 112, 32, 32);
			} else if (ym < 0) {
				graphics.blit(TX1, xx - 4, yy - 4, 64, 112, 32, 32);
			} else if (ym > 0) {
				graphics.blit(TX1, xx - 4, yy - 4, 96, 112, 32, 32);
			} else if (xm > 0) {
				graphics.blit(TX1, xx - 4, yy - 4, 160, 112, 32, 32);
			} else if (xm < 0) {
				graphics.blit(TX1, xx - 4, yy - 4, 128, 112, 32, 32);
			}
		}
		int v = 1;
		int h = 0;
		while (v < yd - (bigCorner ? 1 : 0)) {
			graphics.blit(TX1, xx + xm * 24 * h, yy + ym * 24 * v, 0, 228, 24, 24);
			v++;
		}
		if (bigCorner) {
			if (xm < 0 && ym > 0) graphics.blit(TX1, xx + xm * 24 * h - 24, yy + ym * 24 * v, 0, 180, 48, 48);
			if (xm > 0 && ym > 0) graphics.blit(TX1, xx + xm * 24 * h, yy + ym * 24 * v, 48, 180, 48, 48);
			if (xm < 0 && ym < 0) graphics.blit(TX1, xx + xm * 24 * h - 24, yy + ym * 24 * v - 24, 96, 180, 48, 48);
			if (xm > 0 && ym < 0) graphics.blit(TX1, xx + xm * 24 * h, yy + ym * 24 * v - 24, 144, 180, 48, 48);
		} else {
			if (xm < 0 && ym > 0) graphics.blit(TX1, xx + xm * 24 * h, yy + ym * 24 * v, 48, 228, 24, 24);
			if (xm > 0 && ym > 0) graphics.blit(TX1, xx + xm * 24 * h, yy + ym * 24 * v, 72, 228, 24, 24);
			if (xm < 0 && ym < 0) graphics.blit(TX1, xx + xm * 24 * h, yy + ym * 24 * v, 96, 228, 24, 24);
			if (xm > 0 && ym < 0) graphics.blit(TX1, xx + xm * 24 * h, yy + ym * 24 * v, 120, 228, 24, 24);
		}
		v += bigCorner ? 1 : 0;
		for (h += bigCorner ? 2 : 1; h < xd; h++) {
			graphics.blit(TX1, xx + xm * 24 * h, yy + ym * 24 * v, 24, 228, 24, 24);
		}
		graphics.setColor(1f, 1f, 1f, 1f);
	}

	/** GuiResearchBrowser#drawForbidden: the slowly turning violet aura-node glow behind a node that costs warp. */
	static void drawForbidden(GuiGraphics graphics, double x, double y) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		int count = mc.player != null ? mc.player.tickCount : 0;
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		GuiQuadRenderer.renderQuadCenteredQuarterTurned(graphics, NODE_TEXTURE, 32, 160 + count % 32, 90.0f, 0.33f, 0.0f, 0.44f, 0.9f, true);
		graphics.setColor(1f, 1f, 1f, 1f);
		graphics.pose().popPose();
	}

	private static final Map<ResourceLocation, int[]> TEXTURE_SIZES = new HashMap<>();

	/** Pixel size of a research icon texture, read once from its PNG header (for the animated strip icons). */
	private static int[] textureSize(ResourceLocation loc) {
		return TEXTURE_SIZES.computeIfAbsent(loc, l -> {
			try (var in = net.minecraft.client.Minecraft.getInstance().getResourceManager().open(l)) {
				byte[] header = in.readNBytes(24);
				int w = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16) | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
				int h = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
				return new int[] {w, h};
			} catch (Exception e) {
				return new int[] {16, 16};
			}
		});
	}

	/** GuiResearchBrowser#drawResearchIcon: the entry's icon (cycling once a second if it has several) with its top-left
	 * at (x, y) - a texture (a vertical or horizontal frame strip animates at 150ms a frame), an item, or a focus part. */
	static void drawResearchIcon(GuiGraphics graphics, ResearchEntry entry, int x, int y, boolean bw) {
		Object[] icons = entry.getIcons();
		if (icons == null || icons.length == 0) return;
		Object icon = icons[(int) (System.currentTimeMillis() / 1000L % icons.length)];
		RenderSystem.enableBlend();
		if (icon instanceof ResourceLocation texture) {
			if (bw) graphics.setColor(0.2f, 0.2f, 0.2f, 1.0f);
			int[] size = textureSize(texture);
			int w = size[0];
			int h = size[1];
			if (h > w && h % w == 0) {
				int m = h / w;
				int frame = (int) (System.currentTimeMillis() / 150L % m);
				graphics.blit(texture, x, y, 16, 16, 0, frame * 16, 16, 16, 16, 16 * m);
			} else if (w > h && w % h == 0) {
				int m = w / h;
				int frame = (int) (System.currentTimeMillis() / 150L % m);
				graphics.blit(texture, x, y, 16, 16, frame * 16, 0, 16, 16, 16 * m, 16);
			} else {
				graphics.blit(texture, x, y, 0, 0, 16, 16, 16, 16);
			}
			graphics.setColor(1f, 1f, 1f, 1f);
		} else if (icon instanceof ItemStack stack) {
			graphics.renderItem(stack, x, y);
		} else if (icon instanceof String s && s.startsWith("focus")) {
			drawFocusPart(graphics, s.replace("focus:", "").trim(), x + 8, y + 8, 24.0f, bw);
		}
	}

	/** GuiFocalManipulator#drawPart as the book used it: the effect/medium backing disc in the element's colour, then its icon. */
	private static void drawFocusPart(GuiGraphics graphics, String key, int x, int y, float scale, boolean bw) {
		IFocusElement element = FocusEngine.getElement(key);
		ResourceLocation icon = FocusEngine.getElementIcon(key);
		if (element == null || icon == null) return;
		boolean root = element.getType() == IFocusElement.EnumUnitType.MOD || key.equals("thaumcraft.ROOT");
		if (root) scale *= 2.0f;
		int rgb = FocusEngine.getElementColor(key);
		float dim = bw ? 50f / 220f : 1f;
		float r = ((rgb >> 16) & 0xFF) / 255f * dim;
		float g = ((rgb >> 8) & 0xFF) / 255f * dim;
		float b = (rgb & 0xFF) / 255f * dim;
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().mulPose(Axis.ZN.rotationDegrees(90.0f));
		if (element.getType() == IFocusElement.EnumUnitType.EFFECT) {
			GuiQuadRenderer.renderQuadCenteredQuarterTurned(graphics, FOCUS_EFFECT, 1, 0, scale * 0.9f, r, g, b, 1f, false);
		} else if (element.getType() == IFocusElement.EnumUnitType.MEDIUM && !root) {
			GuiQuadRenderer.renderQuadCenteredQuarterTurned(graphics, FOCUS_MEDIUM, 1, 0, scale * 0.9f, r, g, b, 1f, false);
		}
		graphics.pose().translate(0, 0, 1);
		GuiQuadRenderer.renderQuadCenteredQuarterTurned(graphics, icon, 1, 0, scale / 2.0f, dim, dim, dim, 1f, false);
		graphics.pose().popPose();
	}

	private void drawFrame(GuiGraphics graphics) {
		RenderSystem.enableBlend();
		graphics.setColor(1f, 1f, 1f, 1f);
		for (int c = 16; c < width - 16; c += 64) {
			int p = 64;
			if (c + p > width - 16) p = width - 16 - c;
			if (p > 0) {
				graphics.blit(TX1, c, -2, 48, 13, p, 22);
				graphics.blit(TX1, c, height - 20, 48, 13, p, 22);
			}
		}
		for (int c = 16; c < height - 16; c += 64) {
			int p = 64;
			if (c + p > height - 16) p = height - 16 - c;
			if (p > 0) {
				graphics.blit(TX1, -2, c, 13, 48, 22, p);
				graphics.blit(TX1, width - 20, c, 13, 48, 22, p);
			}
		}
		graphics.blit(TX1, -2, -2, 13, 13, 22, 22);
		graphics.blit(TX1, -2, height - 20, 13, 13, 22, 22);
		graphics.blit(TX1, width - 20, -2, 13, 13, 22, 22);
		graphics.blit(TX1, width - 20, height - 20, 13, 13, 22, 22);
	}

	private void drawButtons(GuiGraphics graphics, int mx, int my) {
		IPlayerKnowledge knowledge = knowledge();
		for (CategoryTab tab : tabs) {
			int x = tab.x();
			int y = tab.y() + addonShift;
			boolean hovered = mx >= x && my >= y && mx < x + 16 && my < y + 16;
			boolean selected = tab.key().equals(selectedCategory);
			RenderSystem.enableBlend();
			graphics.setColor(selected ? 0.6f : 1.0f, 1.0f, 1.0f, 1.0f);
			graphics.blit(TX1, x - 3, y - 3, 13, 13, 22, 22);
			if (selected || hovered) graphics.setColor(1f, 1f, 1f, 1f);
			else graphics.setColor(0.66f, 0.66f, 0.66f, 0.8f);
			graphics.blit(tab.category().icon, x, y, 0, 0, 16, 16, 16, 16);
			graphics.setColor(1f, 1f, 1f, 1f);
			boolean nr = false;
			boolean np = false;
			for (String rk : tab.category().research.keySet()) {
				if (!knowledge.isResearchKnown(rk)) continue;
				if (!nr && knowledge.hasResearchFlag(rk, IPlayerKnowledge.EnumResearchFlag.RESEARCH)) nr = true;
				if (!np && knowledge.hasResearchFlag(rk, IPlayerKnowledge.EnumResearchFlag.PAGE)) np = true;
				if (nr && np) break;
			}
			if (nr) {
				graphics.setColor(1f, 1f, 1f, 0.7f);
				graphics.pose().pushPose();
				graphics.pose().translate(x - 2, y - 2, 0);
				graphics.pose().scale(0.25f, 0.25f, 1f);
				graphics.blit(TX1, 0, 0, 176, 16, 32, 32);
				graphics.pose().popPose();
			}
			if (np) {
				graphics.setColor(1f, 1f, 1f, 0.7f);
				graphics.pose().pushPose();
				graphics.pose().translate(x - 2, y + 9, 0);
				graphics.pose().scale(0.25f, 0.25f, 1f);
				graphics.blit(TX1, 0, 0, 208, 16, 32, 32);
				graphics.pose().popPose();
			}
			graphics.setColor(1f, 1f, 1f, 1f);
			if (hovered) {
				String dp = ResearchCategories.getCategoryName(tab.key()).getString() + " (" + tab.completion() + "%)";
				graphics.drawString(font, dp, tab.flip() ? screenX + 9 - font.width(dp) : x + 22, y + 4, 0xFFFFFF, true);
				int t = 9;
				if (nr) {
					String s = Component.translatable("tc.research.newresearch").getString();
					graphics.drawString(font, s, tab.flip() ? screenX + 9 - font.width(s) : x + 22, y + 4 + t, 0xFFFFFF, true);
					t += 9;
				}
				if (np) {
					String s = Component.translatable("tc.research.newpage").getString();
					graphics.drawString(font, s, tab.flip() ? screenX + 9 - font.width(s) : x + 22, y + 4 + t, 0xFFFFFF, true);
				}
			}
		}
		if (scrollButtons) {
			boolean up = mx >= width - 14 && mx < width - 4 && my >= 20 && my < 31;
			float c = up ? 1f : 0.7f;
			graphics.setColor(c, c, c, 1f);
			graphics.blit(TX1, width - 14, 20, 51, 55, 10, 11);
			boolean down = mx >= width - 14 && mx < width - 4 && my >= screenY + 1 && my < screenY + 12;
			c = down ? 1f : 0.7f;
			graphics.setColor(c, c, c, 1f);
			graphics.blit(TX1, width - 14, screenY + 1, 51, 71, 10, 11);
			graphics.setColor(1f, 1f, 1f, 1f);
		}
		boolean searchHover = mx >= 1 && my >= height - 17 && mx < 17 && my < height - 1;
		float c = searchHover ? 1f : 0.8f;
		RenderSystem.enableBlend();
		graphics.setColor(c, c, c, 1f);
		graphics.blit(TX1, 1, height - 17, 160, 16, 16, 16);
		graphics.setColor(1f, 1f, 1f, 1f);
		if (searchHover) graphics.drawString(font, Component.translatable("tc.search"), 20, height - 13, 0xFFFFFF, true);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

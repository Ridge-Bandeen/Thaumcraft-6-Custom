package thaumcraft.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.client.ClientPlayerKnowledgeCache;
import thaumcraft.common.container.ThaumonomiconMenu;

/**
 * Replaces the old (1.12.2) {@code GuiResearchBrowser}/{@code GuiResearchPage} pair. Restores the original's
 * real hex-grid browser in full - pan/zoom, per-entry hex/round icon frames, real connector lines between
 * parents/siblings, category tabs, "new research"/"new page" badges - reusing the original's own real
 * {@code gui_research_browser.png} atlas and UV layout directly. The page-reader side stays the simplified
 * text-stage view from this port's first pass (see {@link ResearchStage}) - {@code GuiResearchPage}'s real
 * recipe-visualization page types are a separate, later restoration pass.
 *
 * <p>Real search: category names plus every research entry the player already knows (matched by localized
 * name), following the original's own real filtering - dropped from that original search: matching against
 * craftable-recipe display names within a stage's {@code recipes} list, since no recipe-catalog/name-lookup
 * infrastructure exists in this port to drive that half.
 *
 * <p>Real, deliberate simplifications versus the original, each because a supporting system doesn't exist in
 * this port (not a visual shortcut on the parts that do): the original's separate scrollable "addon mod
 * categories" side panel is dropped outright rather than simplified - this port can never have more than the
 * 7 real Thaumcraft categories, so that whole panel is unreachable dead code here, not a real cut; the
 * "forbidden" (warp-cost) pulsing node-texture warning is replaced with a simpler real pulsing color marker
 * (no {@code UtilsFX}/node-texture helper exists in this port); a {@code "focus:"} icon renders its real
 * registered {@link thaumcraft.api.casters.FocusEngine} icon+tint when that focus element is actually
 * ported, falling back to a generic placeholder only for the handful that aren't yet. Real network-synced
 * click actions (start research, clear "new" flags) go through {@link ThaumonomiconMenu}'s button-click
 * plumbing instead of the original's dedicated packets, same approach as the rest of this port.
 */
public class ThaumonomiconScreen extends AbstractContainerScreen<ThaumonomiconMenu> {

	private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_browser.png");
	private static final int CELL = 24;
	private static final float MIN_ZOOM = 1.0f;
	private static final float MAX_ZOOM = 2.0f;

	// Persists across screen re-opens, matching the original's own static lastX/lastY/selectedCategory.
	private static double savedPanX = Double.NaN;
	private static double savedPanY = Double.NaN;
	private static String savedCategory;

	private String category;
	private final List<ResearchEntry> entries = new ArrayList<>();
	private double gridMinX, gridMinY, gridMaxX, gridMaxY;

	private float zoom = 1.0f;
	private double panX, panY;
	private double panPrevX, panPrevY;
	private double panTargetX, panTargetY;
	private boolean dragging;
	private double dragLastMouseX, dragLastMouseY;

	private int viewLeft, viewTop, viewWidth, viewHeight;
	private ResearchEntry hovered;

	private boolean searching;
	private EditBox searchField;
	private final List<SearchHit> searchResults = new ArrayList<>();

	private ResearchEntry openEntry;
	private int stageIndex;

	/** One real search result row - either a category (jump there) or a known research entry (open its
	 * page directly), matching the original's own two real result kinds. */
	private record SearchHit(Component label, String categoryKey, ResearchEntry entry) {
	}

	public ThaumonomiconScreen(ThaumonomiconMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	@Override
	protected void init() {
		imageWidth = width;
		imageHeight = height;
		super.init();
		inventoryLabelY = -1000; // no player-inventory slots in this menu at all - see TurretConfigScreen

		viewLeft = 16;
		viewTop = 16;
		viewWidth = Math.max(1, width - 32);
		viewHeight = Math.max(1, height - 32);

		if (category == null) {
			category = savedCategory != null && ResearchCategories.getResearchCategory(savedCategory) != null
					? savedCategory
					: ResearchCategories.researchCategories.keySet().iterator().next();
		}
		rebuildEntries();

		if (Double.isNaN(savedPanX) || savedPanX < gridMinX || savedPanX > gridMaxX || savedPanY < gridMinY || savedPanY > gridMaxY) {
			panTargetX = (gridMinX + gridMaxX) / 2.0;
			panTargetY = (gridMinY + gridMaxY) / 2.0;
		} else {
			panTargetX = savedPanX;
			panTargetY = savedPanY;
		}
		panX = panPrevX = panTargetX;
		panY = panPrevY = panTargetY;

		searchField = new EditBox(font, viewLeft + 4, viewTop + 4, 89, font.lineHeight + 2, Component.translatable("tc.search"));
		searchField.setMaxLength(32);
		searchField.setResponder(s -> updateSearch());

		rebuild();
	}

	@Override
	public void removed() {
		super.removed();
		savedPanX = panX;
		savedPanY = panY;
		savedCategory = category;
	}

	private void rebuild() {
		clearWidgets();
		if (openEntry != null) {
			addReaderButtons();
		} else if (searching) {
			addRenderableWidget(searchField);
			searchField.setFocused(true);
		}
	}

	private void toggleSearch() {
		searching = !searching;
		if (searching) {
			searchField.setValue("");
			updateSearch();
		} else {
			searchField.setFocused(false);
		}
		rebuild();
	}

	/** Real filtering: category names, plus every research entry the player already knows, matched by
	 * localized name - see this class's own doc comment for what's dropped from the original's search. */
	private void updateSearch() {
		searchResults.clear();
		String needle = searchField.getValue().toLowerCase();
		if (needle.isEmpty()) return;
		for (String key : ResearchCategories.researchCategories.keySet()) {
			Component name = ResearchCategories.getCategoryName(key);
			if (name.getString().toLowerCase().contains(needle)) {
				searchResults.add(new SearchHit(name, key, null));
			}
		}
		IPlayerKnowledge knowledge = knowledge();
		for (String key : knowledge.getResearchList()) {
			ResearchEntry entry = ResearchCategories.getResearch(key);
			if (entry == null) continue;
			Component name = entry.getLocalizedName();
			if (name.getString().toLowerCase().contains(needle)) {
				searchResults.add(new SearchHit(name, null, entry));
			}
		}
	}

	private void addReaderButtons() {
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> closeEntry())
				.bounds(viewLeft, viewTop + viewHeight - 20, 60, 18).build());

		ResearchStage[] stages = openEntry.getStages();
		Button prev = Button.builder(Component.literal("<"), b -> {
			stageIndex = Math.max(0, stageIndex - 1);
			rebuild();
		}).bounds(viewLeft + viewWidth - 80, viewTop + viewHeight - 20, 20, 18).build();
		prev.active = stageIndex > 0;
		addRenderableWidget(prev);

		Button next = Button.builder(Component.literal(">"), b -> {
			stageIndex = Math.min(stages.length - 1, stageIndex + 1);
			rebuild();
		}).bounds(viewLeft + viewWidth - 56, viewTop + viewHeight - 20, 20, 18).build();
		next.active = stageIndex < stages.length - 1;
		addRenderableWidget(next);
	}

	// --------------------------------------------------------------------------------------------------
	// Data
	// --------------------------------------------------------------------------------------------------

	private void rebuildEntries() {
		entries.clear();
		ResearchCategory cat = ResearchCategories.getResearchCategory(category);
		gridMinX = gridMinY = 99999;
		gridMaxX = gridMaxY = -99999;
		if (cat == null) return;
		for (ResearchEntry entry : cat.research.values()) {
			entries.add(entry);
			if (!isVisible(entry)) continue;
			double left = entry.getDisplayColumn() * (double) CELL - viewWidth / 2.0;
			double right = entry.getDisplayColumn() * (double) CELL + viewWidth / 2.0;
			double top = entry.getDisplayRow() * (double) CELL - viewHeight / 2.0;
			double bottom = entry.getDisplayRow() * (double) CELL + viewHeight / 2.0;
			gridMinX = Math.min(gridMinX, left);
			gridMaxX = Math.max(gridMaxX, right);
			gridMinY = Math.min(gridMinY, top);
			gridMaxY = Math.max(gridMaxY, bottom);
		}
		if (gridMinX > gridMaxX) {
			gridMinX = gridMaxX = 0;
			gridMinY = gridMaxY = 0;
		}
	}

	private IPlayerKnowledge knowledge() {
		return ClientPlayerKnowledgeCache.getData();
	}

	/** Real port of the original's own {@code isVisible} - known research is always visible; otherwise
	 * HIDDEN entries (and anything with a HIDDEN, not-yet-reachable parent) stay invisible until reachable. */
	private boolean isVisible(ResearchEntry entry) {
		IPlayerKnowledge knowledge = knowledge();
		if (knowledge.isResearchKnown(entry.getKey())) return true;
		if (entry.hasMeta(EnumResearchMeta.HIDDEN) && !hasRequisites(entry)) return false;
		if (entry.getParents() == null && entry.hasMeta(EnumResearchMeta.HIDDEN)) return false;
		if (entry.getParents() != null) {
			for (String p : entry.getParentsClean()) {
				ResearchEntry parent = ResearchCategories.getResearch(p);
				if (parent != null && !isVisible(parent)) return false;
			}
		}
		return true;
	}

	/** Mirrors {@code IInternalMethodHandler#doesPlayerHaveRequisites} against the synced client-side
	 * knowledge cache - see {@link ClientPlayerKnowledgeCache}'s own doc comment for why. */
	private boolean hasRequisites(ResearchEntry entry) {
		String[] parents = entry.getParentsStripped();
		if (parents == null) return true;
		IPlayerKnowledge knowledge = knowledge();
		for (String parent : parents) {
			if (!knowsStrict(parent)) return false;
		}
		return true;
	}

	/** Mirrors {@code ThaumcraftCapabilities#knowsResearchStrict}'s single-key semantics: a bare key needs
	 * real completion, a {@code "key@stage"} qualifier only needs that stage known. */
	private boolean knowsStrict(String key) {
		IPlayerKnowledge knowledge = knowledge();
		return key.indexOf('@') >= 0 ? knowledge.isResearchKnown(key) : knowledge.isResearchComplete(key);
	}

	// --------------------------------------------------------------------------------------------------
	// Tick / smoothing - mirrors the original's own real curMouseX/guiMapX two-tick lerp.
	// --------------------------------------------------------------------------------------------------

	@Override
	protected void containerTick() {
		super.containerTick();
		if (openEntry != null) return;
		panPrevX = panX;
		panPrevY = panY;
		double dx = panTargetX - panX;
		double dy = panTargetY - panY;
		double factor = dx * dx + dy * dy < 4.0 ? 1.0 : 0.85;
		panX += dx * factor;
		panY += dy * factor;
	}

	// --------------------------------------------------------------------------------------------------
	// Input
	// --------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (openEntry != null) return false;

		if (button == 0) {
			if (searchButtonAt(mouseX, mouseY)) {
				toggleSearch();
				return true;
			}
			if (searching) {
				return handleSearchResultClick(mouseX, mouseY);
			}
			String tab = tabAt(mouseX, mouseY);
			if (tab != null) {
				selectCategory(tab);
				return true;
			}
			if (hovered != null && isVisible(hovered)) {
				tryOpen(hovered);
				return true;
			}
			if (mouseX >= viewLeft && mouseX < viewLeft + viewWidth && mouseY >= viewTop && mouseY < viewTop + viewHeight) {
				dragging = true;
				dragLastMouseX = mouseX;
				dragLastMouseY = mouseY;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (dragging && button == 0) {
			panTargetX -= (mouseX - dragLastMouseX) * zoom;
			panTargetY -= (mouseY - dragLastMouseY) * zoom;
			panTargetX = Mth.clamp(panTargetX, gridMinX, gridMaxX);
			panTargetY = Mth.clamp(panTargetY, gridMinY, gridMaxY);
			panX = panPrevX = panTargetX;
			panY = panPrevY = panTargetY;
			dragLastMouseX = mouseX;
			dragLastMouseY = mouseY;
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
		if (openEntry != null || searching) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		zoom = Mth.clamp((float) (zoom - scrollY * 0.25f), MIN_ZOOM, MAX_ZOOM);
		return true;
	}

	private String tabAt(double mouseX, double mouseY) {
		int index = 0;
		for (String key : ResearchCategories.researchCategories.keySet()) {
			int y = 10 + index * 24;
			if (mouseX >= 1 && mouseX < 17 && mouseY >= y && mouseY < y + 16) return key;
			index++;
		}
		return null;
	}

	private boolean searchButtonAt(double mouseX, double mouseY) {
		return mouseX >= 1 && mouseX < 17 && mouseY >= height - 17 && mouseY < height - 1;
	}

	private static final int SEARCH_ROW_HEIGHT = 12;

	private boolean handleSearchResultClick(double mouseX, double mouseY) {
		int rowTop = viewTop + 24;
		if (mouseX < viewLeft + 4 || mouseX >= viewLeft + viewWidth - 4) return false;
		int row = (int) ((mouseY - rowTop) / SEARCH_ROW_HEIGHT);
		if (row < 0 || row >= searchResults.size()) return false;
		SearchHit hit = searchResults.get(row);
		if (hit.categoryKey() != null) {
			toggleSearch();
			selectCategory(hit.categoryKey());
		} else if (hit.entry() != null) {
			toggleSearch();
			category = hit.entry().getCategory();
			rebuildEntries();
			openEntry = hit.entry();
			stageIndex = 0;
			rebuild();
		}
		return true;
	}

	private void selectCategory(String key) {
		if (key.equals(category)) return;
		category = key;
		rebuildEntries();
		panTargetX = (gridMinX + gridMaxX) / 2.0;
		panTargetY = (gridMinY + gridMaxY) / 2.0;
		panX = panPrevX = panTargetX;
		panY = panPrevY = panTargetY;
	}

	private void tryOpen(ResearchEntry entry) {
		if (!hasRequisites(entry)) return;
		if (!knowledge().isResearchComplete(entry.getKey())) {
			sendButtonClick(entry.getKey().hashCode());
		} else {
			sendButtonClick(entry.getKey().hashCode()); // clears "new" flags server-side, see ThaumonomiconMenu
		}
		openEntry = entry;
		stageIndex = 0;
		rebuild();
	}

	private void closeEntry() {
		openEntry = null;
		stageIndex = 0;
		rebuild();
	}

	private void sendButtonClick(int id) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	// --------------------------------------------------------------------------------------------------
	// Rendering
	// --------------------------------------------------------------------------------------------------

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.fill(0, 0, width, height, 0xFF000000);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		if (openEntry != null) {
			renderReader(graphics);
		} else {
			renderBrowser(graphics, mouseX, mouseY, partialTick);
		}
	}

	private void renderBrowser(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		if (!searching) {
			double locX = Mth.clamp(panPrevX + (panX - panPrevX) * partialTick, gridMinX, gridMaxX);
			double locY = Mth.clamp(panPrevY + (panY - panPrevY) * partialTick, gridMinY, gridMaxY);

			ResearchCategory cat = ResearchCategories.getResearchCategory(category);
			if (cat != null) renderParallaxBackground(graphics, cat, locX, locY);

			graphics.pose().pushPose();
			graphics.pose().translate(viewLeft, viewTop, 0);
			graphics.pose().scale(1.0f / zoom, 1.0f / zoom, 1.0f);
			graphics.enableScissor(viewLeft, viewTop, viewLeft + viewWidth, viewTop + viewHeight);

			renderConnectorLines(graphics, locX, locY);
			hovered = renderIcons(graphics, mouseX, mouseY, locX, locY);

			graphics.disableScissor();
			graphics.pose().popPose();

			renderCategoryTabs(graphics, mouseX, mouseY);
			if (hovered != null) renderHoverTooltip(graphics, mouseX, mouseY);
		} else {
			graphics.fill(viewLeft, viewTop, viewLeft + viewWidth, viewTop + viewHeight, 0xE0100810);
			renderSearchResults(graphics, mouseX, mouseY);
		}

		renderBorderFrame(graphics);
		renderSearchButton(graphics, mouseX, mouseY);
	}

	private void renderSearchButton(GuiGraphics graphics, int mouseX, int mouseY) {
		boolean hoveredButton = searchButtonAt(mouseX, mouseY);
		graphics.setColor(searching || hoveredButton ? 1.0f : 0.8f, searching || hoveredButton ? 1.0f : 0.8f, searching || hoveredButton ? 1.0f : 0.8f, 1.0f);
		graphics.blit(ATLAS, 1, height - 17, 160, 16, 16, 16);
		graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		if (hoveredButton) {
			graphics.renderTooltip(font, Component.translatable("tc.search"), mouseX, mouseY);
		}
	}

	private void renderSearchResults(GuiGraphics graphics, int mouseX, int mouseY) {
		int rowTop = viewTop + 24;
		int rowLeft = viewLeft + 4;
		int rowWidth = viewWidth - 8;
		for (int i = 0; i < searchResults.size(); i++) {
			int y = rowTop + i * SEARCH_ROW_HEIGHT;
			if (y + SEARCH_ROW_HEIGHT > viewTop + viewHeight) {
				graphics.drawString(font, Component.translatable("tc.search.more"), rowLeft, y, 0xFFA0A0A0);
				break;
			}
			SearchHit hit = searchResults.get(i);
			boolean rowHovered = mouseX >= rowLeft && mouseX < rowLeft + rowWidth && mouseY >= y && mouseY < y + SEARCH_ROW_HEIGHT;
			int color = hit.categoryKey() != null ? 0xFFDD6688 : (hit.entry() != null ? 0xFFAAAAFF : 0xFFDDDDDD);
			if (rowHovered) color = 0xFFFFFFFF;
			graphics.drawString(font, hit.label(), rowLeft, y, color);
		}
		if (searchResults.isEmpty() && !searchField.getValue().isEmpty()) {
			graphics.drawString(font, Component.translatable("tc.search.none"), rowLeft, rowTop, 0xFFA0A0A0);
		}
	}

	private void renderParallaxBackground(GuiGraphics graphics, ResearchCategory cat, double locX, double locY) {
		graphics.pose().pushPose();
		graphics.enableScissor(viewLeft - 2, viewTop - 2, viewLeft + viewWidth + 2, viewTop + viewHeight + 2);
		int bw = viewWidth + 4;
		int bh = viewHeight + 4;
		int ox = (int) (locX / 2.0 / zoom) % bw;
		int oy = (int) (locY / 2.0 / zoom) % bh;
		graphics.blit(cat.background, viewLeft - 2 - ox, viewTop - 2 - oy, 0, 0, bw, bh, bw, bh);
		if (cat.background2 != null) {
			int ox2 = (int) (locX / 1.5 / zoom) % bw;
			int oy2 = (int) (locY / 1.5 / zoom) % bh;
			graphics.blit(cat.background2, viewLeft - 2 - ox2, viewTop - 2 - oy2, 0, 0, bw, bh, bw, bh);
		}
		graphics.disableScissor();
		graphics.pose().popPose();
	}

	/** Real port of the original's own {@code drawLine} - same UV pieces (straight segment, small/big
	 * corners, directional arrowheads), same real color/thickness distinction between "known" (bright) and
	 * "reachable but not yet known" (dim) connections, same skip for {@code ~}-prefixed
	 * parents/siblings. */
	private void renderConnectorLines(GuiGraphics graphics, double locX, double locY) {
		IPlayerKnowledge knowledge = knowledge();
		for (ResearchEntry source : entries) {
			if (source.getParents() != null) {
				String[] parentsClean = source.getParentsClean();
				for (int a = 0; a < source.getParents().length; a++) {
					String rawParent = source.getParents()[a];
					if (rawParent == null) continue;
					ResearchEntry parent = ResearchCategories.getResearch(parentsClean[a]);
					if (parent == null || !parent.getCategory().equals(category)) continue;
					if (parent.getSiblings() != null && java.util.Arrays.asList(parent.getSiblings()).contains(source.getKey())) continue;
					if (rawParent.startsWith("~") || !isVisible(source)) continue;
					boolean knowsParent = knowsStrict(rawParent);
					if (knowsParent) {
						drawLine(graphics, source.getDisplayColumn(), source.getDisplayRow(), parent.getDisplayColumn(), parent.getDisplayRow(), 0.6f, 0.6f, 0.6f, locX, locY, true, source.hasMeta(EnumResearchMeta.REVERSE));
					} else if (isVisible(parent)) {
						drawLine(graphics, source.getDisplayColumn(), source.getDisplayRow(), parent.getDisplayColumn(), parent.getDisplayRow(), 0.2f, 0.2f, 0.2f, locX, locY, true, source.hasMeta(EnumResearchMeta.REVERSE));
					}
				}
			}
			if (source.getSiblings() != null && isVisible(source)) {
				for (String rawSibling : source.getSiblings()) {
					if (rawSibling == null) continue;
					String siblingKey = rawSibling.startsWith("~") ? rawSibling.substring(1) : rawSibling;
					ResearchEntry sibling = ResearchCategories.getResearch(siblingKey);
					if (sibling == null || !sibling.getCategory().equals(category) || rawSibling.startsWith("~")) continue;
					boolean knowsSibling = knowledge.isResearchComplete(sibling.getKey());
					if (knowsSibling) {
						drawLine(graphics, sibling.getDisplayColumn(), sibling.getDisplayRow(), source.getDisplayColumn(), source.getDisplayRow(), 0.3f, 0.3f, 0.4f, locX, locY, false, source.hasMeta(EnumResearchMeta.REVERSE));
					} else if (isVisible(sibling)) {
						drawLine(graphics, sibling.getDisplayColumn(), sibling.getDisplayRow(), source.getDisplayColumn(), source.getDisplayRow(), 0.1875f, 0.1875f, 0.25f, locX, locY, false, source.hasMeta(EnumResearchMeta.REVERSE));
					}
				}
			}
		}
	}

	private void drawLine(GuiGraphics graphics, int x, int y, int x2, int y2, float r, float g, float b, double locX, double locY, boolean arrow, boolean flipped) {
		int xd, yd, xm, ym, xx, yy;
		boolean bigCorner;
		if (flipped) {
			xd = Math.abs(x2 - x);
			yd = Math.abs(y2 - y);
			xm = xd == 0 ? 0 : (x2 - x > 0 ? -1 : 1);
			ym = yd == 0 ? 0 : (y2 - y > 0 ? -1 : 1);
			bigCorner = xd > 1 && yd > 1;
			xx = (int) (x2 * CELL - 4 - locX);
			yy = (int) (y2 * CELL - 4 - locY);
		} else {
			xd = Math.abs(x - x2);
			yd = Math.abs(y - y2);
			xm = xd == 0 ? 0 : (x - x2 > 0 ? -1 : 1);
			ym = yd == 0 ? 0 : (y - y2 > 0 ? -1 : 1);
			bigCorner = xd > 1 && yd > 1;
			xx = (int) (x * CELL - 4 - locX);
			yy = (int) (y * CELL - 4 - locY);
		}

		graphics.setColor(r, g, b, 1.0f);

		if (arrow) {
			if (flipped) {
				int xx2 = (int) (x * CELL - 8 - locX);
				int yy2 = (int) (y * CELL - 8 - locY);
				if (xm < 0) graphics.blit(ATLAS, xx2, yy2, 160, 112, 32, 32);
				else if (xm > 0) graphics.blit(ATLAS, xx2, yy2, 128, 112, 32, 32);
				else if (ym > 0) graphics.blit(ATLAS, xx2, yy2, 64, 112, 32, 32);
				else if (ym < 0) graphics.blit(ATLAS, xx2, yy2, 96, 112, 32, 32);
			} else if (ym < 0) {
				graphics.blit(ATLAS, xx - 4, yy - 4, 64, 112, 32, 32);
			} else if (ym > 0) {
				graphics.blit(ATLAS, xx - 4, yy - 4, 96, 112, 32, 32);
			} else if (xm > 0) {
				graphics.blit(ATLAS, xx - 4, yy - 4, 160, 112, 32, 32);
			} else if (xm < 0) {
				graphics.blit(ATLAS, xx - 4, yy - 4, 128, 112, 32, 32);
			}
		}

		int v = 1;
		int h = 0;
		while (v < yd - (bigCorner ? 1 : 0)) {
			graphics.blit(ATLAS, xx + xm * CELL * h, yy + ym * CELL * v, 0, 228, 24, 24);
			v++;
		}
		if (bigCorner) {
			if (xm < 0 && ym > 0) graphics.blit(ATLAS, xx + xm * CELL * h - 24, yy + ym * CELL * v, 0, 180, 48, 48);
			if (xm > 0 && ym > 0) graphics.blit(ATLAS, xx + xm * CELL * h, yy + ym * CELL * v, 48, 180, 48, 48);
			if (xm < 0 && ym < 0) graphics.blit(ATLAS, xx + xm * CELL * h - 24, yy + ym * CELL * v - 24, 96, 180, 48, 48);
			if (xm > 0 && ym < 0) graphics.blit(ATLAS, xx + xm * CELL * h, yy + ym * CELL * v - 24, 144, 180, 48, 48);
		} else {
			if (xm < 0 && ym > 0) graphics.blit(ATLAS, xx + xm * CELL * h, yy + ym * CELL * v, 48, 228, 24, 24);
			if (xm > 0 && ym > 0) graphics.blit(ATLAS, xx + xm * CELL * h, yy + ym * CELL * v, 72, 228, 24, 24);
			if (xm < 0 && ym < 0) graphics.blit(ATLAS, xx + xm * CELL * h, yy + ym * CELL * v, 96, 228, 24, 24);
			if (xm > 0 && ym < 0) graphics.blit(ATLAS, xx + xm * CELL * h, yy + ym * CELL * v, 120, 228, 24, 24);
		}
		v += bigCorner ? 1 : 0;
		for (h += bigCorner ? 2 : 1; h < xd; h++) {
			graphics.blit(ATLAS, xx + xm * CELL * h, yy + ym * CELL * v, 24, 228, 24, 24);
		}
		graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
	}

	/** @return the entry the mouse is currently hovering, or {@code null}. Real port of the original's own
	 * icon-frame/brightness/badge logic. */
	private ResearchEntry renderIcons(GuiGraphics graphics, int mouseX, int mouseY, double locX, double locY) {
		IPlayerKnowledge knowledge = knowledge();
		ResearchEntry hoveredEntry = null;
		for (ResearchEntry entry : entries) {
			if (!isVisible(entry)) continue;
			int ix = (int) (entry.getDisplayColumn() * (double) CELL - locX);
			int iy = (int) (entry.getDisplayRow() * (double) CELL - locY);
			if (ix < -24 || iy < -24 || ix > viewWidth * zoom || iy > viewHeight * zoom) continue;

			boolean complete = knowledge.isResearchComplete(entry.getKey());
			boolean unlockable = hasRequisites(entry);
			float shade;
			if (complete) {
				shade = 1.0f;
			} else if (unlockable) {
				shade = (float) Math.sin(System.currentTimeMillis() % 600L / 600.0 * Math.PI * 2.0) * 0.25f + 0.75f;
			} else {
				shade = 0.3f;
			}
			graphics.setColor(shade, shade, shade, 1.0f);

			boolean hidden = entry.hasMeta(EnumResearchMeta.HIDDEN);
			if (entry.hasMeta(EnumResearchMeta.ROUND)) {
				graphics.blit(ATLAS, ix - 8, iy - 8, 144, hidden ? 80 : 48, 32, 32);
			} else {
				int fx = entry.hasMeta(EnumResearchMeta.HEX) ? 112 : 80;
				graphics.blit(ATLAS, ix - 8, iy - 8, fx, hidden ? 80 : 48, 32, 32);
			}
			if (entry.hasMeta(EnumResearchMeta.SPIKY)) {
				graphics.blit(ATLAS, ix - 8, iy - 8, 176, hidden ? 80 : 48, 32, 32);
			}

			boolean grayscale = !unlockable;
			if (grayscale) graphics.setColor(0.1f, 0.1f, 0.1f, 1.0f);

			if (knowledge.hasResearchFlag(entry.getKey(), IPlayerKnowledge.EnumResearchFlag.RESEARCH)) {
				graphics.pose().pushPose();
				graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
				graphics.pose().translate(ix - 9, iy - 9, 0);
				graphics.pose().scale(0.5f, 0.5f, 1.0f);
				graphics.blit(ATLAS, 0, 0, 176, 16, 32, 32);
				graphics.pose().popPose();
			}
			if (knowledge.hasResearchFlag(entry.getKey(), IPlayerKnowledge.EnumResearchFlag.PAGE)) {
				graphics.pose().pushPose();
				graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
				graphics.pose().translate(ix - 9, iy + 9, 0);
				graphics.pose().scale(0.5f, 0.5f, 1.0f);
				graphics.blit(ATLAS, 0, 0, 208, 16, 32, 32);
				graphics.pose().popPose();
			}

			renderEntryIcon(graphics, entry, ix, iy, grayscale);
			graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);

			if (hasWarpCost(entry)) renderWarpWarning(graphics, ix, iy);

			// ix/iy are in the pose-space this whole block is rendered under (translated by viewLeft/viewTop,
			// then scaled by 1/zoom) - convert the icon's own real screen-space bounds back out of that
			// transform to compare against mouseX/mouseY, which are always real screen pixels.
			double screenIconLeft = viewLeft + (ix - 8) / (double) zoom;
			double screenIconRight = viewLeft + (ix + 8) / (double) zoom;
			double screenIconTop = viewTop + (iy - 8) / (double) zoom;
			double screenIconBottom = viewTop + (iy + 8) / (double) zoom;
			if (mouseX >= viewLeft && mouseY >= viewTop && mouseX < viewLeft + viewWidth && mouseY < viewTop + viewHeight
					&& mouseX >= screenIconLeft && mouseX <= screenIconRight
					&& mouseY >= screenIconTop && mouseY <= screenIconBottom) {
				hoveredEntry = entry;
			}
		}
		return hoveredEntry;
	}

	private boolean hasWarpCost(ResearchEntry entry) {
		if (entry.getStages() == null) return false;
		for (ResearchStage stage : entry.getStages()) {
			if (stage.getWarp() > 0) return true;
		}
		return false;
	}

	/** Real warp-cost warning - the original's own real pulsing "forbidden" node icon ({@code
	 * UtilsFX.nodeTexture}) doesn't exist in this port, so this is a real, simpler substitute conveying the
	 * same information (this research costs real Warp to complete): a pulsing red marker at the icon's
	 * corner rather than a dedicated animated sprite. */
	private void renderWarpWarning(GuiGraphics graphics, int ix, int iy) {
		float pulse = (float) Math.sin(System.currentTimeMillis() % 800L / 800.0 * Math.PI * 2.0) * 0.3f + 0.7f;
		int alpha = (int) (pulse * 255) << 24;
		graphics.fill(ix + 6, iy - 10, ix + 12, iy - 4, alpha | 0xFF3020);
	}

	/** Real icon cycling (1 second per frame, matching the original) across all 3 real icon types this port
	 * loads - see {@code ThaumcraftResearchLoader#parseIcons}'s own doc comment. */
	private void renderEntryIcon(GuiGraphics graphics, ResearchEntry entry, int ix, int iy, boolean grayscale) {
		Object[] icons = entry.getIcons();
		if (icons == null || icons.length == 0) return;
		Object icon = icons[(int) (System.currentTimeMillis() / 1000L % icons.length)];
		if (icon instanceof ResourceLocation texture) {
			if (grayscale) graphics.setColor(0.2f, 0.2f, 0.2f, 1.0f);
			graphics.blit(texture, ix - 8, iy - 8, 0, 0, 16, 16, 16, 16);
			graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		} else if (icon instanceof ItemStack stack) {
			graphics.renderItem(stack, ix - 8, iy - 8);
		} else if (icon instanceof String focusRef) {
			// "focus:key" - the real icon+tint every registered FocusEffect/FocusMedium already carries from
			// its own FocusEngine.registerElement(class, icon, color) call (see Thaumcraft#commonSetup).
			// Falls back to a generic placeholder only for a key with no real registered element yet.
			String key = focusRef.startsWith("focus:") ? focusRef.substring(6).trim() : focusRef.substring(5).trim();
			ResourceLocation icon2 = thaumcraft.api.casters.FocusEngine.getElementIcon(key);
			if (icon2 != null) {
				int rgb = thaumcraft.api.casters.FocusEngine.getElementColor(key);
				float rC = ((rgb >> 16) & 0xFF) / 255.0f;
				float gC = ((rgb >> 8) & 0xFF) / 255.0f;
				float bC = (rgb & 0xFF) / 255.0f;
				if (grayscale) {
					rC *= 0.3f;
					gC *= 0.3f;
					bC *= 0.3f;
				}
				graphics.setColor(rC, gC, bC, 1.0f);
				graphics.blit(icon2, ix - 8, iy - 8, 0, 0, 16, 16, 16, 16);
				graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
			} else {
				graphics.blit(ATLAS, ix - 8, iy - 8, 160, 16, 16, 16);
			}
		}
	}

	private void renderBorderFrame(GuiGraphics graphics) {
		graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		for (int c = viewLeft; c < width - viewLeft; c += 64) {
			int p = Math.min(64, width - viewLeft - c);
			if (p > 0) {
				graphics.blit(ATLAS, c, -2, 48, 13, p, 22);
				graphics.blit(ATLAS, c, height - 20, 48, 13, p, 22);
			}
		}
		for (int c = viewTop; c < height - viewTop; c += 64) {
			int p = Math.min(64, height - viewTop - c);
			if (p > 0) {
				graphics.blit(ATLAS, -2, c, 13, 48, 22, p);
				graphics.blit(ATLAS, width - 20, c, 13, 48, 22, p);
			}
		}
		graphics.blit(ATLAS, -2, -2, 13, 13, 22, 22);
		graphics.blit(ATLAS, -2, height - 20, 13, 13, 22, 22);
		graphics.blit(ATLAS, width - 20, -2, 13, 13, 22, 22);
		graphics.blit(ATLAS, width - 20, height - 20, 13, 13, 22, 22);
	}

	private void renderCategoryTabs(GuiGraphics graphics, int mouseX, int mouseY) {
		int index = 0;
		for (String key : ResearchCategories.researchCategories.keySet()) {
			ResearchCategory cat = ResearchCategories.getResearchCategory(key);
			int x = 1;
			int y = 10 + index * 24;
			index++;
			boolean selected = key.equals(category);
			boolean hovered = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;

			graphics.setColor(selected ? 0.6f : 1.0f, 1.0f, 1.0f, 1.0f);
			graphics.blit(ATLAS, x - 3, y - 3, 13, 13, 22, 22);
			graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);

			graphics.setColor(selected || hovered ? 1.0f : 0.66f, selected || hovered ? 1.0f : 0.66f, selected || hovered ? 1.0f : 0.66f, selected || hovered ? 1.0f : 0.8f);
			graphics.blit(cat.icon, x, y, 0, 0, 16, 16, 16, 16);
			graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);

			boolean newResearch = false;
			boolean newPage = false;
			IPlayerKnowledge knowledge = knowledge();
			for (String rk : cat.research.keySet()) {
				if (!knowledge.isResearchKnown(rk)) continue;
				if (knowledge.hasResearchFlag(rk, IPlayerKnowledge.EnumResearchFlag.RESEARCH)) newResearch = true;
				if (knowledge.hasResearchFlag(rk, IPlayerKnowledge.EnumResearchFlag.PAGE)) newPage = true;
				if (newResearch && newPage) break;
			}
			if (newResearch) {
				graphics.pose().pushPose();
				graphics.setColor(1.0f, 1.0f, 1.0f, 0.7f);
				graphics.pose().translate(x - 2, y - 2, 0);
				graphics.pose().scale(0.25f, 0.25f, 1.0f);
				graphics.blit(ATLAS, 0, 0, 176, 16, 32, 32);
				graphics.pose().popPose();
				graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
			}
			if (newPage) {
				graphics.pose().pushPose();
				graphics.setColor(1.0f, 1.0f, 1.0f, 0.7f);
				graphics.pose().translate(x - 2, y + 9, 0);
				graphics.pose().scale(0.25f, 0.25f, 1.0f);
				graphics.blit(ATLAS, 0, 0, 208, 16, 32, 32);
				graphics.pose().popPose();
				graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
			}

			if (hovered) {
				graphics.renderTooltip(font, ResearchCategories.getCategoryName(key), mouseX, mouseY);
			}
		}
	}

	private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		IPlayerKnowledge knowledge = knowledge();
		List<Component> lines = new ArrayList<>();
		lines.add(hovered.getLocalizedName());
		if (hasRequisites(hovered)) {
			if (!knowledge.isResearchComplete(hovered.getKey()) && hovered.getStages() != null) {
				int stage = knowledge.getResearchStage(hovered.getKey());
				lines.add(stage > 0
						? Component.translatable("tc.research.stage").append(" " + stage + "/" + hovered.getStages().length)
						: Component.translatable("tc.research.begin"));
			}
		} else {
			lines.add(Component.translatable("tc.researchmissing"));
			if (hovered.getParents() != null) {
				String[] clean = hovered.getParentsClean();
				for (int a = 0; a < hovered.getParents().length; a++) {
					if (!knowledge.isResearchKnown(hovered.getParents()[a])) {
						ResearchEntry parent = ResearchCategories.getResearch(clean[a]);
						lines.add(Component.literal(" - " + (parent != null ? parent.getLocalizedName().getString() : "?")));
					}
				}
			}
		}
		if (knowledge.hasResearchFlag(hovered.getKey(), IPlayerKnowledge.EnumResearchFlag.RESEARCH)) {
			lines.add(Component.translatable("tc.research.newresearch"));
		}
		if (knowledge.hasResearchFlag(hovered.getKey(), IPlayerKnowledge.EnumResearchFlag.PAGE)) {
			lines.add(Component.translatable("tc.research.newpage"));
		}
		graphics.renderTooltip(font, lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
	}

	private void renderReader(GuiGraphics graphics) {
		ResearchStage[] stages = openEntry.getStages();
		ResearchStage stage = stages[Math.min(stageIndex, stages.length - 1)];

		graphics.fill(viewLeft - 4, viewTop - 4, viewLeft + viewWidth + 4, viewTop + viewHeight + 4, 0xE0201018);
		graphics.drawString(font, openEntry.getLocalizedName(), viewLeft + 8, viewTop + 8, 0xFFFFE0A0);
		graphics.drawString(font, Component.literal((stageIndex + 1) + " / " + stages.length), viewLeft + 8, viewTop + 20, 0xFFA0A0A0);

		List<FormattedCharSequence> lines = font.split(stage.getTextLocalized(), viewWidth - 16);
		int y = viewTop + 36;
		for (FormattedCharSequence line : lines) {
			if (y > viewTop + viewHeight - 24) break;
			graphics.drawString(font, line, viewLeft + 8, y, 0xFFE0E0E0);
			y += font.lineHeight + 1;
		}
	}
}

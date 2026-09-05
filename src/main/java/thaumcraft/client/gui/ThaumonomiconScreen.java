package thaumcraft.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.client.ClientPlayerKnowledgeCache;
import thaumcraft.common.container.ThaumonomiconMenu;

/**
 * Replaces the old (1.12.2) {@code GuiResearchBrowser}/{@code GuiResearchPage} pair - a real, deliberately
 * simplified Thaumonomicon, same "lighter testing, real simplification" treatment {@code GuiResearchTable}'s
 * own doc comment already established for exactly this situation (client-only rendering/input this session's
 * GameTest discipline can't reach). Three real views instead of the original's interactive hex-grid tree:
 * <ol>
 * <li>a top row of category tabs (only ones the player has unlocked - see {@code researchKey});</li>
 * <li>a scrollable plain list of that category's entries, sorted reading-order (row then column) instead of
 * laid out spatially on a hex grid;</li>
 * <li>a page reader, one real {@link ResearchStage} per page, Prev/Next between stages.</li>
 * </ol>
 * Real, deliberate cuts versus the original for this pass: no hex-grid layout/line-drawing/pan-zoom, no
 * search, no {@code obtain}/{@code craft} item-icon rendering, no recipe-visualization page types (crafting/
 * arcane/crucible/infusion grids), no addenda pages, no category/entry icons (plain text, matching {@code
 * GuiResearchTable}'s own "plain text card names rather than a graphical card layout" precedent).
 *
 * <p>Clicking an unlocked-but-unknown entry both starts <i>and completes</i> it in one action (via {@link
 * ThaumonomiconMenu}'s button click, which calls the real {@code IInternalMethodHandler.completeResearch}) -
 * see that menu's own doc comment for why per-stage {@code required_item}/{@code required_craft} gating isn't
 * enforced yet, which is what makes one-shot completion a faithful v1 behavior rather than a cheat: nothing
 * in this port currently drip-feeds those stages open anyway.
 *
 * <p>Reads {@link ClientPlayerKnowledgeCache} (synced by {@link ThaumonomiconMenu}) rather than the local
 * player's own {@code IPlayerKnowledge} attachment directly - see that cache's own doc comment for why.
 */
public class ThaumonomiconScreen extends AbstractContainerScreen<ThaumonomiconMenu> {

	private static final int ROW_HEIGHT = 14;
	private static final int VISIBLE_ROWS = 11;

	private String category = "BASICS";
	private ResearchEntry openEntry;
	private int stageIndex;
	private int scrollOffset;

	private int listTop;
	private int listLeft;
	private int listWidth;

	public ThaumonomiconScreen(ThaumonomiconMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 320;
		imageHeight = 236;
		inventoryLabelY = -1000; // no player-inventory slots in this menu at all - see TurretConfigScreen
	}

	@Override
	protected void init() {
		super.init();
		listLeft = leftPos + 8;
		listWidth = imageWidth - 16;
		listTop = topPos + 30;
		rebuild();
	}

	private void rebuild() {
		clearWidgets();
		addTabs();
		if (openEntry != null) {
			addReaderButtons();
		}
	}

	private void addTabs() {
		IPlayerKnowledge knowledge = ClientPlayerKnowledgeCache.getData();
		List<ResearchCategory> visible = new ArrayList<>();
		for (ResearchCategory cat : ResearchCategories.researchCategories.values()) {
			if (cat.researchKey == null || knowledge.isResearchKnown(cat.researchKey)) visible.add(cat);
		}
		int tabWidth = Math.max(40, (imageWidth - 12) / Math.max(1, visible.size()));
		int x = leftPos + 6;
		for (ResearchCategory cat : visible) {
			String key = cat.key;
			Button b = Button.builder(ResearchCategories.getCategoryName(key), btn -> selectCategory(key))
					.bounds(x, topPos + 6, tabWidth - 2, 16).build();
			b.active = !key.equals(category) || openEntry != null;
			addRenderableWidget(b);
			x += tabWidth;
		}
	}

	private void addReaderButtons() {
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> closeEntry())
				.bounds(leftPos + 8, topPos + imageHeight - 26, 60, 18).build());

		ResearchStage[] stages = openEntry.getStages();
		Button prev = Button.builder(Component.literal("<"), b -> {
			stageIndex = Math.max(0, stageIndex - 1);
			rebuild();
		}).bounds(leftPos + imageWidth - 80, topPos + imageHeight - 26, 20, 18).build();
		prev.active = stageIndex > 0;
		addRenderableWidget(prev);

		Button next = Button.builder(Component.literal(">"), b -> {
			stageIndex = Math.min(stages.length - 1, stageIndex + 1);
			rebuild();
		}).bounds(leftPos + imageWidth - 56, topPos + imageHeight - 26, 20, 18).build();
		next.active = stageIndex < stages.length - 1;
		addRenderableWidget(next);
	}

	private void selectCategory(String key) {
		category = key;
		openEntry = null;
		stageIndex = 0;
		scrollOffset = 0;
		rebuild();
	}

	private void closeEntry() {
		openEntry = null;
		stageIndex = 0;
		rebuild();
	}

	private void openEntry(ResearchEntry entry) {
		IPlayerKnowledge knowledge = ClientPlayerKnowledgeCache.getData();
		if (!knowledge.isResearchComplete(entry.getKey())) {
			sendButtonClick(entry.getKey().hashCode());
		}
		openEntry = entry;
		stageIndex = 0;
		rebuild();
	}

	private void sendButtonClick(int id) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	/** Real requisite check mirroring {@code IInternalMethodHandler#doesPlayerHaveRequisites}/{@code
	 * ThaumcraftCapabilities#knowsResearchStrict}, but against the synced client-side knowledge cache rather
	 * than (the unsynced) {@code ThaumcraftCapabilities.getKnowledge(minecraft.player)} - see this class's
	 * own doc comment. Doesn't handle the rare "&&"/"||" combinator parent syntax (not used by any real
	 * parent list in the 8 shipped content files) - purely cosmetic if it ever mismatches, since the server's
	 * own {@code doesPlayerHaveRequisites} stays authoritative for the actual completion action.
	 */
	private boolean hasRequisites(ResearchEntry entry) {
		String[] parents = entry.getParentsStripped();
		if (parents == null) return true;
		IPlayerKnowledge knowledge = ClientPlayerKnowledgeCache.getData();
		for (String parent : parents) {
			if (parent.indexOf('@') >= 0) {
				if (!knowledge.isResearchKnown(parent)) return false;
			} else if (!knowledge.isResearchComplete(parent)) {
				return false;
			}
		}
		return true;
	}

	private List<ResearchEntry> visibleEntries() {
		ResearchCategory cat = ResearchCategories.getResearchCategory(category);
		List<ResearchEntry> out = new ArrayList<>();
		if (cat == null) return out;
		for (ResearchEntry entry : cat.research.values()) {
			if (!hasRequisites(entry) && entry.hasMeta(ResearchEntry.EnumResearchMeta.HIDDEN)) continue;
			out.add(entry);
		}
		out.sort(Comparator.comparingInt(ResearchEntry::getDisplayRow).thenComparingInt(ResearchEntry::getDisplayColumn));
		return out;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0201018);
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 24, 0xE0100810);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		if (openEntry != null) {
			renderReader(graphics);
		} else {
			renderList(graphics, mouseX, mouseY);
		}
		renderTooltip(graphics, mouseX, mouseY);
	}

	private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
		List<ResearchEntry> entries = visibleEntries();
		IPlayerKnowledge knowledge = ClientPlayerKnowledgeCache.getData();
		int y = listTop;
		for (int i = scrollOffset; i < entries.size() && i < scrollOffset + VISIBLE_ROWS; i++) {
			ResearchEntry entry = entries.get(i);
			boolean unlocked = hasRequisites(entry);
			boolean complete = unlocked && knowledge.isResearchComplete(entry.getKey());
			int color;
			Component label;
			if (!unlocked) {
				color = 0xFF707070;
				label = Component.literal("??? ").append(Component.translatable("tc.research.locked"));
			} else if (complete) {
				color = 0xFF60C060;
				label = entry.getLocalizedName();
			} else {
				color = 0xFFE0E060;
				label = entry.getLocalizedName();
			}
			boolean hovered = mouseX >= listLeft && mouseX < listLeft + listWidth && mouseY >= y && mouseY < y + ROW_HEIGHT;
			if (hovered && unlocked) {
				graphics.fill(listLeft, y, listLeft + listWidth, y + ROW_HEIGHT, 0x40FFFFFF);
			}
			graphics.drawString(font, label, listLeft + 3, y + 3, color);
			y += ROW_HEIGHT;
		}
		if (entries.isEmpty()) {
			graphics.drawString(font, Component.translatable("tc.research.empty"), listLeft + 3, listTop + 3, 0xFFA0A0A0);
		}
	}

	private void renderReader(GuiGraphics graphics) {
		ResearchStage[] stages = openEntry.getStages();
		ResearchStage stage = stages[Math.min(stageIndex, stages.length - 1)];

		graphics.drawString(font, openEntry.getLocalizedName(), listLeft, topPos + 30, 0xFFFFE0A0);
		graphics.drawString(font, Component.literal((stageIndex + 1) + " / " + stages.length), listLeft, topPos + 42, 0xFFA0A0A0);

		List<FormattedCharSequence> lines = font.split(stage.getTextLocalized(), listWidth);
		int y = topPos + 58;
		for (FormattedCharSequence line : lines) {
			if (y > topPos + imageHeight - 34) break;
			graphics.drawString(font, line, listLeft, y, 0xFFE0E0E0);
			y += font.lineHeight + 1;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (openEntry != null || button != 0) return false;
		if (mouseX < listLeft || mouseX >= listLeft + listWidth || mouseY < listTop) return false;
		int row = (int) ((mouseY - listTop) / ROW_HEIGHT);
		int index = scrollOffset + row;
		List<ResearchEntry> entries = visibleEntries();
		if (index < 0 || index >= entries.size()) return false;
		ResearchEntry entry = entries.get(index);
		if (!hasRequisites(entry)) return false;
		openEntry(entry);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (openEntry == null) {
			int max = Math.max(0, visibleEntries().size() - VISIBLE_ROWS);
			scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
}

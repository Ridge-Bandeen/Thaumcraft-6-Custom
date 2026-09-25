package thaumcraft.client.gui;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.client.ClientResearchTableCache;
import thaumcraft.common.container.ResearchTableMenu;

/**
 * Replaces the old (1.12.2) {@code GuiResearchTable} - the original's real screen (762 lines: a full card
 * layout with hover text, item-cost icons, and its own scrollbar/button-plugin widgets) isn't a line-for-
 * line port, same "lighter testing, real simplification" treatment {@code GuiFocalManipulator}'s own canvas
 * commit already got for the same reason (client-only rendering/input this session's GameTest discipline
 * can't reach).
 *
 * Reads {@link ClientResearchTableCache} (now real - see {@code ResearchTableMenu}'s own doc comment for the
 * sync this screen depends on) each frame to decide which of a fixed set of vanilla buttons should be
 * visible: "Start Research" (no theory in progress), "Draw 2"/"Draw 3" (a theory in progress, nothing
 * currently drawn), up to 3 card-choice buttons labelled by each card's own {@code getLocalizedName()}
 * (real card choices drawn), "Confirm" (a choice has been picked), "Finish"/"Abandon" (theory complete or
 * not). Real simplifications versus the original: no item-cost icons, no per-card hover/detail text, no
 * scrollbar (never needed - at most 3 choices show at once), plain text card names rather than a graphical
 * card layout.
 *
 * <p>Real research-category percentage sidebar ({@link #tickCategorySidebar}/{@link #renderCategorySidebar},
 * restored 2026-09-24): the true original's own real per-category live-counting percentage list, positioned
 * off the right edge of the shared 255-wide GUI panel exactly like the true original's own real layout. The
 * real underlying game data ({@code categoryTotals}/{@code penaltyStart}/{@code categoriesBlocked}) was
 * already fully tracked server-side and synced via {@link ClientResearchTableCache} well before this chunk -
 * this was purely a missing rendering feature, not a missing mechanic, once actually checked directly (a much
 * smaller real gap than an earlier scoping pass assumed from the true original's own overall 762-line size).
 * Real sparkle-particle trail ({@link FXGenericGui#spawn}, restored 2026-09-24 alongside that class itself) -
 * this is the true original's own ONLY genuinely live {@code drawSimpleSparkleGui} caller (see {@link
 * KnowledgeGainOverlay}'s own doc comment for why its own apparent sparkle tie-in is real, confirmed dead
 * code in the true original instead).
 */
public class GuiResearchTable extends AbstractContainerScreen<ResearchTableMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_table.png");

	private Button startButton;
	private Button draw2Button;
	private Button draw3Button;
	private Button[] choiceButtons;
	private Button confirmButton;
	private Button finishButton;
	private Button abandonButton;

	/** Real client-local smoothly-animated copy of {@code ResearchTableData#categoryTotals} - counts up/down
	 * 1%/tick toward the real synced value, matching the true original's own real {@code tempCatTotals}. */
	private final Map<String, Integer> tempCatTotals = new LinkedHashMap<>();
	private long nextCatCheck;

	/** Real per-tick "which categories just counted up" set, matching the true original's own real {@code
	 * sparkle} list - drives {@link FXGenericGui#spawn} in {@link #renderCategorySidebar}. Rebuilt fresh every
	 * time {@link #tickCategorySidebar} actually advances (not every render frame). */
	private final Set<String> justIncremented = new HashSet<>();

	public GuiResearchTable(ResearchTableMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 255;
		imageHeight = 255;
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();

		startButton = addRenderableWidget(Button.builder(Component.translatable("tc.research.start"), b -> click(0))
				.bounds(leftPos + 90, topPos + 100, 90, 20).build());

		draw2Button = addRenderableWidget(Button.builder(Component.literal("Draw 2"), b -> click(2))
				.bounds(leftPos + 40, topPos + 30, 80, 16).build());
		draw3Button = addRenderableWidget(Button.builder(Component.literal("Draw 3"), b -> click(3))
				.bounds(leftPos + 130, topPos + 30, 80, 16).build());

		choiceButtons = new Button[3];
		for (int i = 0; i < 3; i++) {
			int index = i;
			choiceButtons[i] = addRenderableWidget(Button.builder(Component.literal(""), b -> click(4 + index))
					.bounds(leftPos + 8, topPos + 50 + i * 18, 220, 16).build());
		}

		confirmButton = addRenderableWidget(Button.builder(Component.translatable("tc.research.confirm"), b -> click(1))
				.bounds(leftPos + 8, topPos + 108, 100, 16).build());
		finishButton = addRenderableWidget(Button.builder(Component.translatable("tc.research.finish"), b -> click(7))
				.bounds(leftPos + 8, topPos + 108, 100, 16).build());
		abandonButton = addRenderableWidget(Button.builder(Component.translatable("tc.research.abandon"), b -> click(9))
				.bounds(leftPos + 128, topPos + 108, 100, 16).build());
	}

	private void click(int buttonId) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
		}
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		ResearchTableData data = ClientResearchTableCache.getData();
		if (data != null) {
			boolean advanced = tickCategorySidebar(data);
			renderCategorySidebar(graphics, data, mouseX, mouseY, advanced);
		} else {
			tempCatTotals.clear();
		}
	}

	/** Real per-tick count-up/down toward the real synced {@code categoryTotals}, throttled to once per real
	 * game tick (matches the true original's own real {@code nexCatCheck} throttle - this method runs once per
	 * render FRAME, not once per tick, so without the throttle the animation would run at render framerate
	 * rather than a real, consistent 1%/tick pace). Returns whether the tick actually advanced this frame -
	 * {@link #justIncremented} (and the real sparkle trail it drives) is only meaningful on that same frame,
	 * matching the true original's own real {@code sparkle} list being rebuilt fresh every frame but only ever
	 * populated inside its own real throttled block. */
	private boolean tickCategorySidebar(ResearchTableData data) {
		if (minecraft == null || minecraft.player == null) return false;
		long now = minecraft.player.tickCount;
		if (nextCatCheck > now) return false;
		justIncremented.clear();
		for (String cat : ResearchCategories.researchCategories.keySet()) {
			int real = data.categoryTotals.getOrDefault(cat, 0);
			int shown = tempCatTotals.getOrDefault(cat, 0);
			if (real == 0 && shown == 0) {
				tempCatTotals.remove(cat);
				continue;
			}
			if (shown > real) shown--;
			if (shown < real) {
				shown++;
				justIncremented.add(cat);
			}
			tempCatTotals.put(cat, shown);
		}
		nextCatCheck = now + 1;
		return true;
	}

	/** Real sorted (highest-first) icon+percentage list, positioned off the right edge of the shared 255-wide
	 * GUI panel - matches the true original's own real layout/color-coding exactly (past {@code penaltyStart}:
	 * a real "(-N)" penalty suffix and a 4px extra row gap; {@code categoriesBlocked}: a real muted-red tint
	 * instead of the normal gold/white). See the class doc comment for the real, disclosed sparkle-trail gap. */
	private void renderCategorySidebar(GuiGraphics graphics, ResearchTableData data, int mouseX, int mouseY, boolean advanced) {
		List<Map.Entry<String, Integer>> sorted = tempCatTotals.entrySet().stream()
				.filter(e -> e.getValue() != 0)
				.sorted((a, b) -> b.getValue() - a.getValue())
				.toList();

		RandomSource random = minecraft != null && minecraft.level != null ? minecraft.level.random : RandomSource.create();
		int i = 0;
		for (Map.Entry<String, Integer> entry : sorted) {
			String field = entry.getKey();
			int percent = entry.getValue();
			ResearchCategory category = ResearchCategories.getResearchCategory(field);
			if (category == null) {
				i++;
				continue;
			}
			boolean pastPenalty = i > data.penaltyStart;
			int rowY = topPos + 16 + i * 18 + (pastPenalty ? 4 : 0);
			int iconX = leftPos + 253;

			graphics.blit(category.icon, iconX, rowY, 0, 0, 16, 16, 32, 32);

			String text = percent + "%";
			if (pastPenalty) {
				text += " (-" + (percent / 3) + ")";
			}
			int color = data.categoriesBlocked.contains(field) ? 6316128 : (pastPenalty ? 16777215 : 57536);
			int textX = iconX + 23;
			int textY = rowY + 4;
			graphics.drawString(font, text, textX, textY, color, true);

			if (advanced && justIncremented.contains(field)) {
				int width = font.width(text);
				for (int q = 0; q < 2; q++) {
					float rr = 1.0f;
					float gg = (189 + random.nextInt(255 - 189 + 1)) / 255.0f;
					float bb = (64 + random.nextInt(255 - 64 + 1)) / 255.0f;
					FXGenericGui.spawn(random, textX + random.nextFloat() * width, textY + random.nextFloat() * 8.0f,
							random.nextGaussian() * 0.5, random.nextGaussian() * 0.5, 24.0f, rr, gg, bb, 0, 0.9f, -1.0f);
				}
			}

			if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= rowY && mouseY < rowY + 16) {
				graphics.renderTooltip(font, ResearchCategories.getCategoryName(field), mouseX, mouseY);
			}
			i++;
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		updateWidgetState();
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void updateWidgetState() {
		ResearchTableData data = ClientResearchTableCache.getData();
		boolean inProgress = data != null;
		boolean complete = inProgress && data.isComplete();
		boolean hasChoices = inProgress && !data.cardChoices.isEmpty();
		boolean hasSelection = inProgress && data.cardChoices.stream().anyMatch(cc -> cc.selected);

		startButton.visible = !inProgress;

		draw2Button.visible = inProgress && !complete && !hasChoices;
		draw3Button.visible = draw2Button.visible;

		for (int i = 0; i < choiceButtons.length; i++) {
			boolean show = hasChoices && i < data.cardChoices.size();
			choiceButtons[i].visible = show;
			if (show) {
				ResearchTableData.CardChoice cc = data.cardChoices.get(i);
				choiceButtons[i].setMessage(Component.literal((cc.selected ? "> " : "") + cc.card.getLocalizedName()));
			}
		}

		confirmButton.visible = hasSelection;
		finishButton.visible = complete;
		abandonButton.visible = inProgress && !complete;
	}

	@Override
	public void removed() {
		super.removed();
		ClientResearchTableCache.clear();
	}
}

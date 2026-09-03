package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
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

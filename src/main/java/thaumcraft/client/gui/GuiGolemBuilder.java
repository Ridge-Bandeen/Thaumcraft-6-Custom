package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.container.GolemBuilderMenu;

/**
 * Replaces the old (1.12.2) {@code GuiGolemBuilder} - the original's real screen (426 lines: a graphical
 * part carousel with hover-scroll buttons, live health/armor/damage stat previews, and a cost-vs-owned
 * item-icon breakdown) isn't a line-for-line port, same "lighter testing, real simplification" treatment
 * every client-only screen this session has gotten (this port's own GameTest discipline is server-only and
 * structurally can't exercise Screen code at all).
 *
 * Reads {@link GolemBuilderMenu}'s own synced {@code ContainerData} directly each frame (no separate client
 * cache class needed, unlike {@code GuiResearchTable}'s own {@code ClientResearchTableCache} - the menu
 * itself already IS the synced client-side copy, see that menu's own doc comment for the
 * {@code ContainerData} mechanism). 5 buttons cycle material/head/arm/leg/addon (real
 * {@code clickMenuButton} ids 0-4, labelled by each part's own real {@code getLocalizedName()}); a 6th
 * starts the craft (id 5); plain text shows the in-progress cost, real numbers, no icon breakdown.
 */
public class GuiGolemBuilder extends AbstractContainerScreen<GolemBuilderMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_golembuilder.png");

	private Button materialButton;
	private Button headButton;
	private Button armButton;
	private Button legButton;
	private Button addonButton;
	private Button craftButton;

	public GuiGolemBuilder(GolemBuilderMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 208;
		imageHeight = 224;
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();

		materialButton = addRenderableWidget(Button.builder(Component.literal(""), b -> click(0))
				.bounds(leftPos + 8, topPos + 20, 190, 16).build());
		headButton = addRenderableWidget(Button.builder(Component.literal(""), b -> click(1))
				.bounds(leftPos + 8, topPos + 38, 190, 16).build());
		armButton = addRenderableWidget(Button.builder(Component.literal(""), b -> click(2))
				.bounds(leftPos + 8, topPos + 56, 190, 16).build());
		legButton = addRenderableWidget(Button.builder(Component.literal(""), b -> click(3))
				.bounds(leftPos + 8, topPos + 74, 190, 16).build());
		addonButton = addRenderableWidget(Button.builder(Component.literal(""), b -> click(4))
				.bounds(leftPos + 8, topPos + 92, 190, 16).build());

		craftButton = addRenderableWidget(Button.builder(Component.translatable("golem.craft"), b -> click(5))
				.bounds(leftPos + 8, topPos + 116, 190, 20).build());
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

		int cost = menu.getCost();
		int maxCost = menu.getMaxCost();
		if (cost > 0 || maxCost > 0) {
			Component progress = Component.literal(cost + " / " + maxCost);
			graphics.drawCenteredString(font, progress, leftPos + imageWidth / 2, topPos + 140, 0xFFFFFF);
		}
	}

	private void updateWidgetState() {
		materialButton.setMessage(menu.getSelectedMaterial().getLocalizedName());
		headButton.setMessage(menu.getSelectedHead().getLocalizedName());
		armButton.setMessage(menu.getSelectedArm().getLocalizedName());
		legButton.setMessage(menu.getSelectedLeg().getLocalizedName());
		addonButton.setMessage(menu.getSelectedAddon().getLocalizedName());

		boolean building = menu.getMaxCost() > 0;
		materialButton.active = !building;
		headButton.active = !building;
		armButton.active = !building;
		legButton.active = !building;
		addonButton.active = !building;
		craftButton.active = !building;
	}
}

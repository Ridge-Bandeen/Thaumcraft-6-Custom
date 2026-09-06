package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import thaumcraft.common.container.PechMenu;

/**
 * Replaces the old (1.12.2) {@code GuiPech}. Same "lighter, real simplification" treatment every client-only
 * screen this session has gotten (this port's own GameTest discipline is server-only and structurally can't
 * exercise Screen code at all, see {@code GuiGolemBuilder}'s own doc comment): the original's own clickable
 * icon overlay (a custom 25x25 hit-test region, only shown/enabled once the input slot holds a real valued
 * item and all 4 output slots are empty) is replaced with a plain real {@link Button} - same real
 * {@code clickMenuButton} id 0 underneath either way.
 */
public class GuiPechTrade extends AbstractContainerScreen<PechMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_pech.png");

	private Button tradeButton;

	public GuiPechTrade(PechMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 175;
		imageHeight = 232;
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		tradeButton = addRenderableWidget(Button.builder(Component.translatable("pech.trade"), b -> click(0))
				.bounds(leftPos + 55, topPos + 50, 65, 20).build());
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
		ItemStack input = menu.slots.get(0).getItem();
		boolean outputsEmpty = menu.slots.get(1).getItem().isEmpty() && menu.slots.get(2).getItem().isEmpty()
				&& menu.slots.get(3).getItem().isEmpty() && menu.slots.get(4).getItem().isEmpty();
		tradeButton.active = !input.isEmpty() && outputsEmpty;
	}
}

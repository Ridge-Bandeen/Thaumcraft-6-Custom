package thaumcraft.client.gui;

import java.util.Optional;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.common.container.LogisticsMenu;
import thaumcraft.common.lib.network.ServerboundLogisticsPackets;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * Replaces the old (1.12.2) {@code GuiLogistics}, at the original's layout (215x215): a 9x9 grid of everything your golem
 * Provide seals in range can supply, a scroll bar and arrows on the right, and along the bottom a quantity control
 * (minus, a bar, plus, the number), a Request button and a search box. Click an item to select it (highlighted), set how
 * many, and press Request: golems then fetch that many to the block you clicked with the Golem Bell - or to you.
 */
public class LogisticsScreen extends AbstractContainerScreen<LogisticsMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_logistics.png");

	private int selectedSlot = -1;
	private ItemStack selectedStack = ItemStack.EMPTY;
	private int stackSize = 1;
	private long nextRefresh;
	private int lastSentRow = -1;

	private EditBox searchBox;
	private Button minus;
	private Button plus;
	private Button request;

	public LogisticsScreen(LogisticsMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 215;
		imageHeight = 215;
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(Button.builder(Component.literal("^"), b -> click(1)).bounds(leftPos + 195, topPos + 16, 10, 10).build());
		addRenderableWidget(Button.builder(Component.literal("v"), b -> click(0)).bounds(leftPos + 195, topPos + 180, 10, 10).build());
		minus = addRenderableWidget(Button.builder(Component.literal("-"), b -> stackSize = Math.max(1, stackSize - 1)).bounds(leftPos + 13, topPos + 195, 10, 10).build());
		plus = addRenderableWidget(Button.builder(Component.literal("+"), b -> stackSize = Math.min(selectedCount(), stackSize + 1)).bounds(leftPos + 57, topPos + 195, 10, 10).build());
		request = addRenderableWidget(Button.builder(Component.translatable("tc.logistics.request"), b -> sendRequest()).bounds(leftPos + 116, topPos + 200, 40, 13).build());

		searchBox = new EditBox(font, leftPos + 143, topPos + 196, 55, 12, Component.translatable("tc.logistics.search"));
		searchBox.setMaxLength(10);
		searchBox.setHint(Component.translatable("tc.logistics.search"));
		searchBox.setResponder(text -> PacketDistributor.sendToServer(new ServerboundLogisticsPackets.Search(text)));
		addRenderableWidget(searchBox);
	}

	private void click(int buttonId) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
		}
	}

	private int selectedCount() {
		return selectedSlot >= 0 && menu.getSlot(selectedSlot).hasItem() ? Math.max(1, menu.getSlot(selectedSlot).getItem().getCount()) : 1;
	}

	private void sendRequest() {
		if (selectedSlot < 0 || !menu.getSlot(selectedSlot).hasItem()) return;
		ItemStack single = menu.getSlot(selectedSlot).getItem().copyWithCount(1);
		Direction side = menu.getSide() != null ? menu.getSide() : Direction.UP;
		PacketDistributor.sendToServer(new ServerboundLogisticsPackets.Request(Optional.ofNullable(menu.getTarget()), side, single, stackSize));
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		long now = System.currentTimeMillis();
		if (now > nextRefresh) {
			nextRefresh = now + 1000L;
			click(22); // re-scan the provider chests
		}
		// keep the selection on the same item if the list shifted under it
		if (selectedSlot >= 0 && !selectedStack.isEmpty() && !ItemStack.isSameItemSameComponents(selectedStack, menu.getSlot(selectedSlot).getItem())) {
			int found = -1;
			for (int i = 0; i < LogisticsMenu.SLOTS; i++) {
				if (ItemStack.isSameItemSameComponents(selectedStack, menu.getSlot(i).getItem())) {
					found = i;
					break;
				}
			}
			selectedSlot = found;
		}
		if (selectedSlot >= 0 && !menu.getSlot(selectedSlot).hasItem()) selectedSlot = -1;
		stackSize = Math.max(1, Math.min(stackSize, selectedCount()));
		boolean show = selectedSlot >= 0;
		minus.visible = show;
		plus.visible = show;
		request.visible = show;
	}

	@Override
	protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
		if (slot != null && slotId < LogisticsMenu.SLOTS && slot.hasItem()) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ThaumcraftSounds.CLACK.get(), 1.0f, 0.66f));
			selectedSlot = slotId;
			selectedStack = slot.getItem().copy();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			click(scrollY < 0 ? 0 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean inScrollBar(double mx, double my) {
		return mx >= leftPos + 196 && mx < leftPos + 204 && my >= topPos + 28 && my < topPos + 177;
	}

	private boolean inCountBar(double mx, double my) {
		return selectedSlot >= 0 && mx >= leftPos + 24 && mx < leftPos + 56 && my >= topPos + 196 && my < topPos + 204;
	}

	private void dragScroll(double my) {
		int end = menu.getEnd();
		if (end <= 0) return;
		double frac = Math.max(0.0, Math.min(1.0, (my - (topPos + 28)) / 141.0));
		int row = (int) Math.round(frac * end);
		if (row != lastSentRow) {
			lastSentRow = row;
			click(100 + row);
		}
	}

	private void dragCount(double mx) {
		int max = selectedCount();
		double frac = Math.max(0.0, Math.min(1.0, (mx - (leftPos + 24)) / 31.0));
		stackSize = Math.max(1, Math.min(max, 1 + (int) Math.round(frac * (max - 1))));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			if (inScrollBar(mouseX, mouseY)) {
				dragScroll(mouseY);
				return true;
			}
			if (inCountBar(mouseX, mouseY)) {
				dragCount(mouseX);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button == 0) {
			if (inScrollBar(mouseX, mouseY) || (mouseX >= leftPos + 190 && mouseX < leftPos + 210 && mouseY >= topPos + 20 && mouseY < topPos + 185)) {
				dragScroll(mouseY);
				return true;
			}
			if (selectedSlot >= 0 && mouseY >= topPos + 190 && mouseY < topPos + 210 && mouseX >= leftPos + 20 && mouseX < leftPos + 60) {
				dragCount(mouseX);
				return true;
			}
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		if (selectedSlot >= 0) {
			String s = Integer.toString(stackSize);
			graphics.drawString(font, s, 83 - font.width(s) / 2, 196, 0x333333, false);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		if (selectedSlot >= 0) {
			graphics.blit(TEXTURE, leftPos + 17 + selectedSlot % 9 * 19, topPos + 17 + selectedSlot / 9 * 19, 222, 46, 20, 20);
		}
		// scroll thumb
		int end = menu.getEnd();
		int thumb = end <= 0 ? 0 : (int) Math.round(menu.getStart() / (double) end * 141.0);
		graphics.fill(leftPos + 196, topPos + 28 + thumb, leftPos + 204, topPos + 28 + thumb + 8, 0xFFA0A0A0);
		// quantity thumb
		if (selectedSlot >= 0) {
			int max = selectedCount();
			int px = max <= 1 ? 0 : (int) Math.round((stackSize - 1) / (double) (max - 1) * 24.0);
			graphics.fill(leftPos + 24 + px, topPos + 196, leftPos + 24 + px + 8, topPos + 204, 0xFFA0A0A0);
		}
	}
}

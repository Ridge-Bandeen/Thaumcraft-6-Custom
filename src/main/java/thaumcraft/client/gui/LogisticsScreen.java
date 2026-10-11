package thaumcraft.client.gui;

import java.util.Optional;

import net.minecraft.client.gui.GuiGraphics;
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
 * many, and press Request: golems then fetch that many to the block you clicked with the Golem Bell - or to you. The
 * controls are the original's own widgets ({@link TCWidgets}).
 */
public class LogisticsScreen extends AbstractContainerScreen<LogisticsMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_logistics.png");

	private int selectedSlot = -1;
	private ItemStack selectedStack = ItemStack.EMPTY;
	private int stackSize = 1;
	private int lastStackSize = 1;
	private boolean stackSizeUpdated;
	private int lastScrollPos;
	private long nextRefresh;

	private EditBox searchBox;
	private TCWidgets.Slider scrollbar;
	private TCWidgets.Slider countbar;
	private TCWidgets.PlusMinusButton minus;
	private TCWidgets.PlusMinusButton plus;
	private TCWidgets.ImageButton request;

	public LogisticsScreen(LogisticsMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 215;
		imageHeight = 215;
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(new TCWidgets.ScrollButton(leftPos + 195, topPos + 16, 10, 10, true, true, () -> click(1)));
		addRenderableWidget(new TCWidgets.ScrollButton(leftPos + 195, topPos + 180, 10, 10, false, true, () -> click(0)));
		minus = addRenderableWidget(new TCWidgets.PlusMinusButton(leftPos + 13, topPos + 195, 10, 10, true, () -> {
			stackSize = Math.max(1, stackSize - 1);
			stackSizeUpdated = true;
		}));
		plus = addRenderableWidget(new TCWidgets.PlusMinusButton(leftPos + 57, topPos + 195, 10, 10, false, () -> {
			stackSize = Math.min(selectedCount(), stackSize + 1);
			stackSizeUpdated = true;
		}));
		scrollbar = addRenderableWidget(new TCWidgets.Slider(leftPos + 196, topPos + 28, 8, 149, 0.0f, menu.getEnd(), menu.getStart(), true));
		countbar = addRenderableWidget(new TCWidgets.Slider(leftPos + 24, topPos + 196, 32, 8, 1.0f, 64.0f, 1.0f, false));
		request = addRenderableWidget(new TCWidgets.ImageButton(leftPos + 116, topPos + 200, 40, 13, Component.translatable("tc.logistics.request"),
				TCWidgets.BASE, 37, 82, 40, 13, this::sendRequest));
		lastScrollPos = menu.getStart();

		// the original's text field: its border sits one pixel outside the 55x9 box
		searchBox = new EditBox(font, leftPos + 142, topPos + 195, 57, 11, Component.translatable("tc.logistics.search"));
		searchBox.setMaxLength(10);
		searchBox.setTextColor(0xFFFFFF);
		searchBox.setResponder(text -> PacketDistributor.sendToServer(new ServerboundLogisticsPackets.Search(text)));
		addRenderableWidget(searchBox);
		updateSelectionWidgets();
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

	private void updateSelectionWidgets() {
		boolean show = selectedSlot >= 0;
		countbar.visible = show;
		minus.visible = show;
		plus.visible = show;
		request.visible = show;
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		long now = System.currentTimeMillis();
		if (now > nextRefresh) {
			nextRefresh = now + 1000L;
			click(22); // re-scan the provider chests
		}
	}

	/** The original's foreground pass: the scroll bar follows (and drives) the list, the selection follows its item if
	 * the list shifts, and the quantity bar follows (and drives) the request size. */
	private void syncControls() {
		if (scrollbar.getMax() != menu.getEnd()) scrollbar.setMax(menu.getEnd());
		int sv = Math.round(scrollbar.getValue());
		if (sv != lastScrollPos) {
			lastScrollPos = sv;
			click(100 + lastScrollPos);
		} else if (sv != menu.getStart()) {
			// scrolled by the arrows or the wheel
			scrollbar.setValue(menu.getStart());
			lastScrollPos = menu.getStart();
		}

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
		updateSelectionWidgets();
		if (selectedSlot >= 0) {
			int count = menu.getSlot(selectedSlot).getItem().getCount();
			if (countbar.getMax() != count) countbar.setMax(count);
			int cv = Math.round(countbar.getValue());
			if (cv != lastStackSize) {
				lastStackSize = cv;
				stackSize = lastStackSize;
			} else if (stackSizeUpdated && cv != stackSize) {
				countbar.setValue(stackSize);
				stackSizeUpdated = false;
			}
		}
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

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		scrollbar.release();
		countbar.release();
		return super.mouseReleased(mouseX, mouseY, button);
	}

	/** As the original, typing goes to the search box while it has focus (the inventory key doesn't close the screen). */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (searchBox.isFocused() && keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			searchBox.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		// the search hint over the box, as the original, while it's empty and unfocused
		if (!searchBox.isFocused() && searchBox.getValue().isEmpty()) {
			graphics.drawString(font, Component.translatable("tc.logistics.search"), 146, 197, 0x222222, false);
		}
		if (selectedSlot >= 0 && menu.getSlot(selectedSlot).hasItem()) {
			String s = Integer.toString(stackSize);
			graphics.drawString(font, s, 83 - font.width(s) / 2, 196, 0x333333, false);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		syncControls();
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		if (selectedSlot >= 0) {
			graphics.blit(TEXTURE, leftPos + 17 + selectedSlot % 9 * 19, topPos + 17 + selectedSlot / 9 * 19, 222, 46, 20, 20);
		}
	}
}

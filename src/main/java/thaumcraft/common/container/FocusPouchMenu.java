package thaumcraft.common.container;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.items.casters.ItemFocusPouch;
import thaumcraft.common.registry.ThaumcraftMenus;

/**
 * Replaces the old (1.12.2) {@code ContainerFocusPouch}. Real slot layout kept exactly the same as the
 * original (18 focus slots in a 6x3 grid at (37 + col*18, 51 + row*18), matching {@code gui_focuspouch.png}'s
 * own real texture), each real-restricted to {@link ItemFocus} items only via {@link SlotItemHandler}'s own
 * {@code mayPlace} override - a direct modern equivalent of the original's own {@code SlotLimitedByClass}.
 *
 * The real 18-slot {@link ItemStackHandler} backing this is seeded once, at construction, from the pouch
 * stack's own real {@link ItemFocusPouch#getContents} - not a live view of it. Real, deliberate simplification
 * matching the original's own exact behavior (the original's {@code ContainerFocusPouch} did the same one-shot
 * copy-in/copy-out round trip, via {@code onContainerClosed}): changes are written back to the real pouch
 * stack in {@link #removed} (menu close), not continuously. This assumes nothing else touches the same pouch
 * stack's contents while its own menu is open - true here, since nothing else in this port reads or writes a
 * Focus Pouch's contents.
 */
public class FocusPouchMenu extends AbstractContainerMenu {

	private static final int POUCH_SLOTS = 18;
	private static final int PLAYER_INV_START = POUCH_SLOTS;
	private static final int HOTBAR_END = POUCH_SLOTS + 36;

	private final ItemStackHandler handler;
	private final ItemStack pouchStack;

	/** Client-side reconstruction - see {@code PechMenu}'s own doc comment for why: the client doesn't have
	 * the real pouch stack's own contents at construction time, but every real slot gets synced right after
	 * via vanilla's usual {@code Slot} sync regardless. */
	public FocusPouchMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, ItemStack.EMPTY);
	}

	public FocusPouchMenu(int containerId, Inventory playerInventory, ItemStack pouchStack) {
		super(ThaumcraftMenus.FOCUS_POUCH.get(), containerId);
		this.pouchStack = pouchStack;
		this.handler = new ItemStackHandler(POUCH_SLOTS) {
			@Override
			public boolean isItemValid(int slot, ItemStack stack) {
				return stack.getItem() instanceof ItemFocus;
			}
		};
		if (!pouchStack.isEmpty()) {
			NonNullList<ItemStack> contents = ItemFocusPouch.getContents(pouchStack);
			for (int i = 0; i < POUCH_SLOTS; i++) {
				handler.setStackInSlot(i, contents.get(i));
			}
		}

		for (int i = 0; i < POUCH_SLOTS; i++) {
			addSlot(new SlotItemHandler(handler, i, 37 + i % 6 * 18, 51 + i / 6 * 18));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 151 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 209));
		}
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!pouchStack.isEmpty() && !player.level().isClientSide) {
			NonNullList<ItemStack> contents = NonNullList.withSize(POUCH_SLOTS, ItemStack.EMPTY);
			for (int i = 0; i < POUCH_SLOTS; i++) {
				contents.set(i, handler.getStackInSlot(i));
			}
			ItemFocusPouch.setContents(pouchStack, contents);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack copy = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			copy = stack.copy();
			if (index < PLAYER_INV_START) {
				if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) {
					return ItemStack.EMPTY;
				}
			} else if (stack.getItem() instanceof ItemFocus) {
				if (!moveItemStackTo(stack, 0, POUCH_SLOTS, false)) {
					return ItemStack.EMPTY;
				}
			} else {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
			if (stack.getCount() == copy.getCount()) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, stack);
		}
		return copy;
	}
}

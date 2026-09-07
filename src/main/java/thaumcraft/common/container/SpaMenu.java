package thaumcraft.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import thaumcraft.common.registry.ThaumcraftMenus;
import thaumcraft.common.tiles.devices.TileSpa;

/**
 * Replaces the old (1.12.2) {@code ContainerSpa}. Slot layout matches the original's real Bath Salts slot
 * position (65, 31) exactly, then the usual 27 player-inventory + 9-hotbar slots. No "mix/unmix" toggle
 * button - see {@link TileSpa}'s own doc comment for why that mode is dropped; there's no other visible
 * server-only state left for a button to drive, so unlike {@link PechMenu}/{@link GolemBuilderMenu} this
 * menu needs no {@link #clickMenuButton} override at all.
 */
public class SpaMenu extends AbstractContainerMenu {

	private static final int INPUT_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int HOTBAR_END = 37;

	private final ContainerLevelAccess access;

	/** Client-side reconstruction - see {@link PechMenu}'s own doc comment for why. */
	public SpaMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new ItemStackHandler(1), ContainerLevelAccess.NULL);
	}

	public SpaMenu(int containerId, Inventory playerInventory, TileSpa tile) {
		this(containerId, playerInventory, tile.getItemHandler(), ContainerLevelAccess.create(tile.getLevel(), tile.getBlockPos()));
	}

	private SpaMenu(int containerId, Inventory playerInventory, IItemHandler handler, ContainerLevelAccess access) {
		super(ThaumcraftMenus.SPA.get(), containerId);
		this.access = access;

		addSlot(new SlotItemHandler(handler, INPUT_SLOT, 65, 31));
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, thaumcraft.api.blocks.BlocksTC.spa.get());
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
			} else if (!moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
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

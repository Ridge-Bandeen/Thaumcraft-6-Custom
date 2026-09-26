package thaumcraft.common.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.registry.ThaumcraftMenus;
import thaumcraft.common.tiles.crafting.TileThaumatorium;

/**
 * Replaces the old (1.12.2) {@code ContainerThaumatorium}: slot 0 is the catalyst slot, then the player inventory
 * (1-27) and hotbar (28-36), at the original's positions. Picking recipes is a menu-button action - button id =
 * the recipe's index in {@link TileThaumatorium#allRecipes} (identical on both sides), which the tile validates
 * (research known, catalyst matches, room in the list) before selecting or deselecting it.
 */
public class ThaumatoriumMenu extends AbstractContainerMenu {

	private final TileThaumatorium tile;
	private final ContainerLevelAccess access;

	/** Client constructor - the block position arrives in the open-menu extra data. */
	public ThaumatoriumMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
		this(containerId, playerInventory, playerInventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof TileThaumatorium t ? t : null);
	}

	public ThaumatoriumMenu(int containerId, Inventory playerInventory, TileThaumatorium tile) {
		super(ThaumcraftMenus.THAUMATORIUM.get(), containerId);
		this.tile = tile;
		this.access = tile != null && tile.getLevel() != null ? ContainerLevelAccess.create(tile.getLevel(), tile.getBlockPos()) : ContainerLevelAccess.NULL;

		addSlot(new SlotItemHandler(tile != null ? tile.getItemHandler() : new ItemStackHandler(1), 0, 55, 24));
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 9; j++) {
				addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 135 + i * 18));
			}
		}
		for (int i = 0; i < 9; i++) {
			addSlot(new Slot(playerInventory, i, 8 + i * 18, 193));
		}
	}

	public TileThaumatorium getTile() {
		return tile;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (tile == null || player.level().isClientSide) return true;
		tile.toggleSelection(player, id);
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			result = stack.copy();
			if (index == 0) {
				if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
			} else if (!moveItemStackTo(stack, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
			else slot.setChanged();
			if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
			slot.onTake(player, stack);
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return tile != null && stillValid(access, player, BlocksTC.thaumatorium.get());
	}
}

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
import thaumcraft.common.tiles.crafting.TileVoidSiphon;

/**
 * Replaces the old (1.12.2) {@code ContainerVoidSiphon}: one output-only slot (80, 32) holding what the siphon has
 * produced, then the player inventory (1-27) and hotbar (28-36). Nothing can be placed in the output slot. Restored
 * 2026-09-25 - the port's stand-in was a bare right-click that emptied the slot into your inventory.
 */
public class VoidSiphonMenu extends AbstractContainerMenu {

	private final TileVoidSiphon tile;
	private final ContainerLevelAccess access;

	/** Client constructor - the block position arrives in the open-menu extra data. */
	public VoidSiphonMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
		this(containerId, playerInventory, playerInventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof TileVoidSiphon t ? t : null);
	}

	public VoidSiphonMenu(int containerId, Inventory playerInventory, TileVoidSiphon tile) {
		super(ThaumcraftMenus.VOID_SIPHON.get(), containerId);
		this.tile = tile;
		this.access = tile != null && tile.getLevel() != null ? ContainerLevelAccess.create(tile.getLevel(), tile.getBlockPos()) : ContainerLevelAccess.NULL;

		addSlot(new SlotItemHandler(tile != null ? tile.getItemHandler() : new ItemStackHandler(1), 0, 80, 32) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 9; j++) {
				addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
			}
		}
		for (int i = 0; i < 9; i++) {
			addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			result = stack.copy();
			// only the output slot can be shifted out; nothing can be shifted in
			if (index != 0 || !moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
			if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
			else slot.setChanged();
			if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
			slot.onTake(player, stack);
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return tile != null && stillValid(access, player, BlocksTC.voidSiphon.get());
	}
}

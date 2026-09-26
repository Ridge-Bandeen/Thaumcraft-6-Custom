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
import thaumcraft.common.tiles.devices.TilePotionSprayer;

/**
 * Replaces the old (1.12.2) {@code ContainerPotionSprayer}: the potion slot (56, 64), then the player inventory (1-27)
 * at (16, 151) and hotbar (28-36) at (16, 209) - the panel is 192x233. Restored 2026-09-25; the port's stand-in
 * inserted or removed the potion on right-click and showed none of the recipe or charge state.
 */
public class PotionSprayerMenu extends AbstractContainerMenu {

	private final TilePotionSprayer tile;
	private final ContainerLevelAccess access;

	/** Client constructor - the block position arrives in the open-menu extra data. */
	public PotionSprayerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
		this(containerId, playerInventory, playerInventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof TilePotionSprayer t ? t : null);
	}

	public PotionSprayerMenu(int containerId, Inventory playerInventory, TilePotionSprayer tile) {
		super(ThaumcraftMenus.POTION_SPRAYER.get(), containerId);
		this.tile = tile;
		this.access = tile != null && tile.getLevel() != null ? ContainerLevelAccess.create(tile.getLevel(), tile.getBlockPos()) : ContainerLevelAccess.NULL;

		addSlot(new SlotItemHandler(tile != null ? tile.getItemHandler() : new ItemStackHandler(1), 0, 56, 64) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return TilePotionSprayer.isValidPotion(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 9; j++) {
				addSlot(new Slot(playerInventory, j + i * 9 + 9, 16 + j * 18, 151 + i * 18));
			}
		}
		for (int i = 0; i < 9; i++) {
			addSlot(new Slot(playerInventory, i, 16 + i * 18, 209));
		}
	}

	public TilePotionSprayer getTile() {
		return tile;
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
			} else if (!slots.get(0).mayPlace(stack) || !moveItemStackTo(stack, 0, 1, false)) {
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
		return tile != null && stillValid(access, player, BlocksTC.potionSprayer.get());
	}
}

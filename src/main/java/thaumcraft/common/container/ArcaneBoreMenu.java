package thaumcraft.common.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import thaumcraft.common.entities.construct.EntityArcaneBore;
import thaumcraft.common.registry.ThaumcraftMenus;

/**
 * Replaces the old (1.12.2) {@code ContainerArcaneBore}: one slot (80, 29) that IS the bore's main-hand pickaxe (only
 * pickaxes can be put in it), then the player inventory (1-27) and hotbar (28-36). Restored 2026-09-25 - the port's
 * stand-in swapped the held item with the bore on right-click, which showed none of the bore's stats.
 */
public class ArcaneBoreMenu extends AbstractContainerMenu {

	private final EntityArcaneBore bore;

	/** Client constructor - the bore's entity id arrives in the open-menu extra data. */
	public ArcaneBoreMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
		this(containerId, playerInventory, playerInventory.player.level().getEntity(buf.readVarInt()) instanceof EntityArcaneBore b ? b : null);
	}

	public ArcaneBoreMenu(int containerId, Inventory playerInventory, EntityArcaneBore bore) {
		super(ThaumcraftMenus.ARCANE_BORE.get(), containerId);
		this.bore = bore;

		addSlot(new Slot(new MainHandContainer(bore), 0, 80, 29) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(ItemTags.PICKAXES);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
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

	public EntityArcaneBore getBore() {
		return bore;
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
		return bore != null && bore.isAlive() && player.distanceToSqr(bore) <= 64.0;
	}

	/** A one-slot {@link Container} view of a mob's main-hand item. */
	private static final class MainHandContainer implements Container {
		private final EntityArcaneBore mob;

		MainHandContainer(EntityArcaneBore mob) {
			this.mob = mob;
		}

		@Override
		public int getContainerSize() {
			return 1;
		}

		@Override
		public boolean isEmpty() {
			return mob == null || mob.getMainHandItem().isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return mob == null ? ItemStack.EMPTY : mob.getMainHandItem();
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			if (mob == null) return ItemStack.EMPTY;
			ItemStack current = mob.getMainHandItem();
			ItemStack taken = current.split(amount);
			mob.setItemInHand(InteractionHand.MAIN_HAND, current);
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			if (mob == null) return ItemStack.EMPTY;
			ItemStack current = mob.getMainHandItem();
			mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return current;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			if (mob != null) mob.setItemInHand(InteractionHand.MAIN_HAND, stack);
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player player) {
			return mob != null && mob.isAlive();
		}

		@Override
		public void clearContent() {
			if (mob != null) mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
	}
}

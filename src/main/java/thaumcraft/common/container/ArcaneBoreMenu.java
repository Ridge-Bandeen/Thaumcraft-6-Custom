package thaumcraft.common.container;

import net.minecraft.network.FriendlyByteBuf;
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

		addSlot(new Slot(new MobMainHandContainer(bore), 0, 80, 29) {
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
}

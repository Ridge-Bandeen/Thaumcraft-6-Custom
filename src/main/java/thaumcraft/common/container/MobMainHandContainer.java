package thaumcraft.common.container;

import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A one-slot {@link Container} view of a mob's main-hand item - the original's {@code SlotMobEquipment}, behind the
 * Arcane Bore's pickaxe slot and the turrets' ammo slot. The mob may be null on the client before it has loaded.
 */
public final class MobMainHandContainer implements Container {

	private final Mob mob;

	public MobMainHandContainer(Mob mob) {
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

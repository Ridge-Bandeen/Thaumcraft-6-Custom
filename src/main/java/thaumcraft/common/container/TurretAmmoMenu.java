package thaumcraft.common.container;

import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.entities.construct.EntityTurretCrossbow;

/**
 * What the original's {@code ContainerTurretBasic} and {@code ContainerTurretAdvanced} share: one ammo slot that IS the
 * turret's main-hand item (arrows only, the original's {@code SlotTurretBasic}), then the player inventory (1-27) and
 * hotbar (28-36). Shift-clicking moves arrows into the ammo slot and the ammo back out.
 */
public abstract class TurretAmmoMenu extends AbstractContainerMenu {

	@Nullable
	protected final EntityTurretCrossbow turret;

	protected TurretAmmoMenu(MenuType<?> type, int containerId, Inventory playerInventory, @Nullable EntityTurretCrossbow turret, int ammoX) {
		super(type, containerId);
		this.turret = turret;
		addSlot(new Slot(new MobMainHandContainer(turret), 0, ammoX, 29) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof ArrowItem;
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

	/** The turret named by the entity id in the open-menu extra data (the client side), or null. */
	@Nullable
	protected static <T extends EntityTurretCrossbow> T readTurret(Inventory playerInventory, FriendlyByteBuf buf, Class<T> type) {
		var entity = playerInventory.player.level().getEntity(buf.readVarInt());
		return type.isInstance(entity) ? type.cast(entity) : null;
	}

	@Nullable
	public EntityTurretCrossbow getTurret() {
		return turret;
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
		return turret != null && turret.isAlive() && player.distanceToSqr(turret) <= 64.0;
	}
}

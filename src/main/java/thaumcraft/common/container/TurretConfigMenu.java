package thaumcraft.common.container;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced;
import thaumcraft.common.registry.ThaumcraftMenus;

/**
 * Replaces the old (1.12.2) {@code ContainerTurretAdvanced}: the ammo slot (42, 29) and player inventory (see
 * {@link TurretAmmoMenu}), plus the four target filters (animal/mob/player/friendly) toggled by menu buttons and a health
 * readout, both synced through a plain {@link ContainerData}.
 */
public class TurretConfigMenu extends TurretAmmoMenu {

	private static final int DATA_FLAGS = 0;
	private static final int DATA_HEALTH = 1;
	private static final int DATA_MAX_HEALTH = 2;
	private static final int DATA_COUNT = 3;

	public static final int BUTTON_TARGET_ANIMAL = 0;
	public static final int BUTTON_TARGET_MOB = 1;
	public static final int BUTTON_TARGET_PLAYER = 2;
	public static final int BUTTON_TARGET_FRIENDLY = 3;

	private final ContainerData data = new SimpleContainerData(DATA_COUNT);

	public TurretConfigMenu(int containerId, Inventory playerInventory, @Nullable EntityTurretCrossbowAdvanced turret) {
		super(ThaumcraftMenus.TURRET_CONFIG.get(), containerId, playerInventory, turret, 42);
		addDataSlots(data);
	}

	/** Client constructor - the turret's entity id arrives in the open-menu extra data. */
	public TurretConfigMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, readTurret(playerInventory, buf, EntityTurretCrossbowAdvanced.class));
	}

	@Nullable
	private EntityTurretCrossbowAdvanced advanced() {
		return turret instanceof EntityTurretCrossbowAdvanced a ? a : null;
	}

	@Override
	public void broadcastChanges() {
		EntityTurretCrossbowAdvanced turret = advanced();
		if (turret != null && !turret.level().isClientSide) {
			int flags = 0;
			if (turret.getTargetAnimal()) flags |= 1;
			if (turret.getTargetMob()) flags |= 2;
			if (turret.getTargetPlayer()) flags |= 4;
			if (turret.getTargetFriendly()) flags |= 8;
			data.set(DATA_FLAGS, flags);
			data.set(DATA_HEALTH, Math.round(turret.getHealth()));
			data.set(DATA_MAX_HEALTH, Math.round(turret.getMaxHealth()));
		}
		super.broadcastChanges();
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		EntityTurretCrossbowAdvanced turret = advanced();
		if (turret == null || !turret.isAlive() || !turret.isOwnedBy(player)) {
			return false;
		}
		switch (id) {
			case BUTTON_TARGET_ANIMAL -> turret.setTargetAnimal(!turret.getTargetAnimal());
			case BUTTON_TARGET_MOB -> turret.setTargetMob(!turret.getTargetMob());
			case BUTTON_TARGET_PLAYER -> turret.setTargetPlayer(!turret.getTargetPlayer());
			case BUTTON_TARGET_FRIENDLY -> turret.setTargetFriendly(!turret.getTargetFriendly());
			default -> {
				return false;
			}
		}
		return true;
	}

	// --- client-side accessors, backing TurretConfigScreen ---

	public boolean isTargetAnimal() {
		return (data.get(DATA_FLAGS) & 1) != 0;
	}

	public boolean isTargetMob() {
		return (data.get(DATA_FLAGS) & 2) != 0;
	}

	public boolean isTargetPlayer() {
		return (data.get(DATA_FLAGS) & 4) != 0;
	}

	public boolean isTargetFriendly() {
		return (data.get(DATA_FLAGS) & 8) != 0;
	}

	/** 0..1 fraction, for the health bar. */
	public float getHealthFraction() {
		int max = data.get(DATA_MAX_HEALTH);
		return max <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, data.get(DATA_HEALTH) / (float) max));
	}
}

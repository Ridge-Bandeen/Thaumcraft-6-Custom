package thaumcraft.common.container;

import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.common.entities.construct.EntityTurretCrossbow;
import thaumcraft.common.registry.ThaumcraftMenus;

/** The original's {@code ContainerTurretBasic}: the Basic Crossbow Turret's ammo slot (80, 29) and the player inventory. */
public class TurretBasicMenu extends TurretAmmoMenu {

	/** Client constructor - the turret's entity id arrives in the open-menu extra data. */
	public TurretBasicMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
		this(containerId, playerInventory, readTurret(playerInventory, buf, EntityTurretCrossbow.class));
	}

	public TurretBasicMenu(int containerId, Inventory playerInventory, @Nullable EntityTurretCrossbow turret) {
		super(ThaumcraftMenus.TURRET_BASIC.get(), containerId, playerInventory, turret, 80);
	}
}

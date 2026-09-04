package thaumcraft.common.container;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced;
import thaumcraft.common.registry.ThaumcraftMenus;

/**
 * Replaces the old (1.12.2) {@code ContainerTurretAdvanced} - real target-filter flags (animal/mob/player/
 * friendly) and a real health readout, both synced through a plain {@link ContainerData}, same
 * {@code AbstractFurnaceMenu}-style mechanism {@link GolemBuilderMenu}/{@link SealConfigMenu} already
 * established in this port. No player-inventory or ammo slots at all (real, documented cut, matching
 * {@link EntityTurretCrossbowAdvanced}'s own doc comment - real dispenser auto-reload already covers the
 * original's own single ammo slot, and this menu has nothing else to hold) - the original's own container
 * only ever had the one ammo slot plus the player's own inventory reflected back at it anyway.
 *
 * <p>The turret reference is null on the client (nothing per-instance to read there - every real field this
 * screen needs comes through {@link #data}), same "the menu already IS the synced copy" shape
 * {@link SealConfigMenu} already established; unlike that menu, this one's shape never varies per instance,
 * so the client constructor doesn't need the extra-data buffer at all.
 */
public class TurretConfigMenu extends AbstractContainerMenu {

	private static final int DATA_FLAGS = 0;
	private static final int DATA_HEALTH = 1;
	private static final int DATA_MAX_HEALTH = 2;
	private static final int DATA_COUNT = 3;

	public static final int BUTTON_TARGET_ANIMAL = 0;
	public static final int BUTTON_TARGET_MOB = 1;
	public static final int BUTTON_TARGET_PLAYER = 2;
	public static final int BUTTON_TARGET_FRIENDLY = 3;

	private final EntityTurretCrossbowAdvanced turret;
	private final ContainerData data = new SimpleContainerData(DATA_COUNT);

	/** Server-side: the menu can read every real field straight off the live turret. */
	public TurretConfigMenu(int containerId, Inventory playerInventory, EntityTurretCrossbowAdvanced turret) {
		super(ThaumcraftMenus.TURRET_CONFIG.get(), containerId);
		this.turret = turret;
		addDataSlots(data);
	}

	/** Client-side: nothing entity-specific to read - see this class's own doc comment. */
	public TurretConfigMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		this(containerId, playerInventory, (EntityTurretCrossbowAdvanced) null);
	}

	@Override
	public void broadcastChanges() {
		if (turret != null) {
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

	// --- real client-side accessors, backing TurretConfigScreen ---

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

	/** 0..1 fraction, for the real health bar overlay - see {@code TurretConfigScreen}'s own doc comment. */
	public float getHealthFraction() {
		int max = data.get(DATA_MAX_HEALTH);
		return max <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, data.get(DATA_HEALTH) / (float) max));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return turret == null || (turret.isAlive() && turret.isOwnedBy(player));
	}
}

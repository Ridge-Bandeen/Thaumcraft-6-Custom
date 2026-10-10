package thaumcraft.common.entities.projectile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityAlumentum} - the thrown-fire-bomb projectile behind
 * {@link thaumcraft.common.items.consumables.ItemAlumentum}. Extends vanilla's
 * {@link ThrowableItemProjectile} (the same base {@code Snowball}/{@code ThrownEgg} use - a thrown item
 * that renders as a spinning icon of whatever {@code getItem()} returns) rather than the generic
 * {@link net.minecraft.world.entity.projectile.ThrowableProjectile} this port's other projectiles use,
 * since Alumentum (unlike a wand focus) is a real, visible held item being thrown.
 *
 * As the original it bursts in a small fiery explosion (strength 1.1) that breaks blocks.
 *
 * <p>Real fire-mote flight trail restored 2026-09-17 (was dropped under this port's old blanket "no client
 * FX" rule) - real, faithful port of the true original's own exact per-tick spawn (colour/scale/jitter all
 * matching), routed through {@link thaumcraft.client.fx.ClientFxHandlers#spawnFireMote} rather than
 * referencing the client-only {@code FXFireMote} directly from this common-side class, the same real
 * dist-crash-avoidance boundary this whole port has kept since {@code FocusEffect#renderParticleFX}.
 */
public class EntityAlumentum extends ThrowableItemProjectile {

	public EntityAlumentum(EntityType<? extends EntityAlumentum> type, Level level) {
		super(type, level);
	}

	public EntityAlumentum(LivingEntity thrower, Level level) {
		super(ThaumcraftEntities.ALUMENTUM.get(), thrower, level);
	}

	@Override
	protected Item getDefaultItem() {
		return ItemsTC.alumentum.get();
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			thaumcraft.client.fx.ClientFxHandlers.thrownTrail(level(), this, false);
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide) {
			level().explode(this, getX(), getY(), getZ(), 1.1f, true, Level.ExplosionInteraction.TNT);
			discard();
		}
	}
}

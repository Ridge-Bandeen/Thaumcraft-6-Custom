package thaumcraft.common.entities.projectile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityCausalityCollapser} - the thrown-grenade projectile behind
 * {@link thaumcraft.common.items.consumables.ItemCausalityCollapser}. Same real {@link ThrowableItemProjectile}
 * shape as {@link EntityBottleTaint}/{@link EntityAlumentum} (this port's own closest already-shipped
 * siblings). Found and closed while reviewing this port's own "dead/blocked" candidates for real remaining
 * gaps vs. genuine cuts - previously skipped as blocked on {@link EntityFluxRift}, which is real now (built
 * later for Void Siphon; see the port-tracking memory for the fuller review).
 *
 * On impact: a real block-damaging, fire-starting explosion ({@link Level.ExplosionInteraction#TNT}, the
 * real modern equivalent of the original's own {@code world.createExplosion(this, ..., 2.0f, true)} - same
 * real strength, same real "causes fire" behavior), then collapses any real {@link EntityFluxRift} within 3
 * blocks (same real range/method the original used).
 *
 * Real fire-mote flight trail restored 2026-09-17 (was dropped under this port's old blanket "no client FX"
 * rule) - the real {@code drawAlumentum} half of the true original's own client-side trail (same real
 * particle class as {@link EntityAlumentum}'s own trail, a real warmer red-orange tint instead), routed
 * through {@link thaumcraft.client.fx.ClientFxHandlers#spawnFireMote} for the same real dist-crash-avoidance
 * reason. The original's own separate generic-particle half of that same trail isn't restored here - a
 * smaller, real remaining gap, not re-scoped in this pass.
 */
public class EntityCausalityCollapser extends ThrowableItemProjectile {

	public EntityCausalityCollapser(EntityType<? extends EntityCausalityCollapser> type, Level level) {
		super(type, level);
	}

	public EntityCausalityCollapser(LivingEntity thrower, Level level) {
		super(ThaumcraftEntities.CAUSALITY_COLLAPSER.get(), thrower, level);
	}

	@Override
	protected Item getDefaultItem() {
		return ItemsTC.causalityCollapser.get();
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			thaumcraft.client.fx.ClientFxHandlers.thrownTrail(level(), this, true);
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide) {
			level().explode(this, getX(), getY(), getZ(), 2.0f, Level.ExplosionInteraction.TNT);
			AABB area = new AABB(getX(), getY(), getZ(), getX(), getY(), getZ()).inflate(3.0);
			for (EntityFluxRift rift : level().getEntitiesOfClass(EntityFluxRift.class, area)) {
				rift.setCollapse(true);
			}
			discard();
		}
	}
}

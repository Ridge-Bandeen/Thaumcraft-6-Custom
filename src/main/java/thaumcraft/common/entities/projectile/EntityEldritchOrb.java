package thaumcraft.common.entities.projectile;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityEldritchOrb} -
 * {@link thaumcraft.common.entities.monster.EntityEldritchGuardian}/
 * {@link thaumcraft.common.entities.monster.boss.EntityEldritchWarden}'s ranged attack projectile. Extends
 * {@link ThrowableProjectile} like {@link EntityGolemOrb} (that class's own doc comment explains the real
 * pattern reused here), but doesn't home - straight shot, matching the original's own plain {@code
 * EntityThrowable}. The caller aims it via the inherited {@code shoot(x, y, z, velocity, inaccuracy)}
 * right after construction, same as {@link thaumcraft.common.entities.monster.boss.EntityCultistLeader}
 * already does for {@link EntityGolemOrb}.
 *
 * Real AoE burst on any impact (entity or block): every living, non-undead entity within 2 blocks of the
 * orb takes indirect-magic damage (66.6% of the thrower's own Attack Damage) and a real 8-second Weakness -
 * matches the original's own {@code onImpact} exactly (it scanned around the orb itself, not just whatever
 * it directly hit - a real splash attack, not a single-target one). Real self-expiry at 100 ticks.
 *
 * Real, documented cut: no impact/travel particle trail effects (tied to the unported {@code FXDispatcher}).
 * Real visible model restored 2026-09-16 - see {@link thaumcraft.client.fx.particles.FXEldritchOrbSpikes}'s
 * own doc comment (this entity's own registered {@code EntityRenderer} stays the invisible {@code
 * RenderFocusProjectile} placeholder, matching {@code WISP}/{@code GOLEM_ORB} - the real visual is entirely 2
 * real client-local tracking particles, not drawn from an {@code EntityRenderer} at all).
 */
public class EntityEldritchOrb extends ThrowableProjectile {

	public EntityEldritchOrb(EntityType<? extends EntityEldritchOrb> type, Level level) {
		super(type, level);
	}

	public EntityEldritchOrb(LivingEntity thrower) {
		super(ThaumcraftEntities.ELDRITCH_ORB.get(), thrower, thrower.level());
		setOwner(thrower);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		// no per-entity synced state - see the class doc comment.
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			thaumcraft.client.fx.ClientFxHandlers.maintainEldritchOrb(this);
		}
		if (tickCount > 100) {
			discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		burst();
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		burst();
	}

	private void burst() {
		if (level().isClientSide || isRemoved()) return;
		if (getOwner() instanceof LivingEntity thrower) {
			var atk = thrower.getAttribute(Attributes.ATTACK_DAMAGE);
			float damage = atk != null ? (float) atk.getValue() * 0.666f : 4.0f;
			AABB area = getBoundingBox().inflate(2.0);
			for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class, area, e -> e != thrower && e.isAlive())) {
				if (!victim.getType().is(EntityTypeTags.UNDEAD)) {
					victim.hurt(level().damageSources().indirectMagic(this, thrower), damage);
					victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
				}
			}
		}
		playSound(SoundEvents.FIRE_EXTINGUISH, 0.5f, 2.6f + (random.nextFloat() - random.nextFloat()) * 0.8f);
		discard();
	}
}

package thaumcraft.common.entities.projectile;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityGolemOrb} - {@link thaumcraft.common.entities.monster.cult.EntityCultistCleric}'s
 * real ranged attack projectile (the "blast" alt-attack, vs. the small-fireball alt-attack; see that
 * class's own {@code attackEntityWithRangedAttack}). Extends {@link ThrowableProjectile} rather than
 * reimplementing flight by hand, same real pattern {@code EntityFocusProjectile} already established -
 * only {@link #tick()} (for homing) and {@link #onHitEntity}/{@link #onHitBlock} (for impact damage) are
 * overridden.
 *
 * Real homing: steers toward its {@code target} every tick, clamped to the original's own
 * ±0.25-per-axis speed limit. Real damage on hit (thrower's own {@code Attributes.ATTACK_DAMAGE} - full
 * for a "red" orb, 60% for a normal one) via {@code damageSources().indirectMagic}, matching the original's
 * {@code DamageSource.causeIndirectMagicDamage}. Real self-expiry (240 ticks red / 160 ticks normal,
 * matching the original exactly). Real "reflect it back" mechanic via {@link #hurt} - a player who hits the
 * orb redirects it along their own look vector, exactly like the original.
 *
 * Real, documented cuts vs the original: no client spawn-data sync for {@code target}/{@code red} (this
 * project's established "no client FX" simplification, same as every other focus/projectile entity here -
 * the homing math and damage calc are server-only anyway) and no impact/travel particle effects (tied to
 * the unported {@code FXDispatcher}). Uses {@link thaumcraft.client.render.RenderFocusProjectile} (no
 * visible model) for the same reason.
 */
public class EntityGolemOrb extends ThrowableProjectile {

	private LivingEntity target;
	public boolean red;

	public EntityGolemOrb(EntityType<? extends EntityGolemOrb> type, Level level) {
		super(type, level);
	}

	public EntityGolemOrb(LivingEntity thrower, LivingEntity target, boolean red) {
		super(ThaumcraftEntities.GOLEM_ORB.get(), thrower, thrower.level());
		setOwner(thrower);
		this.target = target;
		this.red = red;
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
		if (tickCount > (red ? 240 : 160)) {
			discard();
			return;
		}
		if (target != null && target.isAlive()) {
			Vec3 pos = position();
			double dx = target.getX() - pos.x;
			double dy = target.getBoundingBox().minY + target.getBbHeight() * 0.6 - pos.y;
			double dz = target.getZ() - pos.z;
			double distSq = Math.max(dx * dx + dy * dy + dz * dz, 1.0e-4);
			double dist = Math.sqrt(distSq);
			double pull = 0.2;
			Vec3 motion = getDeltaMovement();
			double mx = clamp(motion.x + (dx / dist) * pull, -0.25, 0.25);
			double my = clamp(motion.y + (dy / dist) * pull, -0.25, 0.25);
			double mz = clamp(motion.z + (dz / dist) * pull, -0.25, 0.25);
			setDeltaMovement(mx, my, mz);
		}
	}

	private static double clamp(double v, double min, double max) {
		return v < min ? min : Math.min(v, max);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (!level().isClientSide && getOwner() != null) {
			var attackDamage = getOwner() instanceof LivingEntity owner ? owner.getAttribute(Attributes.ATTACK_DAMAGE) : null;
			float damage = attackDamage != null ? (float) attackDamage.getValue() * (red ? 1.0f : 0.6f) : (red ? 4.0f : 2.4f);
			result.getEntity().hurt(level().damageSources().indirectMagic(this, getOwner()), damage);
		}
		playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f);
		discard();
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f);
		discard();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (isInvulnerableTo(source)) {
			return false;
		}
		Entity direct = source.getDirectEntity();
		if (direct != null) {
			Vec3 look = direct.getLookAngle();
			setDeltaMovement(look.scale(0.9));
			playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f);
			return true;
		}
		return false;
	}
}

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
 * As the original, the target and colour travel to the client with the orb (so it homes there too), the pull
 * weakens with the square of the distance (the original divided by the distance squared), it bursts in a puff with
 * the shock sound, and a hit reflects it with a zap.
 */
public class EntityGolemOrb extends ThrowableProjectile implements net.neoforged.neoforge.entity.IEntityWithComplexSpawn {

	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_RED =
			SynchedEntityData.defineId(EntityGolemOrb.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

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
		setRed(red);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_RED, false);
	}

	public void setRed(boolean value) {
		this.red = value;
		entityData.set(DATA_RED, value);
	}

	public boolean isRed() {
		return entityData.get(DATA_RED);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			thaumcraft.client.fx.ClientFxHandlers.maintainGolemOrb(this, isRed());
		}
		if (tickCount > (red ? 240 : 160)) {
			discard();
			return;
		}
		if (target != null && target.isAlive()) {
			Vec3 pos = position();
			double dx = target.getX() - pos.x;
			double dy = target.getBoundingBox().minY + target.getBbHeight() * 0.6 - pos.y;
			double dz = target.getZ() - pos.z;
			// as the original: divided by the distance squared
			double distSq = Math.max(distanceToSqr(target), 1.0e-4);
			double pull = 0.2;
			Vec3 motion = getDeltaMovement();
			double mx = clamp(motion.x + (dx / distSq) * pull, -0.25, 0.25);
			double my = clamp(motion.y + (dy / distSq) * pull, -0.25, 0.25);
			double mz = clamp(motion.z + (dz / distSq) * pull, -0.25, 0.25);
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
			result.getEntity().hurt(level().damageSources().indirectMagic(getOwner(), this), damage);
		}
		impact();
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		impact();
	}

	private void impact() {
		playSound(thaumcraft.common.registry.ThaumcraftSounds.SHOCK.get(), 1.0f, 1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f);
		if (level().isClientSide) thaumcraft.client.fx.ClientFxHandlers.burst(level(), getX(), getY(), getZ(), 1.0f);
		discard();
	}

	@Override
	public void writeSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
		buffer.writeInt(target != null ? target.getId() : -1);
		buffer.writeBoolean(red);
	}

	@Override
	public void readSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
		int id = buffer.readInt();
		if (id >= 0 && level().getEntity(id) instanceof LivingEntity living) target = living;
		red = buffer.readBoolean();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (isInvulnerableTo(source)) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (attacker != null) {
			Vec3 look = attacker.getLookAngle();
			setDeltaMovement(look.scale(0.9));
			playSound(thaumcraft.common.registry.ThaumcraftSounds.ZAP.get(), 1.0f, 1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f);
			return true;
		}
		return false;
	}
}

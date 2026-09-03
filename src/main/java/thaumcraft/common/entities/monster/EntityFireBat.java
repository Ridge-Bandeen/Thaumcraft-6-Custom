package thaumcraft.common.entities.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Replaces the old (1.12.2) {@code EntityFireBat} - hand-rolled erratic flight AI ({@code registerGoals()}
 * intentionally empty), same real approach {@link EntityWisp} already established for this port's other
 * exotic flyer (there's no vanilla {@code Goal} for either bat-hanging or wisp-zapping to replace). Hangs
 * from a real solid ceiling block until a player wanders within 4 blocks or the ceiling itself disappears,
 * then wanders/homes in exactly like {@link EntityWisp}'s own {@code steerToward} spring-toward-point
 * formula (duplicated here rather than extracted into a shared utility - matches this port's own existing
 * precedent of not sharing this one small helper across unrelated mobs).
 *
 * <p>Real, size-based touch attack: a 10% chance per swing to detonate (radius 1.5, no block damage) and
 * die instead of dealing normal melee damage - the original's own real "sometimes explodes on contact"
 * design. Fire-immune (set via the real {@code EntityType.Builder#fireImmune()} registration flag, not a
 * per-hit damage-source check) and immune to explosion damage specifically (its own detonation shouldn't
 * hurt itself via any incidental splash). No fall damage, no pressure-plate triggering, real drowning damage
 * while wet - all direct translations of the original's own overrides.
 *
 * <p>Real, documented cut: the original's own {@code damBonus} field was read/written to NBT but never
 * actually consulted anywhere in its own damage/attack logic - dead state even in the original, not ported.
 *
 * <p>Real, documented gap: no custom {@code EntityRenderer} exists here yet - unlike
 * {@code EntityThaumicSlime}/{@code EntityBrainyZombie}, this doesn't extend a vanilla type with a directly
 * reusable renderer ({@code BatModel} is hard-typed to vanilla {@link net.minecraft.world.entity.ambient.Bat}
 * specifically, not reusable for a {@code FlyingMob} without a parallel model), so this ships with the same
 * real "no renderer yet" gap {@code EntityWisp}/{@code EntityFluxRift} already carry rather than force a
 * mismatched reuse or a from-scratch model for this one mob right now.
 */
public class EntityFireBat extends FlyingMob implements Enemy {

	private static final EntityDataAccessor<Boolean> DATA_HANGING =
			SynchedEntityData.defineId(EntityFireBat.class, EntityDataSerializers.BOOLEAN);

	private BlockPos currentFlightTarget;
	private int attackTime;

	public EntityFireBat(EntityType<? extends EntityFireBat> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 5.0)
				.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HANGING, true);
	}

	@Override
	protected void registerGoals() {
		// intentionally empty - matches EntityWisp's own doc comment: every behaviour is hand-rolled in
		// aiStep(), there's no vanilla Goal for hanging or homing-in-and-exploding to replace.
	}

	public boolean isHanging() {
		return entityData.get(DATA_HANGING);
	}

	public void setHanging(boolean hanging) {
		entityData.set(DATA_HANGING, hanging);
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
		// no fall damage, matches the original's own no-op fall() override.
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (isInvulnerableTo(source) || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		if (!level().isClientSide && isHanging()) {
			setHanging(false);
		}
		return super.hurt(source, amount);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (isInWater()) {
			hurt(damageSources().drown(), 1.0f);
		}
		if (level().isClientSide) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (attackTime > 0) {
			attackTime--;
		}

		BlockPos below = blockPosition();
		BlockPos above = below.above();
		if (isHanging()) {
			setDeltaMovement(Vec3.ZERO);
			setPosRaw(getX(), Mth.floor(getY()) + 1.0 - getBbHeight(), getZ());
			if (!level.getBlockState(above).isFaceSturdy(level, above, net.minecraft.core.Direction.DOWN)) {
				setHanging(false);
			} else {
				if (random.nextInt(200) == 0) {
					setYHeadRot(random.nextInt(360));
				}
				if (level.getNearestPlayer(this, 4.0) != null) {
					setHanging(false);
				}
			}
		} else if (getTarget() == null) {
			if (currentFlightTarget != null && (!level.isEmptyBlock(currentFlightTarget) || currentFlightTarget.getY() < 1)) {
				currentFlightTarget = null;
			}
			if (currentFlightTarget == null || random.nextInt(30) == 0 || distanceToSqr(centerOf(currentFlightTarget)) < 4.0) {
				currentFlightTarget = new BlockPos((int) getX() + random.nextInt(7) - random.nextInt(7),
						(int) getY() + random.nextInt(6) - 2, (int) getZ() + random.nextInt(7) - random.nextInt(7));
			}
			steerToward(currentFlightTarget.getX() + 0.5 - getX(), currentFlightTarget.getY() + 0.1 - getY(), currentFlightTarget.getZ() + 0.5 - getZ());
			if (random.nextInt(100) == 0 && level.getBlockState(above).isFaceSturdy(level, above, net.minecraft.core.Direction.DOWN)) {
				setHanging(true);
			}
		} else {
			LivingEntity target = getTarget();
			steerToward(target.getX() - getX(), target.getY() + target.getEyeHeight() * 0.66 - getY(), target.getZ() - getZ());
		}

		if (getTarget() instanceof Player player && player.getAbilities().invulnerable) {
			setTarget(null);
		}
		if (getTarget() != null && !getTarget().isAlive()) {
			setTarget(null);
		}
		if (getTarget() == null) {
			setTarget(level.getNearestPlayer(this, 12.0));
		} else if (isAlive() && hasLineOfSight(getTarget())) {
			touchAttack(getTarget());
		}
	}

	/** Same real spring-toward-point formula {@link EntityWisp#steerToward} uses. */
	private void steerToward(double dx, double dy, double dz) {
		var motion = getDeltaMovement();
		double mx = motion.x + (Math.signum(dx) * 0.5 - motion.x) * 0.1;
		double my = motion.y + (Math.signum(dy) * 0.7 - motion.y) * 0.1;
		double mz = motion.z + (Math.signum(dz) * 0.5 - motion.z) * 0.1;
		setDeltaMovement(mx, my, mz);
		float yaw = (float) (Math.atan2(mz, mx) * 180.0 / Math.PI) - 90.0f;
		setYRot(getYRot() + Mth.wrapDegrees(yaw - getYRot()));
	}

	private Vec3 centerOf(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	private void touchAttack(LivingEntity target) {
		if (attackTime > 0 || distanceTo(target) >= Math.max(2.5f, target.getBbWidth() * 1.1f)) {
			return;
		}
		if (target.getBoundingBox().maxY <= getBoundingBox().minY || target.getBoundingBox().minY >= getBoundingBox().maxY) {
			return;
		}
		attackTime = 20 + random.nextInt(20);
		if (random.nextInt(10) == 0) {
			ServerLevel level = (ServerLevel) level();
			level.explode(this, getX(), getY(), getZ(), 1.5f, false, Level.ExplosionInteraction.NONE);
			discard();
			return;
		}
		playSound(SoundEvents.BAT_HURT, 0.5f, 0.9f + random.nextFloat() * 0.2f);
		doHurtTarget(target);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return isHanging() && random.nextInt(4) != 0 ? null : SoundEvents.BAT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.BAT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BAT_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.1f;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		spawnAtLocation(Items.GUNPOWDER);
	}

	/** Real darkness-based spawn check (same {@code LightLayer.BLOCK} query {@link EntityWisp#isValidLightLevel}
	 * already established, simpler here since the original only ever checked block light, not sky light too)
	 * - kept ready even though this mob isn't wired into any spawn-event table yet, matching
	 * {@code EntityWisp#canSpawnHere}'s own "real but unregistered" precedent. */
	public boolean canSpawnHere() {
		return level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, blockPosition()) <= random.nextInt(7);
	}
}

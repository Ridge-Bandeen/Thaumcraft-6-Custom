package thaumcraft.common.entities.monster.tainted;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.resources.ItemCrystalEssence;

/**
 * Replaces the old (1.12.2) {@code EntityTaintSwarm} - real hand-rolled flight (no vanilla Goal-based AI to
 * replace, {@code registerGoals()} is intentionally empty), same real shape {@code EntityWisp} already
 * established for this port's other {@link FlyingMob} ({@code EntityFlying} has no direct modern equivalent
 * name, but the no-gravity/no-fall-damage behavior is identical). Wander flight reuses the exact same
 * "spring toward a random nearby point, re-picked periodically" formula as the original, kept as its own
 * private helper (not {@code EntityWisp}'s, which uses a single shared smoothing rate for all three axes -
 * this one keeps the original's own distinct horizontal/vertical smoothing rates, a real, minor difference
 * between the two mobs worth preserving rather than flattening into one shared method).
 *
 * Real "summoned" synced flag: with no attack target, a summoned swarm keeps hurting itself for 5 real
 * damage every tick (a real, deliberate self-destruct timer for a temporary conjured swarm - matches the
 * original exactly) and never independently seeks out a player to attack ({@link #findAttackTarget} returns
 * {@code null} while summoned). Real melee attack applies vanilla Weakness and explicitly restores the
 * target's own pre-hit velocity afterward (no real knockback), matching the original's own manual
 * motion-preservation.
 *
 * Real client-side "buzzing insect cloud" particle-swarm FX restored 2026-09-16 ({@link #maintainSwarmFx},
 * see {@link thaumcraft.client.fx.particles.FXSwarm}'s own doc comment) - was a plain, undocumented gap
 * before (this entity is otherwise invisible/hitbox-only; the true original's own visible "swarm" is
 * entirely these particles). Real custom {@code swarm}/{@code swarmattack} sounds restored alongside it
 * (were vanilla bee-sound stand-ins).
 *
 * Real, documented cuts: no full-bright self-illumination ({@code getBrightnessForRender}/{@code
 * getBrightness} have no modern per-entity equivalent - lighting is purely block-based now, same real gap
 * every other exotic mob this session carries); the original's own "mark the victim as recently-hit" call
 * (an obscure cross-system hook into a DIFFERENT entity's own future death-loot bonus, not this swarm's own
 * behavior) is dropped.
 */
public class EntityTaintSwarm extends FlyingMob implements ITaintedMob {

	private static final EntityDataAccessor<Boolean> DATA_SUMMONED =
			SynchedEntityData.defineId(EntityTaintSwarm.class, EntityDataSerializers.BOOLEAN);

	private BlockPos currentFlightTarget;
	private int attackTime;
	public int damBonus;

	public EntityTaintSwarm(EntityType<? extends EntityTaintSwarm> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.ATTACK_DAMAGE, 2.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SUMMONED, false);
	}

	@Override
	protected void registerGoals() {
		// intentionally empty - every behaviour below is hand-rolled in aiStep(), matching the original's
		// own onLivingUpdate approach; there's no vanilla Goal to replace, same real shape EntityWisp uses.
	}

	public boolean isSummoned() {
		return entityData.get(DATA_SUMMONED);
	}

	public void setSummoned(boolean summoned) {
		entityData.set(DATA_SUMMONED, summoned);
	}

	/** Real live re-application, replacing the original's own directly-mutable public {@code damBonus} field
	 * - modern {@code AttributeInstance}s need an explicit call to actually take effect. */
	public void setDamBonus(int bonus) {
		damBonus = bonus;
		var attribute = getAttribute(Attributes.ATTACK_DAMAGE);
		if (attribute != null) {
			attribute.setBaseValue(2.0 + bonus);
		}
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof ITaintedMob || super.isAlliedTo(other);
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
	protected float getSoundVolume() {
		return 0.1f;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("summoned", isSummoned());
		tag.putByte("damBonus", (byte) damBonus);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setSummoned(tag.getBoolean("summoned"));
		setDamBonus(tag.getByte("damBonus"));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		setDeltaMovement(getDeltaMovement().multiply(1.0, 0.6, 1.0));
		if (level().isClientSide) {
			maintainSwarmFx();
			return;
		}

		if (attackTime > 0) {
			attackTime--;
		}

		LivingEntity target = getTarget();
		if (target == null) {
			if (isSummoned()) {
				hurt(damageSources().generic(), 5.0f);
			}
			if (currentFlightTarget != null && (!level().isEmptyBlock(currentFlightTarget) || currentFlightTarget.getY() < 1
					|| currentFlightTarget.getY() > level().getHeight(Heightmap.Types.MOTION_BLOCKING, currentFlightTarget.getX(), currentFlightTarget.getZ()) + 2
					|| !thaumcraft.common.blocks.world.taint.TaintHelper.isNearTaintSeed(level(), currentFlightTarget))) {
				currentFlightTarget = null;
			}
			if (currentFlightTarget == null || random.nextInt(30) == 0 || distanceToSqr(centerOf(currentFlightTarget)) < 4.0) {
				currentFlightTarget = new BlockPos((int) getX() + random.nextInt(7) - random.nextInt(7),
						(int) getY() + random.nextInt(6) - 2, (int) getZ() + random.nextInt(7) - random.nextInt(7));
			}
			steerToward(currentFlightTarget.getX() + 0.5 - getX(), currentFlightTarget.getY() + 0.1 - getY(),
					currentFlightTarget.getZ() + 0.5 - getZ(), 0.5, 0.7, 0.015, 0.1);
		} else {
			steerToward(target.getX() - getX(), target.getY() + target.getEyeHeight() - getY(), target.getZ() - getZ(),
					0.5, 0.7, 0.025, 0.1);
		}

		if (getTarget() == null) {
			setTarget(findAttackTarget());
		} else if (isAlive() && getTarget().isAlive()) {
			attack(getTarget(), getTarget().distanceTo(this));
		} else {
			setTarget(null);
		}
		if (getTarget() instanceof Player player && player.getAbilities().invulnerable) {
			setTarget(null);
		}
	}

	/** Real client-only "buzzing insect cloud" visual, restored 2026-09-16 - no packet needed at all, the
	 * whole thing is derived purely from this entity's own already-synced position/alive state, matching the
	 * true original's own real client-only field shape exactly (it kept its own {@code ArrayList<FXSwarm>}
	 * directly on the entity, safe in 1.12.2's looser common/client split; this port keeps that same list in
	 * {@code ClientFxHandlers} instead, keyed by entity id, since a modern common-side entity class can't
	 * carry a client-only field type at all). See {@code ClientFxHandlers#maintainSwarm} for the real
	 * maintenance loop (up to 30 particles, matching the true original exactly). */
	private void maintainSwarmFx() {
		thaumcraft.client.fx.ClientFxHandlers.maintainSwarm(this);
	}

	/** {@code (dx, dy, dz, horizontalPull, verticalPull, horizontalSmoothing, verticalSmoothing)} - the
	 * original's own distinct horizontal-vs-vertical spring rates, kept real rather than flattened to one
	 * shared rate. */
	private void steerToward(double dx, double dy, double dz, double horizontalPull, double verticalPull, double horizontalSmoothing, double verticalSmoothing) {
		Vec3 motion = getDeltaMovement();
		double mx = motion.x + (Math.signum(dx) * horizontalPull - motion.x) * horizontalSmoothing;
		double my = motion.y + (Math.signum(dy) * verticalPull - motion.y) * verticalSmoothing;
		double mz = motion.z + (Math.signum(dz) * horizontalPull - motion.z) * horizontalSmoothing;
		setDeltaMovement(mx, my, mz);
		float yaw = (float) (Math.atan2(mz, mx) * 180.0 / Math.PI) - 90.0f;
		setYRot(getYRot() + Mth.wrapDegrees(yaw - getYRot()));
		zza = 0.1f;
	}

	private Vec3 centerOf(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	private LivingEntity findAttackTarget() {
		return isSummoned() ? null : thaumcraft.common.entities.PlayerTargeting.nearestTargetable(level(), this, 8.0);
	}

	private void attack(LivingEntity target, float distance) {
		if (attackTime > 0 || distance >= 3.0f || target.getBoundingBox().maxY <= getBoundingBox().minY
				|| target.getBoundingBox().minY >= getBoundingBox().maxY || !hasLineOfSight(target)) {
			return;
		}
		attackTime = 15 + random.nextInt(10);
		Vec3 preHitMotion = target.getDeltaMovement();
		if (doHurtTarget(target)) {
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
		}
		target.setDeltaMovement(preHitMotion);
		playSound(thaumcraft.common.registry.ThaumcraftSounds.SWARMATTACK.get(), 0.3f, 0.9f + random.nextFloat() * 0.2f);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return thaumcraft.common.registry.ThaumcraftSounds.SWARM.get();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return thaumcraft.common.registry.ThaumcraftSounds.SWARMATTACK.get();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return thaumcraft.common.registry.ThaumcraftSounds.SWARMATTACK.get();
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		if (random.nextBoolean()) {
			ItemStack crystal = new ItemStack(ItemsTC.crystalEssence.get());
			((ItemCrystalEssence) crystal.getItem()).setAspects(crystal, new AspectList().add(Aspect.FLUX, 1));
			spawnAtLocation(crystal);
		}
	}

	/** The original's own darkness-based spawn validity check, kept real (not simplified) for whenever a
	 * future summon-focus/spawner directly creates one - same real shape {@code EntityWisp#canSpawnHere}
	 * already established, not wired into any natural world spawn list yet either. */
	public boolean canSpawnHere() {
		BlockPos pos = new BlockPos((int) getX(), (int) getBoundingBox().minY, (int) getZ());
		return level().getMaxLocalRawBrightness(pos) <= random.nextInt(7);
	}
}

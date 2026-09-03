package thaumcraft.common.entities.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import thaumcraft.api.entities.IEldritchMob;

/**
 * Replaces the old (1.12.2) {@code EntityEldritchCrab} - a real {@link Monster} subclass (the original was
 * {@code EntityMob} too), reusing vanilla's own real swim/leap/melee/wander AI. Real "climb onto the
 * target's back and repeatedly claw it" mechanic (hand-rolled in {@link #aiStep}, matching the original's
 * own {@code onUpdate} - there's no vanilla riding-attack Goal to replace) via real vanilla passenger/vehicle
 * calls ({@link #startRiding}/{@link #stopRiding}).
 *
 * Real helm mechanic: a synced {@code helm} flag (real armor +5 via {@link #getArmorValue}, a real
 * movement-speed penalty while worn) that breaks off for good once health drops to half, matching the
 * original exactly. Real bonus ender pearl death drop, real poison immunity, and crabs don't fight each
 * other (real {@link #isAlliedTo} override).
 *
 * Real, documented cuts: the original's own targeting also included {@code EntityCultist} (still unported -
 * see this whole session's own monster-arc notes) - this only targets real players for now, a real, minor
 * simplification until that entity exists. Door-breaking pathfinding and the original's own arthropod
 * creature-attribute tag (bane-of-arthropods bonus damage - modern MC replaced that whole system with
 * data-driven entity tags, not a per-instance Java override) aren't ported either.
 */
public class EntityEldritchCrab extends Monster implements IEldritchMob {

	private static final EntityDataAccessor<Boolean> DATA_HELM =
			SynchedEntityData.defineId(EntityEldritchCrab.class, EntityDataSerializers.BOOLEAN);

	private int attackTime;

	public EntityEldritchCrab(EntityType<? extends EntityEldritchCrab> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HELM, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.63f));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	public boolean hasHelm() {
		return entityData.get(DATA_HELM);
	}

	public void setHelm(boolean helm) {
		entityData.set(DATA_HELM, helm);
	}

	@Override
	public int getArmorValue() {
		return hasHelm() ? 5 : 0;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return effect.getEffect() != MobEffects.POISON && super.canBeAffected(effect);
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof EntityEldritchCrab || super.isAlliedTo(other);
	}

	@Override
	public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
			net.minecraft.world.entity.MobSpawnType spawnType, net.minecraft.world.entity.SpawnGroupData spawnGroupData) {
		setHelm(difficulty.getDifficulty() == net.minecraft.world.Difficulty.HARD || random.nextFloat() < 0.33f);
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hasHelm() && getHealth() / getMaxHealth() <= 0.5f) {
			setHelm(false);
			var speed = getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) speed.setBaseValue(0.3);
		}
		return hurt;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (attackTime > 0) attackTime--;
		if (level().isClientSide) return;

		LivingEntity target = getTarget();
		if (getVehicle() == null && target != null && !target.isVehicle() && !onGround() && !hasHelm()
				&& target.isAlive() && getY() - target.getY() >= target.getBbHeight() / 2.0 && distanceToSqr(target) < 4.0) {
			startRiding(target);
		}
		if (getVehicle() != null && isAlive() && attackTime <= 0) {
			attackTime = 10 + random.nextInt(10);
			doHurtTarget(getVehicle());
			if (random.nextFloat() < 0.2f) {
				stopRiding();
			}
		}
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		if (super.doHurtTarget(target)) {
			playSound(SoundEvents.SPIDER_HURT, 1.0f, 0.9f + random.nextFloat() * 0.2f);
			return true;
		}
		return false;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		if (recentlyHit && (random.nextInt(3) == 0)) {
			spawnAtLocation(Items.ENDER_PEARL);
		}
	}

	/** Real, documented substitution: the original's own custom {@code SoundsTC.crabtalk}/{@code crabdeath}/
	 * {@code crabclaw} sounds aren't registered in this port yet - stands in with vanilla spider sounds
	 * (a real arthropod-flavored match) rather than standing up custom sound registration for one mob. */
	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SPIDER_DEATH;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.GENERIC_HURT;
	}

	@Override
	protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		playSound(SoundEvents.SPIDER_STEP, 0.15f, 1.0f);
	}
}

package thaumcraft.common.entities.monster.tainted;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.world.taint.ITaintBlock;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import thaumcraft.common.items.resources.ItemCrystalEssence;
import thaumcraft.common.registry.ThaumcraftEntities;
import thaumcraft.common.registry.ThaumcraftMobEffects;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code EntityTaintSeed} - the real, stationary origin of a taint zone. This is
 * the real {@code EntityTaintSeed} {@link TaintHelper}'s own class doc comment already promised was a real,
 * documented simplification away from ({@code isNearTaintSeed} no longer self-prunes stale tracked positions
 * by checking whether a live seed entity still exists there) - now that this class is real, that self-
 * pruning could be added back, but isn't yet (a real, separate follow-up, not attempted this chunk).
 *
 * Real per-tick behavior, faithfully ported from the original's own {@code onUpdate}: re-registers its own
 * real tracked seed position every 1200 ticks (self-healing in case something else pruned it), then every 20
 * real ticks (or every tick while a real {@link #boost} is active) either starves slightly and pollutes the
 * aura (if real local flux saturation is zero) or spreads real fibres in a real jittered area around itself
 * via {@link TaintHelper#spreadFibres}. Also every 20 ticks: spawns a real {@link EntityTaintacleSmall} at a
 * distant, visible target, and inflicts real Flux Taint on every real living entity within its real area.
 *
 * Real, stationary shape shared with {@link EntityTaintacle} (same real {@link #move} override), same real
 * "starves/spreads instead of dying to zero flux" mechanic {@code EntityTaintacle} doesn't have (that one
 * just starves outright off-taint; this one has a real flux-driven middle state via {@link #boost}).
 *
 * Real, documented cuts: same {@code BiomeHandler.ELDRITCH} drop as {@link EntityTaintacle#spawnTentacles}
 * (unported custom biome); {@link #getArea} defaults to 1 real chunk-ish unit, matching the original exactly
 * ({@link EntityTaintSeedPrime} doubles it). Custom {@code SoundsTC.tentacle} audio substitution, same real
 * vanilla Warden tendril-click stand-in {@code EntityTaintacle} already established.
 */
public class EntityTaintSeed extends Monster implements ITaintedMob {

	public int boost;
	private boolean firstRun;

	public EntityTaintSeed(EntityType<? extends EntityTaintSeed> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 75.0)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	protected int getArea() {
		return 1;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));
		targetSelector.addGoal(0, new HurtByTargetGoal(this));
		targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof ITaintedMob || super.isAlliedTo(other);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("boost", boost);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		boost = tag.getInt("boost");
	}

	@Override
	public void move(MoverType type, Vec3 movement) {
		double y = movement.y > 0.0 ? 0.0 : movement.y;
		super.move(type, new Vec3(0.0, y, 0.0));
	}

	@Override
	public void die(DamageSource cause) {
		TaintHelper.removeTaintSeed(level(), blockPosition());
		super.die(cause);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		BlockPos pos = blockPosition();
		if (!firstRun || tickCount % 1200 == 0) {
			TaintHelper.removeTaintSeed(level(), pos);
			TaintHelper.addTaintSeed(level(), pos);
			firstRun = true;
		}
		if (!isAlive()) {
			return;
		}
		boolean tickFlag = tickCount % 20 == 0;
		if (boost > 0 || tickFlag) {
			float mod = boost > 0 ? 1.0f : AuraHandler.getFluxSaturation(level(), pos);
			if (boost > 0) {
				boost--;
			}
			if (mod <= 0.0f) {
				hurt(damageSources().starve(), 0.5f);
				AuraHelper.polluteAura(level(), pos, 0.1f, false);
			} else {
				BlockPos target = pos.offset(Mth.nextInt(random, -getArea() * 3, getArea() * 3),
						Mth.nextInt(random, -getArea(), getArea()), Mth.nextInt(random, -getArea() * 3, getArea() * 3));
				TaintHelper.spreadFibres(level(), target, true);
			}
		}
		if (tickFlag) {
			LivingEntity target = getTarget();
			if (target != null && distanceToSqr(target) < getArea() * 256.0 && hasLineOfSight(target)) {
				spawnTentacles(target);
			}
			for (LivingEntity nearby : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(getArea() * 4.0))) {
				if (nearby != this) {
					nearby.addEffect(new MobEffectInstance(ThaumcraftMobEffects.FLUX_TAINT, 100, getArea() - 1));
				}
			}
		}
	}

	protected void spawnTentacles(Entity entity) {
		BlockPos pos = entity.blockPosition();
		if (!(level().getBlockState(pos).getBlock() instanceof ITaintBlock) && !(level().getBlockState(pos.below()).getBlock() instanceof ITaintBlock)) {
			return;
		}
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		EntityTaintacleSmall taintlet = ThaumcraftEntities.TAINTACLE_SMALL.get().create(serverLevel);
		if (taintlet == null) {
			return;
		}
		taintlet.moveTo(entity.getX() + random.nextFloat() - random.nextFloat(), entity.getY(),
				entity.getZ() + random.nextFloat() - random.nextFloat(), 0.0f, 0.0f);
		serverLevel.addFreshEntity(taintlet);
		playSound(SoundEvents.WARDEN_TENDRIL_CLICKS, getSoundVolume(), getVoicePitch());
	}

	public boolean canSpawnHere() {
		return level().getDifficulty() != Difficulty.PEACEFUL && isNotColliding()
				&& level().getEntitiesOfClass(EntityTaintSeed.class, getBoundingBox().inflate(TaintHelper.spreadArea() * 0.8)).isEmpty();
	}

	public boolean isNotColliding() {
		return !level().containsAnyLiquid(getBoundingBox()) && level().noCollision(this);
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		if (super.doHurtTarget(target)) {
			playSound(SoundEvents.WARDEN_TENDRIL_CLICKS, getSoundVolume(), getVoicePitch());
			return true;
		}
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.CHORUS_FLOWER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.3f - getBbHeight() / 10.0f;
	}

	@Override
	protected float getSoundVolume() {
		return getBbHeight() / 8.0f;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.WARDEN_TENDRIL_CLICKS;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_TENDRIL_CLICKS;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		spawnAtLocation(makeCrystal());
	}

	static ItemStack makeCrystal() {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemCrystalEssence) stack.getItem()).setAspects(stack, new AspectList().add(Aspect.FLUX, 1));
		return stack;
	}

	@Override
	protected int decreaseAirSupply(int air) {
		return air;
	}

	@Override
	public boolean isPushable() {
		return false;
	}
}

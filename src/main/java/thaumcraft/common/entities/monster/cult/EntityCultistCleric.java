package thaumcraft.common.entities.monster.cult;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.projectile.EntityGolemOrb;

/**
 * Replaces the old (1.12.2) {@code EntityCultistCleric} - the Crimson Cult's ranged/ritualist rank. Real
 * {@link RangedAttackMob} implementer using vanilla's own {@link RangedAttackGoal} (replacing the
 * original's custom {@code AILongRangeAttack}/1.12 {@code EntityAIAttackRanged} - one real, minor
 * simplification: vanilla's version has no minimum-engagement-distance gate, so this cleric will fire from
 * point-blank range too, unlike the original's own {@code min=2.0} tuning). Real alt-attack roll on each
 * shot, matching the original exactly: ~34% chance of a homing {@link EntityGolemOrb} "blast", ~66% chance
 * of a 3-fireball {@link SmallFireball} burst.
 *
 * Real Crimson Robe equipment (always worn) and a real 30%(hard)/10%(normal) chance of Crimson Boots,
 * matching the original's own {@code setLoot} odds.
 *
 * **Real, working ritualist/altar-focus mechanic** - the original's own version of this was a genuine bug
 * in the shipped 1.12.2 mod (see {@link BlocksTC#cultistRitualAltar}'s own doc comment: the block field it
 * checked was never assigned, so the state always canceled itself within ~2 seconds regardless of any real
 * altar). This port fixes that for real: a synced {@code ritualist} flag that, while true, prevents this
 * cleric from naturally despawning ({@link #removeWhenFarAway}) and gets cancelled for real once every 40
 * ticks if it's more than 4 blocks from its own home position or that position no longer holds a real
 * {@link BlocksTC#cultistRitualAltar} block - a faithful, now-functional port of {@code AIAltarFocus}'s own
 * distance/block check. The original's own {@code rage} counter (a second, alternate cancel condition -
 * {@code rage >= 5}) is ported too, but nothing in the original's own {@code EntityCultistCleric.java}
 * itself ever increments it, and no other original source file this port has read so far sets {@code
 * ritualist} true either - both are real, pre-existing gaps in the original's own broader ritual/summoning
 * system (likely a separate, still-unported structure or event), not something this port can respond to yet.
 * {@link #setRitualist} is real and public, ready for whatever real trigger surfaces when that system is
 * ported.
 *
 * As the original: the ritualist flag is saved ({@code ritualist}), so a ritual cleric can come from saved or summon
 * data; the home travels in the spawn data so the client knows where the altar is; and on the client a ritualist keeps
 * its head turned to the altar (home + 0.5, 1.5, 0.5) while {@link thaumcraft.client.render.RenderCultistKnight}
 * bobs it and draws its ritual lines there.
 */
public class EntityCultistCleric extends EntityCultist implements RangedAttackMob, net.neoforged.neoforge.entity.IEntityWithComplexSpawn {

	private static final EntityDataAccessor<Boolean> DATA_RITUALIST =
			SynchedEntityData.defineId(EntityCultistCleric.class, EntityDataSerializers.BOOLEAN);

	public int rage;

	public EntityCultistCleric(EntityType<? extends EntityCultistCleric> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityCultist.createAttributes().add(Attributes.MAX_HEALTH, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_RITUALIST, false);
	}

	public boolean getIsRitualist() {
		return entityData.get(DATA_RITUALIST);
	}

	public void setRitualist(boolean ritualist) {
		entityData.set(DATA_RITUALIST, ritualist);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new AltarFocusGoal(this));
		goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 20, 40, 24.0f));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(4, new OpenDoorGoal(this, true));
		goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.8));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractIllager.class, true));
	}

	@Override
	public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("ritualist", getIsRitualist());
	}

	@Override
	public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setRitualist(tag.getBoolean("ritualist"));
	}

	@Override
	public void writeSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
		buffer.writeBlockPos(getRestrictCenter());
	}

	@Override
	public void readSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
		restrictTo(buffer.readBlockPos(), 8);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide && getIsRitualist()) {
			// the original's client-side gaze at the altar
			BlockPos home = getRestrictCenter();
			double dx = home.getX() + 0.5 - getX();
			double dy = home.getY() + 1.5 - (getY() + getEyeHeight());
			double dz = home.getZ() + 0.5 - getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
			float pitch = (float) -(Math.atan2(dy, horizontal) * 180.0 / Math.PI);
			setXRot(turnTowards(getXRot(), pitch, 10.0f));
			setYHeadRot(turnTowards(getYHeadRot(), yaw, getMaxHeadXRot()));
		}
	}

	private static float turnTowards(float from, float to, float maxStep) {
		return from + net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(to - from), -maxStep, maxStep);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return !getIsRitualist() && super.removeWhenFarAway(distanceSquared);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!level().isClientSide && getIsRitualist() && rage >= 5) {
			setRitualist(false);
		}
	}

	@Override
	public void performRangedAttack(LivingEntity target, float distanceFactor) {
		swing(InteractionHand.MAIN_HAND);
		if (random.nextFloat() > 0.66f) {
			EntityGolemOrb blast = new EntityGolemOrb(this, target, true);
			Vec3 dir = target.position().add(0, target.getEyeHeight() * 0.6, 0)
					.subtract(position().add(0, getEyeHeight() * 0.6, 0)).normalize();
			blast.shoot(dir.x, dir.y, dir.z, 0.66f, 3.0f);
			playSound(thaumcraft.common.registry.ThaumcraftSounds.EGATTACK.get(), 1.0f, 1.0f + random.nextFloat() * 0.1f);
			level().addFreshEntity(blast);
		}
		else {
			// as the original: the fire-extinguish fizz with the fireballs
			level().levelEvent(null, net.minecraft.world.level.block.LevelEvent.SOUND_EXTINGUISH_FIRE, blockPosition(), 0);
			double dx = target.getX() - getX();
			double dy = target.getBoundingBox().minY + target.getBbHeight() / 2.0 - (getY() + getEyeHeight());
			double dz = target.getZ() - getZ();
			float spread = (float) Math.sqrt(distanceFactor) * 0.5f;
			for (int i = 0; i < 3; i++) {
				Vec3 dir = new Vec3(dx + random.nextGaussian() * spread, dy, dz + random.nextGaussian() * spread).normalize();
				SmallFireball fireball = new SmallFireball(level(), this, dir);
				fireball.setPos(getX(), getY() + getEyeHeight() * 0.5 + 0.5, getZ());
				level().addFreshEntity(fireball);
			}
		}
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
		setLoot(difficulty);
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	private void setLoot(DifficultyInstance diff) {
		RandomSource rand = random;
		boolean hard = diff.getDifficulty() == Difficulty.HARD;
		setItemSlot(EquipmentSlot.HEAD, new ItemStack(ItemsTC.crimsonRobeHelm.get()));
		setItemSlot(EquipmentSlot.CHEST, new ItemStack(ItemsTC.crimsonRobeChest.get()));
		setItemSlot(EquipmentSlot.LEGS, new ItemStack(ItemsTC.crimsonRobeLegs.get()));
		if (rand.nextFloat() < (hard ? 0.3f : 0.1f)) {
			setItemSlot(EquipmentSlot.FEET, new ItemStack(ItemsTC.crimsonBoots.get()));
		}
	}

	@Override
	protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		spawnAtLocation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, random.nextInt(3)));
	}

	/**
	 * Replaces the original's {@code AIAltarFocus} - now a real, functional check (see this class's own doc
	 * comment for why the original's version wasn't). Real {@code Goal.Flag}-based mutex (blocks movement/
	 * look/jump while ritualist, matching the original's {@code setMutexBits(7)}).
	 */
	private static class AltarFocusGoal extends Goal {

		private final EntityCultistCleric cleric;

		AltarFocusGoal(EntityCultistCleric cleric) {
			this.cleric = cleric;
			setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return cleric.getIsRitualist() && cleric.hasRestriction();
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void tick() {
			if (cleric.tickCount % 40 == 0) {
				cleric.checkAltarFocus();
			}
		}
	}

	/**
	 * The real once-per-40-ticks distance/block check {@link AltarFocusGoal} runs - pulled out to its own
	 * public method (rather than left inline in that private goal class) so it's directly callable both from
	 * the goal itself and from a test, without needing to drive the whole goal-selector timing to exercise
	 * it. Real, faithful port of the original's own {@code AIAltarFocus#updateTask} check.
	 */
	public void checkAltarFocus() {
		if (!hasRestriction()) {
			return;
		}
		BlockPos home = getRestrictCenter();
		boolean tooFar = home.distSqr(blockPosition()) > 16.0;
		boolean altarGone = !level().getBlockState(home).is(BlocksTC.cultistRitualAltar.get());
		if (tooFar || altarGone) {
			setRitualist(false);
		}
	}
}

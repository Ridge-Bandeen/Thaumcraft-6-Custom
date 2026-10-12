package thaumcraft.common.entities.monster.tainted;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.world.taint.BlockTaintFibre;
import thaumcraft.common.blocks.world.taint.ITaintBlock;
import thaumcraft.common.blocks.world.taint.TaintHelper;
import thaumcraft.common.items.resources.ItemCrystalEssence;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * Replaces the old (1.12.2) {@code EntityTaintCrawler} - a real {@link Monster} subclass (the original was
 * {@code EntityMob} too), reusing vanilla's own real swim/melee/wander AI. Real "grows a real taint fibre
 * under its own feet" mechanic every 40 ticks via {@link #tick} - a faithful port of the original's own
 * {@code onUpdate}, minus one real, harmless dead check: the original's own {@code lastPos != getPosition()}
 * guard compared two freshly-constructed {@code BlockPos} objects by reference, not value, so it was always
 * true and never actually filtered anything - dropped rather than faithfully reproducing a no-op bug.
 *
 * Real vanilla silverfish sounds/model/texture reuse throughout - unlike most vanilla-substitute sound
 * choices elsewhere in this port, this one is faithful to the original itself (it always used {@code
 * SoundEvents.ENTITY_SILVERFISH_*} and a bare {@code ModelSilverfish}, not a stand-in for something
 * unported). See {@link thaumcraft.client.render.RenderTaintCrawler} for the renderer half.
 *
 * Real, documented cuts: {@code die()}'s own {@code fluxGoo}-adjacent "not already taint" check now uses
 * {@link ITaintBlock} (a real modern marker every block in this arc implements) in place of the original's
 * own {@code Material} comparison - not a behavior change, {@code ThaumcraftMaterials.MATERIAL_TAINT} was
 * only ever assigned to these same blocks. No custom "gore"/pitch-tuning beyond {@link #getVoicePitch} - the
 * original's own {@code canTriggerWalking() == false} has no modern override point at all (confirmed: {@code
 * LivingEntity} carries no equivalent hook any more), so it's dropped; {@link #playStepSound} alone already
 * gives the real quiet silverfish footstep.
 */
public class EntityTaintCrawler extends Monster implements ITaintedMob {

	public EntityTaintCrawler(EntityType<? extends EntityTaintCrawler> type, Level level) {
		super(type, level);
		xpReward = 3; // as the original
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 8.0)
				.add(Attributes.MOVEMENT_SPEED, 0.275)
				.add(Attributes.ATTACK_DAMAGE, 2.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof ITaintedMob || super.isAlliedTo(other);
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).withEyeHeight(0.1f);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide && isAlive() && tickCount % 40 == 0) {
			BlockPos pos = blockPosition();
			BlockState bs = level().getBlockState(pos);
			boolean eligible = bs.isAir() || bs.canBeReplaced() || bs.getBlock() instanceof SaplingBlock || bs.getBlock() instanceof BushBlock;
			if (eligible && !bs.is(BlockTags.LEAVES) && !bs.liquid() && !(bs.getBlock() instanceof ITaintBlock)
					&& TaintHelper.isAdjacentToSolidBlock(level(), pos) && !BlockTaintFibre.isOnlyAdjacentToTaint(level(), pos)) {
				BlockState fibre = BlockTaintFibre.computeState(BlocksTC.taintFibre.get().defaultBlockState(), level(), pos);
				if (thaumcraft.api.events.TaintSpreadEvent.allows(level(), pos, pos, fibre)) {
					level().setBlockAndUpdate(pos, fibre);
				}
			}
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		if (random.nextInt(8) == 0) {
			ItemStack crystal = new ItemStack(ItemsTC.crystalEssence.get());
			((ItemCrystalEssence) crystal.getItem()).setAspects(crystal, new AspectList().add(Aspect.FLUX, 1));
			spawnAtLocation(crystal);
		}
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		if (super.doHurtTarget(target)) {
			if (target instanceof LivingEntity living) {
				int tier = switch (level().getDifficulty()) {
					case NORMAL -> 3;
					case HARD -> 6;
					default -> 0;
				};
				if (tier > 0 && random.nextInt(tier + 1) > 2) {
					living.addEffect(new MobEffectInstance(ThaumcraftMobEffects.FLUX_TAINT, tier * 20, 0));
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public float getVoicePitch() {
		return 0.7f;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.SILVERFISH_STEP, 0.15f, 1.0f);
	}
}

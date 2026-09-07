package thaumcraft.common.entities.monster;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.projectile.EntityEldritchOrb;

/**
 * Replaces the old (1.12.2) {@code EntityEldritchGuardian} - the Eldritch dimension's mid-tier mob, not a
 * full boss (no boss bar - see {@link thaumcraft.common.entities.monster.boss.EntityEldritchWarden} for the
 * boss-bar version sharing this mob's ranged attack). Real {@link Monster} + {@link RangedAttackMob}, real
 * vanilla {@link RangedAttackGoal} (no minimum-engagement-distance gate - same simplification as {@link
 * thaumcraft.common.entities.monster.cult.EntityCultistCleric}'s own doc comment explains).
 *
 * Real ranged attack: fires a real {@link EntityEldritchOrb} most of the time (85% - matches the original's
 * own {@code rand.nextFloat() > 0.15f} branch), normalized straight-line aim at the target's predicted
 * position. Real melee-range "scream" fallback (the other 15%, or whenever line of sight allows it): applies
 * a real 20-second Wither II and adds real, permanent-track Warp via {@link ThaumcraftCapabilities#getWarp}
 * (the modern replacement for the original's {@code ThaumcraftApi.internalMethods.addWarpToPlayer}), matching
 * the original's own {@code attackEntityWithRangedAttack} exactly (same duration/amplifier, same 1-3 random
 * warp amount, same {@code TEMPORARY} warp type).
 *
 * Real half-damage resistance to magic damage (matches the original's own {@code attackEntityFrom} halving).
 * Real home-position restriction comes free from vanilla's own {@link Monster#restrictTo} (see {@code
 * EntityCultist}'s own doc comment for why the original needed to hand-roll this and this port doesn't).
 *
 * Real, documented cuts: no arm-lift swing animation ({@code armLiftL}/{@code armLiftR}, tied to the not-yet-
 * ported mesh - see {@link thaumcraft.client.render.ModelEldritchPlaceholder}'s own doc comment), no sonic-
 * scream client FX broadcast ({@code PacketFXSonic}, tied to the unported {@code FXDispatcher}), no per-
 * dimension absorption buff (the original's own Eldritch-dimension-only extra half-health shield via {@code
 * ModConfig.CONFIG_WORLD.dimensionOuterId} - that dimension isn't ported), no multiplayer aggro-scaling (same
 * cut as {@link thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss}'s own doc comment explains for
 * the same reason - the unported {@code EntityUtils}).
 */
public class EntityEldritchGuardian extends Monster implements RangedAttackMob, IEldritchMob {

	public EntityEldritchGuardian(EntityType<? extends EntityEldritchGuardian> type, Level level) {
		super(type, level);
		xpReward = 20;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 50.0)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.ATTACK_DAMAGE, 7.0)
				.add(Attributes.ARMOR, 4.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 20, 40, 24.0f));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, EntityCultist.class, true));
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof IEldritchMob || super.isAlliedTo(other);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
			amount /= 2.0f;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void performRangedAttack(LivingEntity target, float distanceFactor) {
		if (random.nextFloat() > 0.15f) {
			EntityEldritchOrb blast = new EntityEldritchOrb(this);
			double dx = target.getX() - getX();
			double dy = target.getBoundingBox().minY + target.getBbHeight() / 2.0 - (getY() + getEyeHeight() / 2.0) + 2.0;
			double dz = target.getZ() - getZ();
			var dir = new net.minecraft.world.phys.Vec3(dx, dy, dz).normalize();
			blast.shoot(dir.x, dir.y, dir.z, 1.1f, 2.0f);
			playSound(net.minecraft.sounds.SoundEvents.WITHER_SHOOT, 2.0f, 1.0f + random.nextFloat() * 0.1f);
			level().addFreshEntity(blast);
		} else if (hasLineOfSight(target)) {
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 1));
			if (target instanceof net.minecraft.server.level.ServerPlayer player) {
				ThaumcraftCapabilities.getWarp(player).add(IPlayerWarp.EnumWarpType.TEMPORARY, 1 + random.nextInt(3));
			}
			playSound(net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 1.0f + random.nextFloat() * 0.1f);
		}
	}
}

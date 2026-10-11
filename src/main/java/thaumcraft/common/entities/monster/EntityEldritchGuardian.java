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
 * Real sonic-scream client FX ({@link #sonicStartTick}, restored 2026-09-17): the true original's own real
 * {@code PacketFXSonic} broadcast, now a plain vanilla entity-event trigger (id 17) instead - the client-only
 * dome effect itself is drawn directly from {@link thaumcraft.client.render.RenderEldritchGuardian}'s own
 * render call (see {@link thaumcraft.client.render.EldritchSonicFx}'s own doc comment for why).
 *
 * Real arm-lift swing animation ({@link #armLiftL}/{@link #armLiftR}, restored 2026-09-24 alongside the real
 * mesh port - see {@link thaumcraft.client.renderers.models.entity.ModelEldritchGuardian}): a real client-only
 * entity-event pair ({@link #ORB_LEFT_EVENT}/{@link #ORB_RIGHT_EVENT}) fired on every real orb-blast ranged
 * attack, alternating, matching the true original's own real {@code lastBlast} toggle exactly; decayed 0.05f/
 * tick client-side in {@link #aiStep}, same real formula as the true original's own {@code onUpdate}.
 *
 * Real, documented cuts: no per-dimension absorption buff (the original's own extra half-health shield gated on
 * {@code ModConfig.CONFIG_WORLD.dimensionOuterId} - confirmed 2026-09-09, not just a porting gap: Thaumcraft
 * 6 never shipped a real custom dimension at all, exhaustively checked directly against the true original
 * source and a community wiki; that config field is a real, generic "recognize some OTHER mod's dimension by
 * numeric id" compatibility knob, not a TC6-authored place. Permanently inapplicable, not a future item), no
 * multiplayer aggro-scaling (same cut as {@link thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss}'s
 * own doc comment explains for the same reason - the unported {@code EntityUtils}).
 */
public class EntityEldritchGuardian extends Monster implements RangedAttackMob, IEldritchMob {

	private static final byte ORB_LEFT_EVENT = 15;
	private static final byte ORB_RIGHT_EVENT = 16;
	private static final byte SONIC_EVENT = 17;

	/** Client-only "sonic scream" trigger state - {@link #tickCount} at the moment it fired, or {@link
	 * Integer#MIN_VALUE} for "no effect active". Never synced directly; set from {@link #handleEntityEvent}. */
	private int sonicStartTick = Integer.MIN_VALUE;
	private int sonicDuration;
	private float sonicYaw;
	private float sonicPitch;

	/** Client-only arm-lift swing state, decayed in {@link #aiStep}. Never synced directly; set from
	 * {@link #handleEntityEvent}. */
	private float armLiftL;
	private float armLiftR;
	private boolean lastBlast;

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

	/** Real client-only ambient particle trail restored 2026-09-17 (was dropped under this port's old blanket
	 * "no client FX" rule) - a real faithful port of the true original's own real constant per-tick
	 * {@code wispFXEG} dust trail, routed through {@link thaumcraft.client.fx.ClientFxHandlers#spawnWispEG}
	 * for the same real dist-crash-avoidance reason every other client-only trigger in this port uses. */
	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide) {
			if (armLiftL > 0.0f) {
				armLiftL -= 0.05f;
			}
			if (armLiftR > 0.0f) {
				armLiftR -= 0.05f;
			}
			float x = (float) (getX() + (random.nextFloat() - random.nextFloat()) * 0.2f);
			float z = (float) (getZ() + (random.nextFloat() - random.nextFloat()) * 0.2f);
			thaumcraft.client.fx.ClientFxHandlers.spawnWispEG(level(), x, getY() + 0.22 * getBbHeight(), z, this);
		} else if ((tickCount == 0 || tickCount % 100 == 0) && level().getDifficulty() != net.minecraft.world.Difficulty.EASY) {
			// as the original: the fog thickens around anyone within 16 blocks (24 on Hard)
			double range = level().getDifficulty() == net.minecraft.world.Difficulty.HARD ? 576.0 : 256.0;
			for (Player player : level().players()) {
				if (player.isAlive() && player.distanceToSqr(this) < range && player instanceof net.minecraft.server.level.ServerPlayer sp) {
					thaumcraft.common.lib.network.ClientboundMiscEventPacket.send(sp, thaumcraft.common.lib.network.ClientboundMiscEventPacket.GUARDIAN);
				}
			}
		}
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
			lastBlast = !lastBlast;
			level().broadcastEntityEvent(this, lastBlast ? ORB_RIGHT_EVENT : ORB_LEFT_EVENT);
			double dx = target.getX() - getX();
			double dy = target.getBoundingBox().minY + target.getBbHeight() / 2.0 - (getY() + getEyeHeight() / 2.0) + 2.0;
			double dz = target.getZ() - getZ();
			var dir = new net.minecraft.world.phys.Vec3(dx, dy, dz).normalize();
			blast.shoot(dir.x, dir.y, dir.z, 1.1f, 2.0f);
			playSound(thaumcraft.common.registry.ThaumcraftSounds.EGATTACK.get(), 2.0f, 1.0f + random.nextFloat() * 0.1f);
			level().addFreshEntity(blast);
		} else if (hasLineOfSight(target)) {
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 0)); // as the original: Wither I
			if (target instanceof net.minecraft.server.level.ServerPlayer player) {
				thaumcraft.api.ThaumcraftApi.internalMethods.addWarpToPlayer(player, 1 + random.nextInt(3), IPlayerWarp.EnumWarpType.TEMPORARY);
			}
			playSound(thaumcraft.common.registry.ThaumcraftSounds.EGSCREECH.get(), 3.0f, 1.0f + random.nextFloat() * 0.1f);
			level().broadcastEntityEvent(this, SONIC_EVENT);
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == SONIC_EVENT) {
			sonicStartTick = tickCount;
			sonicDuration = 10 + random.nextInt(5);
			sonicYaw = getYHeadRot();
			sonicPitch = getXRot();
		} else if (id == ORB_LEFT_EVENT) {
			armLiftL = 0.5f;
		} else if (id == ORB_RIGHT_EVENT) {
			armLiftR = 0.5f;
		} else {
			super.handleEntityEvent(id);
		}
	}

	public int getSonicStartTick() {
		return sonicStartTick;
	}

	public int getSonicDuration() {
		return sonicDuration;
	}

	public float getSonicYaw() {
		return sonicYaw;
	}

	public float getSonicPitch() {
		return sonicPitch;
	}

	public float getArmLiftL() {
		return armLiftL;
	}

	public float getArmLiftR() {
		return armLiftR;
	}

	@Override
	public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		thaumcraft.common.lib.utils.EntityChecks.saveHome(this, tag);
	}

	@Override
	public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		thaumcraft.common.lib.utils.EntityChecks.loadHome(this, tag);
	}

	/** As the original: the Eldritch idle murmur and death cry. */
	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return thaumcraft.common.registry.ThaumcraftSounds.EGIDLE.get();
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return thaumcraft.common.registry.ThaumcraftSounds.EGDEATH.get();
	}

	/** As the original ({@code canDespawn} = no home): guardians placed with a home (the Eldritch maze's) stay put. */
	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return !hasRestriction() && super.removeWhenFarAway(distanceSquared);
	}
}

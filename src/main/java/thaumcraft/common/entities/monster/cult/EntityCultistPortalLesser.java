package thaumcraft.common.entities.monster.cult;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * Replaces the old (1.12.2) {@code EntityCultistPortalLesser} - an immobile "monolith" that periodically
 * conjures real {@link EntityCultistKnight}/{@link EntityCultistCleric} minions near itself once a player
 * gets close. Real {@link Monster} subclass (the original was {@code EntityMob} too), real {@link
 * #move}/AI no-ops (never moves, never runs vanilla AI - matches the original's own {@code move}/{@code
 * onLivingUpdate} overrides doing nothing).
 *
 * Real activation (a player within 32 blocks starts it), real periodic spawn cycle (every 50-99 ticks once
 * active, matching the original's own {@code stagecounter} range) that conjures a real cultist (67% Knight,
 * 33% Cleric, matching the original's own roll) capped at a real per-difficulty count of nearby {@link
 * EntityCultist}s (2/4/6 on easy/normal/hard, reduced by however many already exist within 32 blocks -
 * matches the original exactly), each spawn costing the portal 5-9 real self-damage. Real touch damage (4
 * indirect-magic damage to any player within 3 blocks, matching the original's own {@code
 * onCollideWithPlayer}), real potion immunity, real death explosion (radius 1.5, no block/mob damage,
 * matching the original's own {@code (1.5f, false, false)} call).
 *
 * Real, documented cuts: no "always full-bright" self-illumination - the original's own {@code
 * getBrightnessForRender}/{@code getBrightness} overrides don't have a real modern equivalent (entities
 * don't carry their own light-emission override point any more; lighting is purely block-based now), a
 * real, minor cosmetic loss. Real custom {@code craftstart}/{@code wandfail}/{@code monolith}/{@code zap}/
 * {@code shock} sounds restored 2026-09-15 (were vanilla stand-ins) - see {@link ThaumcraftSounds}'s own
 * doc comment. The original's own {@code getTiming()} method is dead code in the original too (declared,
 * never called anywhere in that same file) - not ported.
 */
public class EntityCultistPortalLesser extends Monster {

	private static final EntityDataAccessor<Boolean> DATA_ACTIVE =
			SynchedEntityData.defineId(EntityCultistPortalLesser.class, EntityDataSerializers.BOOLEAN);

	private int stageCounter = 100;

	public EntityCultistPortalLesser(EntityType<? extends EntityCultistPortalLesser> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 100.0)
				.add(Attributes.ATTACK_DAMAGE, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTIVE, false);
	}

	public boolean isActive() {
		return entityData.get(DATA_ACTIVE);
	}

	public void setActive(boolean active) {
		entityData.set(DATA_ACTIVE, active);
	}

	@Override
	public int getArmorValue() {
		return 4;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void move(MoverType type, Vec3 movement) {
		// Immobile - matches the original's own no-op override exactly.
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return false;
	}

	@Override
	public void aiStep() {
		// No super.aiStep() call - matches the original's own empty onLivingUpdate() override exactly (no
		// vanilla AI/goal-selector ticking for an immobile monolith).
		if (level().isClientSide) {
			return;
		}
		if (!isActive()) {
			if (tickCount % 10 == 0 && level().getNearestPlayer(this, 32.0) != null) {
				setActive(true);
				playSound(ThaumcraftSounds.CRAFTSTART.get(), 1.0f, 1.0f);
			}
		}
		else if (stageCounter-- <= 0) {
			Player nearest = level().getNearestPlayer(this, 32.0);
			if (nearest != null && hasLineOfSight(nearest)) {
				Difficulty diff = level().getCurrentDifficultyAt(blockPosition()).getDifficulty();
				int count = diff == Difficulty.HARD ? 6 : diff == Difficulty.NORMAL ? 4 : 2;
				count -= level().getEntitiesOfClass(EntityCultist.class, getBoundingBox().inflate(32.0)).size();
				if (count > 0) {
					spawnMinion();
				}
			}
			stageCounter = 50 + random.nextInt(50);
		}
	}

	private void spawnMinion() {
		EntityCultist cultist = random.nextFloat() > 0.33f
				? thaumcraft.common.registry.ThaumcraftEntities.CULTIST_KNIGHT.get().create(level())
				: thaumcraft.common.registry.ThaumcraftEntities.CULTIST_CLERIC.get().create(level());
		if (cultist == null || !(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
			return;
		}
		cultist.moveTo(getX() + random.nextFloat() - random.nextFloat(), getY() + 0.25, getZ() + random.nextFloat() - random.nextFloat(),
				0.0f, 0.0f);
		cultist.finalizeSpawn(serverLevel, level().getCurrentDifficultyAt(cultist.blockPosition()),
				net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);
		serverLevel.addFreshEntity(cultist);
		cultist.playSound(ThaumcraftSounds.WANDFAIL.get(), 1.0f, 1.0f);
		hurt(damageSources().magic(), 5 + random.nextInt(5));
	}

	@Override
	public void playerTouch(Player player) {
		if (distanceToSqr(player) < 3.0 && player.hurt(damageSources().indirectMagic(this, this), 4.0f)) {
			playSound(ThaumcraftSounds.ZAP.get(), 1.0f, (random.nextFloat() - random.nextFloat()) * 0.1f + 1.0f);
		}
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			serverLevel.explode(this, getX(), getY(), getZ(), 1.5f, Level.ExplosionInteraction.NONE);
		}
		super.die(source);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ThaumcraftSounds.MONOLITH.get();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return ThaumcraftSounds.ZAP.get();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ThaumcraftSounds.SHOCK.get();
	}
}

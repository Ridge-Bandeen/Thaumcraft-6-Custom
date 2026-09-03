package thaumcraft.common.entities.monster;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityInhabitedZombie} - a real {@link Zombie} subclass wearing
 * real Crimson Plate scraps (always a helm, plus a real 90%(hard)/60%(else) chance each of chest/legs).
 * Never calls for reinforcements, same real technique {@code EntityBrainyZombie} already established.
 *
 * The real, distinctive mechanic: it doesn't die normally at all - {@link #die} is fully overridden (no
 * {@code super.die} call, matching the original's own {@code onDeath}/{@code dropFewItems} both being
 * no-ops) to spawn a real, helmed {@link EntityEldritchCrab} in its place, a real explosion-particle burst
 * (broadcast to nearby clients via {@link ServerLevel#sendParticles} - the original's own server-side
 * {@code world.spawnParticle} call, which already does the same real network broadcast, not a "no client
 * FX" case), and a real manual XP-orb award (only when killed by a real player and {@code doMobLoot} is on,
 * via the modern {@link ExperienceOrb#award} helper - simpler than the original's own manual per-orb
 * splitting loop). No other death drops happen at all, matching the original's own {@code getDropItem}/
 * {@code dropFewItems} overrides exactly.
 *
 * Real, documented cuts: no {@code getCanSpawnHere} "don't spawn near another inhabited zombie" restriction
 * - this whole monster arc has consistently deferred all natural-spawn ({@code SpawnPlacements}) wiring, so
 * there's no spawn-time hook for this check to run from yet either. Sound substitution, same real reason
 * {@code EntityEldritchCrab} already documents: the original's own bundled {@code SoundsTC.crabtalk} isn't
 * registered in this port yet, stands in with a vanilla spider sound (this zombie's real "inhabited by
 * something crab-shaped" identity, not an arbitrary choice).
 */
public class EntityInhabitedZombie extends Zombie implements IEldritchMob {

	public EntityInhabitedZombie(EntityType<? extends EntityInhabitedZombie> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
		float chance = difficulty.getDifficulty() == Difficulty.HARD ? 0.9f : 0.6f;
		setItemSlot(EquipmentSlot.HEAD, new ItemStack(ItemsTC.crimsonPlateHelm.get()));
		if (random.nextFloat() <= chance) {
			setItemSlot(EquipmentSlot.CHEST, new ItemStack(ItemsTC.crimsonPlateChest.get()));
		}
		if (random.nextFloat() <= chance) {
			setItemSlot(EquipmentSlot.LEGS, new ItemStack(ItemsTC.crimsonPlateLegs.get()));
		}
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel serverLevel) {
			EntityEldritchCrab crab = ThaumcraftEntities.ELDRITCH_CRAB.get().create(serverLevel);
			if (crab != null) {
				crab.moveTo(getX(), getY() + getEyeHeight(), getZ(), getYRot(), getXRot());
				crab.setHelm(true);
				serverLevel.addFreshEntity(crab);
			}
			if (lastHurtByPlayer != null && serverLevel.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
				int xp = getExperienceReward(serverLevel, lastHurtByPlayer);
				if (xp > 0) {
					ExperienceOrb.award(serverLevel, position(), xp);
				}
			}
			serverLevel.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + getBbHeight() / 2.0, getZ(), 20,
					getBbWidth(), getBbHeight() / 2.0, getBbWidth(), 0.02);
		}
		discard();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return SoundEvents.GENERIC_HURT;
	}
}

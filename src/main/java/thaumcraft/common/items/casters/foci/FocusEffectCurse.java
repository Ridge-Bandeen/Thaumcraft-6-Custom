package thaumcraft.common.items.casters.foci;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusEffectCurse}. Damages the target and stacks a cascade of debuffs
 * on it (poison always, then slowness/weakness/mining fatigue/hunger/unluck each with decreasing odds -
 * same probabilities and durations as the original). Field names changed under the hood:
 * {@code MobEffects.SLOWNESS}/{@code MINING_FATIGUE} are {@link MobEffects#MOVEMENT_SLOWDOWN}/
 * {@link MobEffects#DIG_SLOWDOWN} now, and {@code PotionEffect} is {@link MobEffectInstance} taking a
 * {@code Holder<MobEffect>} instead of a raw {@code Potion}.
 *
 * Simplified: drops the original's block-hit branch (surrounding the impact point with
 * {@code BlocksTC.effectSap}, a sticky-web trap block) entirely - that block isn't ported, and there's
 * nothing sensible to fall back to. Only the entity-hit branch (damage + debuffs) exists here; a block hit
 * is a no-op, same as every other focus in this package that only reacts to entities.
 */
public class FocusEffectCurse extends FocusEffect {

	@Override
	public String getResearch() {
		return "FOCUSCURSE";
	}

	@Override
	public String getKey() {
		return "thaumcraft.CURSE";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.DEATH;
	}

	@Override
	public int getComplexity() {
		return getSettingValue("duration") + getSettingValue("power") * 3;
	}

	@Override
	public float getDamageForDisplay(float finalPower) {
		return (1.0f + getSettingValue("power")) * finalPower;
	}

	@Override
	public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) {
		Level level = getPackage().world;
		if (level == null) return false;

		if (!(target instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof LivingEntity living)) {
			return false;
		}

		float damage = getDamageForDisplay(finalPower);
		int duration = 20 * getSettingValue("duration");
		int amplifier = Math.max(0, Math.round(getSettingValue("power") * finalPower / 2.0f));

		Entity caster = getPackage().getCaster();
		living.hurt(level.damageSources().indirectMagic(caster, caster), damage);

		living.addEffect(new MobEffectInstance(MobEffects.POISON, duration, amplifier));
		float chance = 0.85f;
		if (level.random.nextFloat() < chance) {
			living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier));
			chance -= 0.15f;
		}
		if (level.random.nextFloat() < chance) {
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier));
			chance -= 0.15f;
		}
		if (level.random.nextFloat() < chance) {
			living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration * 2, amplifier));
			chance -= 0.15f;
		}
		if (level.random.nextFloat() < chance) {
			living.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration * 3, amplifier));
			chance -= 0.15f;
		}
		if (level.random.nextFloat() < chance) {
			living.addEffect(new MobEffectInstance(MobEffects.UNLUCK, duration * 3, amplifier));
		}
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		return new NodeSetting[] {
				new NodeSetting("power", "focus.common.power", new NodeSetting.NodeSettingIntRange(1, 5)),
				new NodeSetting("duration", "focus.common.duration", new NodeSetting.NodeSettingIntRange(1, 10))
		};
	}

	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		// no-op for now - depends on the not-yet-ported client particle dispatcher (FXDispatcher)
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.15f,
				1.0f + caster.level().random.nextFloat() / 2.0f);
	}
}

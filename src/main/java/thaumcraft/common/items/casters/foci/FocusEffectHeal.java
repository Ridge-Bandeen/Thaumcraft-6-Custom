package thaumcraft.common.items.casters.foci;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Replaces the old (1.12.2) {@code FocusEffectHeal}. Same core rule (heal the living, hurt the undead) -
 * {@code isEntityUndead()} doesn't exist any more, replaced with {@link LivingEntity#isInvertedHealAndHarm()},
 * the flag vanilla's own Instant Health/Harm potions use for the identical undead-inverts-healing check.
 * Drops the network FX packet broadcast and client-only particle rendering (renderParticleFX is now a
 * no-op), same as every other ported focus in this package.
 */
public class FocusEffectHeal extends FocusEffect {

	@Override
	public String getResearch() {
		return "FOCUSHEAL";
	}

	@Override
	public String getKey() {
		return "thaumcraft.HEAL";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.LIFE;
	}

	@Override
	public int getComplexity() {
		return getSettingValue("power") * 4;
	}

	@Override
	public float getDamageForDisplay(float finalPower) {
		return -getSettingValue("power") * finalPower;
	}

	@Override
	public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) {
		Level level = getPackage().world;
		if (level == null) return false;

		if (!(target instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof LivingEntity living)) {
			return false;
		}

		float power = getSettingValue("power") * finalPower;
		// isInvertedHealAndHarm() is vanilla's own modern equivalent of the old isEntityUndead() check -
		// see net.minecraft.world.effect.HealOrHarmMobEffect, which uses the same flag the same way.
		if (living.isInvertedHealAndHarm()) {
			Entity caster = getPackage().getCaster();
			living.hurt(level.damageSources().indirectMagic(caster, caster), power * 1.5f);
		} else {
			living.heal(power);
		}
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		return new NodeSetting[] { new NodeSetting("power", "focus.heal.power", new NodeSetting.NodeSettingIntRange(1, 5)) };
	}

	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		// no-op for now - depends on the not-yet-ported client particle dispatcher (FXDispatcher)
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.CHORUS_FLOWER_GROW, SoundSource.PLAYERS, 2.0f,
				2.0f + (float) (caster.level().random.nextGaussian() * 0.1));
	}
}

package thaumcraft.common.items.casters.foci;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusEffectEarth}. Plain physical damage on an entity hit -
 * {@code DamageSource.causeThrownDamage} is {@link net.minecraft.world.damagesource.DamageSources#thrown}
 * now.
 *
 * Simplified: dropped the original's block-hit branch entirely (queuing a soft-enough block for gradual
 * breaking via {@code ServerEvents.addBreaker}) - same gradual-mining scheduler subsystem Focus:
 * Excavation's own doc comment explains isn't ported. Unlike Excavation, this one doesn't get an instant-
 * break substitute either - Earth's original design was "a cheap side effect of an attack focus," not a
 * dedicated mining tool the way Excavation is, so a block hit is just a no-op here, consistent with every
 * other entity-only focus in this package.
 */
public class FocusEffectEarth extends FocusEffect {

	@Override
	public String getResearch() {
		return "FOCUSELEMENTAL";
	}

	@Override
	public String getKey() {
		return "thaumcraft.EARTH";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.EARTH;
	}

	@Override
	public int getComplexity() {
		return getSettingValue("power") * 3;
	}

	@Override
	public float getDamageForDisplay(float finalPower) {
		return 2 * getSettingValue("power") * finalPower;
	}

	@Override
	public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) {
		Level level = getPackage().world;
		if (level == null) return false;

		if (!(target instanceof EntityHitResult entityHit)) return false;

		float damage = getDamageForDisplay(finalPower);
		Entity caster = getPackage().getCaster();
		entityHit.getEntity().hurt(level.damageSources().thrown(caster, caster), damage);
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		return new NodeSetting[] { new NodeSetting("power", "focus.common.power", new NodeSetting.NodeSettingIntRange(1, 5)) };
	}

	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		// no-op for now - depends on the not-yet-ported client particle dispatcher (FXDispatcher)
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 0.25f,
				1.0f + (float) (caster.level().random.nextGaussian() * 0.05));
	}
}

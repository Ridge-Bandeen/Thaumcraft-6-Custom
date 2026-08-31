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
 * Replaces the old (1.12.2) {@code FocusEffectFlux}. Despite the name, this never actually touches the
 * aura system in the original either (no {@code AuraHelper} call anywhere in its {@code execute}) - it's
 * plain indirect-magic damage tagged with {@link Aspect#FLUX} for flavor/complexity purposes only, same
 * shape as {@link FocusEffectEarth}.
 *
 * The original's {@code execute} always returned {@code false}, even after successfully applying damage -
 * every other damage-only focus ported this session returns {@code true} on a successful hit, so this
 * follows that established (and more obviously correct) convention rather than mechanically preserving
 * what reads as a copy-paste slip in the original.
 */
public class FocusEffectFlux extends FocusEffect {

	@Override
	public String getResearch() {
		return "FOCUSFLUX";
	}

	@Override
	public String getKey() {
		return "thaumcraft.FLUX";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.FLUX;
	}

	@Override
	public int getComplexity() {
		return getSettingValue("power") * 3;
	}

	@Override
	public float getDamageForDisplay(float finalPower) {
		return (3 + getSettingValue("power")) * finalPower;
	}

	@Override
	public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) {
		Level level = getPackage().world;
		if (level == null) return false;

		if (!(target instanceof EntityHitResult entityHit)) return false;

		float damage = getDamageForDisplay(finalPower);
		Entity caster = getPackage().getCaster();
		entityHit.getEntity().hurt(level.damageSources().indirectMagic(caster, caster), damage);
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
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.CHORUS_FLOWER_GROW, SoundSource.PLAYERS, 2.0f,
				2.0f + (float) (caster.level().random.nextGaussian() * 0.1));
	}
}

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
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * Replaces the old (1.12.2) {@code FocusEffectAir}. Damage plus knockback, unlike the other plain-damage
 * elemental foci ported so far (Earth/Flux) - {@code EntityLivingBase#knockBack(Entity, float, double,
 * double)} is {@link LivingEntity#knockback(double, double, double)} now (no attacker parameter; strength
 * and direction ratios only).
 *
 * Real custom {@code wind} cast sound restored 2026-09-15 (was a vanilla {@code PHANTOM_FLAP} stand-in) -
 * see {@link ThaumcraftSounds}'s own doc comment.
 */
public class FocusEffectAir extends FocusEffect {

	@Override
	public String getResearch() {
		return "FOCUSELEMENTAL";
	}

	@Override
	public String getKey() {
		return "thaumcraft.AIR";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.AIR;
	}

	@Override
	public int getComplexity() {
		return getSettingValue("power") * 2;
	}

	@Override
	public float getDamageForDisplay(float finalPower) {
		return (1 + getSettingValue("power")) * finalPower;
	}

	@Override
	public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) {
		Level level = getPackage().world;
		if (level == null) return false;

		level.playSound(null, target.getLocation().x, target.getLocation().y, target.getLocation().z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5f, 0.66f);

		if (!(target instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof LivingEntity living)) {
			return false;
		}

		float damage = getDamageForDisplay(finalPower);
		Entity caster = getPackage().getCaster();
		living.hurt(level.damageSources().thrown(caster, caster), damage);

		if (trajectory != null) {
			living.knockback(damage * 0.25, -trajectory.direction.x, -trajectory.direction.z);
		} else {
			double yawRad = Math.toRadians(living.getYRot());
			living.knockback(damage * 0.25, -Math.sin(yawRad), Math.cos(yawRad));
		}
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
		caster.level().playSound(null, caster.blockPosition().above(), ThaumcraftSounds.WIND.get(), SoundSource.PLAYERS, 0.125f, 2.0f);
	}
}

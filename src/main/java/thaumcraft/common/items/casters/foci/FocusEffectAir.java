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
		FocusFxHelper.sendPartImpact(level, target.getLocation(), getKey());

		level.playSound(null, target.getLocation().x, target.getLocation().y, target.getLocation().z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5f, 0.66f);

		if (!(target instanceof EntityHitResult entityHit)) return false;

		// as the original: thrown damage to anything hit, knocked back if it lives
		float damage = getDamageForDisplay(finalPower);
		Entity caster = getPackage().getCaster();
		entityHit.getEntity().hurt(level.damageSources().thrown(caster, entityHit.getEntity()), damage);
		if (!(entityHit.getEntity() instanceof LivingEntity living)) return true;

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

	/** See {@link thaumcraft.client.fx.ClientFxHandlers#spawnGeneric}'s own doc comment for why this builds a
	 * plain {@link FocusGenericFx} rather than an {@code FXGeneric} directly. */
	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		FocusGenericFx cfg = new FocusGenericFx();
		cfg.grav = -0.1f;
		cfg.age = 20 + world.random.nextInt(10);
		cfg.alpha = new float[] { 0.5f, 0.0f };
		cfg.grid = 32;
		cfg.partStart = 337;
		cfg.partNum = 5;
		cfg.slowDown = 0.75;
		cfg.rot = (float) world.random.nextGaussian() / 2.0f;
		float s = (float) (2.0 + world.random.nextGaussian() * 0.5);
		cfg.scale = new float[] { s, s * 2.0f };
		thaumcraft.client.fx.ClientFxHandlers.spawnGeneric(world, posX, posY, posZ, motionX, motionY, motionZ, cfg);
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), ThaumcraftSounds.WIND.get(), SoundSource.PLAYERS, 0.125f, 2.0f);
	}
}

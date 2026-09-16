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
 * Replaces the old (1.12.2) {@code FocusEffectFrost}. Simplified versus the original for this pass: the
 * water-to-frosted-ice terrain effect is dropped (needs real fluid-state iteration/scheduled-tick placement
 * that wasn't worth building for a secondary part of this effect) - only the entity slow+damage part is
 * ported. Same FX/network-packet drop as FocusEffectFire/FocusMediumTouch.
 */
public class FocusEffectFrost extends FocusEffect {

	@Override
	public String getResearch() {
		return "FOCUSELEMENTAL";
	}

	@Override
	public String getKey() {
		return "thaumcraft.FROST";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.COLD;
	}

	@Override
	public int getComplexity() {
		return getSettingValue("duration") + getSettingValue("power") * 2;
	}

	@Override
	public float getDamageForDisplay(float finalPower) {
		return (3 + getSettingValue("power")) * finalPower;
	}

	@Override
	public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) {
		Level level = getPackage().world;
		if (level != null) {
			FocusFxHelper.sendPartImpact(level, target.getLocation(), getKey());
		}
		if (!(target instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof LivingEntity living)) {
			return false;
		}
		Entity caster = getPackage().getCaster();
		float damage = getDamageForDisplay(finalPower);
		int duration = 20 * getSettingValue("duration");
		int potency = (int) (1.0f + getSettingValue("power") * finalPower / 3.0f);
		living.hurt(level.damageSources().indirectMagic(caster, caster), damage);
		living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, potency));
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		return new NodeSetting[] {
				new NodeSetting("power", "focus.common.power", new NodeSetting.NodeSettingIntRange(1, 5)),
				new NodeSetting("duration", "focus.common.duration", new NodeSetting.NodeSettingIntRange(2, 10))
		};
	}

	/** See {@link thaumcraft.client.fx.ClientFxHandlers#spawnGeneric}'s own doc comment for why this builds a
	 * plain {@link FocusGenericFx} rather than an {@code FXGeneric} directly. */
	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		FocusGenericFx cfg = new FocusGenericFx();
		cfg.age = 40 + world.random.nextInt(40);
		cfg.alpha = new float[] { 1.0f, 0.0f };
		cfg.partStart = 8;
		cfg.grav = 0.033f;
		cfg.slowDown = 0.8;
		cfg.randomX = 0.0025f;
		cfg.randomY = 1.0E-4f;
		cfg.randomZ = 0.0025f;
		cfg.scale = new float[] { (float) (0.7 + world.random.nextGaussian() * 0.3) };
		cfg.rotstart = world.random.nextFloat() * 3.0f;
		cfg.rot = (float) world.random.nextGaussian() / 4.0f;
		thaumcraft.client.fx.ClientFxHandlers.spawnGeneric(world, posX, posY, posZ, motionX, motionY, motionZ, cfg);
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.2f,
				1.0f + (float) (caster.level().random.nextGaussian() * 0.05));
	}
}

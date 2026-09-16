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
 * Real {@code PacketFXFocusPartImpact} broadcast + {@link #renderParticleFX} restored 2026-09-16 (see
 * {@link FocusFxHelper}'s own doc comment).
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
		FocusFxHelper.sendPartImpact(level, target.getLocation(), getKey());

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

	/** See {@link thaumcraft.client.fx.ClientFxHandlers#spawnGeneric}'s own doc comment for why this builds a
	 * plain {@link FocusGenericFx} rather than an {@code FXGeneric} directly. */
	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		FocusGenericFx cfg = new FocusGenericFx();
		cfg.age = (int) (10.0f + 10.0f * world.random.nextFloat());
		cfg.alpha = new float[] { 0.0f, 0.7f, 0.7f, 0.0f };
		cfg.scale = new float[] { world.random.nextFloat() * 2.0f, world.random.nextFloat() };
		cfg.slowDown = 0.8;
		cfg.grav = (float) (world.random.nextGaussian() * 0.1);
		cfg.randomX = cfg.randomY = cfg.randomZ = 0.0125f;
		cfg.rot = (float) world.random.nextGaussian();
		cfg.delay = world.random.nextInt(4);
		thaumcraft.client.fx.ClientFxHandlers.spawnGeneric(world, posX, posY, posZ,
				motionX + world.random.nextGaussian() * 0.01, motionY + world.random.nextGaussian() * 0.01, motionZ + world.random.nextGaussian() * 0.01, cfg);
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.CHORUS_FLOWER_GROW, SoundSource.PLAYERS, 2.0f,
				2.0f + (float) (caster.level().random.nextGaussian() * 0.1));
	}
}

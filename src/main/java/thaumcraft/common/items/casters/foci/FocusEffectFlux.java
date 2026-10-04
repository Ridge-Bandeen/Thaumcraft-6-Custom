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
		FocusFxHelper.sendPartImpact(level, target.getLocation(), getKey());

		if (!(target instanceof EntityHitResult entityHit)) return false;

		float damage = getDamageForDisplay(finalPower);
		Entity caster = getPackage().getCaster();
		entityHit.getEntity().hurt(level.damageSources().indirectMagic(caster, entityHit.getEntity()), damage);
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
		cfg.age = (int) (15.0f + 10.0f * world.random.nextFloat());
		cfg.redStart = cfg.redEnd = 0.25f + world.random.nextFloat() * 0.25f;
		cfg.greenStart = cfg.greenEnd = 0.0f;
		cfg.blueStart = cfg.blueEnd = 0.25f + world.random.nextFloat() * 0.25f;
		cfg.alpha = new float[] { 0.0f, 1.0f, 1.0f, 0.0f };
		cfg.partStart = 128;
		cfg.partNum = 14;
		cfg.scale = new float[] { 2.0f + world.random.nextFloat(), 0.25f + world.random.nextFloat() * 0.25f };
		cfg.loop = true;
		cfg.slowDown = 0.9;
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

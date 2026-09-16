package thaumcraft.common.items.casters.foci;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.lib.scheduling.BlockScheduler;

/**
 * Replaces the old (1.12.2) {@code FocusEffectEarth}. Plain physical damage on an entity hit -
 * {@code DamageSource.causeThrownDamage} is {@link net.minecraft.world.damagesource.DamageSources#thrown}
 * now.
 *
 * Real port of the original's own block-hit branch, restored via {@link BlockScheduler#addBreaker} (this
 * class's own doc comment previously said this branch was dropped entirely - a real, felt gameplay
 * difference from the original that went out without a check-in; see the port-tracking memory for why it's
 * being corrected now). Matches the original's own exact real formula, confirmed against that class's real
 * pre-port source: a soft-enough block (hardness &lt;= {@code getDamageForDisplay(finalPower) / 25.0f}) gets
 * queued with {@code strength=1.0f}, {@code durabilityCurrent=0.0f}, {@code durabilityMax=1.0f} - since
 * {@code durabilityCurrent} already starts at (and stays) 0 or below, this isn't a real multi-tick grind the
 * way Focus: Excavation's own break is; it's a real single delayed break, staggered by {@code num} ticks
 * (via {@code delay}) so a multi-target cast doesn't break every soft block in the same tick. No FX overlay
 * (matches the original's own real {@code fx=false}), a small real 0.1 vis cost. Earth's original design was
 * "a cheap side effect of an attack focus," not a dedicated mining tool - the original itself never returns
 * {@code true} for a block hit (only an entity hit ends the cast successfully), kept as-is.
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
		FocusFxHelper.sendPartImpact(level, target.getLocation(), getKey());

		if (target instanceof EntityHitResult entityHit) {
			float damage = getDamageForDisplay(finalPower);
			Entity caster = getPackage().getCaster();
			entityHit.getEntity().hurt(level.damageSources().thrown(caster, caster), damage);
			return true;
		}

		if (target instanceof BlockHitResult blockHit && getPackage().getCaster() instanceof Player player) {
			BlockPos pos = blockHit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (state.getDestroySpeed(level, pos) <= getDamageForDisplay(finalPower) / 25.0f) {
				BlockScheduler.addBreaker(level, pos, state, player, false, false, 0, 1.0f, 0.0f, 1.0f, num, 0.1f);
			}
		}
		return false;
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
		cfg.grav = 0.4f;
		cfg.layer = 1;
		cfg.age = 20 + world.random.nextInt(10);
		cfg.alpha = new float[] { 1.0f, 0.0f };
		cfg.partStart = 75 + world.random.nextInt(4);
		cfg.slowDown = 0.9;
		cfg.rot = (float) world.random.nextGaussian();
		float s = (float) (1.0 + world.random.nextGaussian() * 0.2);
		cfg.scale = new float[] { s, s / 2.0f };
		thaumcraft.client.fx.ClientFxHandlers.spawnGeneric(world, posX, posY, posZ, motionX, motionY, motionZ, cfg);
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 0.25f,
				1.0f + (float) (caster.level().random.nextGaussian() * 0.05));
	}
}

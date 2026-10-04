package thaumcraft.common.items.casters.foci;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusEffectFire}. Real {@code PacketFXFocusPartImpact}
 * broadcast + {@link #renderParticleFX} restored 2026-09-16 (see {@link FocusFxHelper}'s own doc comment) -
 * the gameplay effect (damage + ignite, or placing fire on a clicked block) was always unchanged.
 */
public class FocusEffectFire extends FocusEffect {

	@Override
	public String getResearch() {
		return "BASEAUROMANCY";
	}

	@Override
	public String getKey() {
		return "thaumcraft.FIRE";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.FIRE;
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
		if (level == null) return false;
		FocusFxHelper.sendPartImpact(level, target.getLocation(), getKey());

		if (target instanceof BlockHitResult blockHit) {
			if (getSettingValue("duration") <= 0) return false;
			BlockPos pos = blockHit.getBlockPos().relative(blockHit.getDirection());
			if (level.getBlockState(pos).isAir() && level.random.nextFloat() < finalPower) {
				level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, level.random.nextFloat() * 0.4f + 0.8f);
				level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 11);
				return true;
			}
			return false;
		}

		if (!(target instanceof EntityHitResult entityHit)) return false;
		Entity hit = entityHit.getEntity();
		if (hit.fireImmune()) return false;

		// as the original: fireball damage (fire), its direct source the target itself, set alight (1 + duration²) x power seconds
		float fire = (1 + getSettingValue("duration") * getSettingValue("duration")) * finalPower;
		float damage = getDamageForDisplay(finalPower);
		Entity caster = getPackage().getCaster();
		hit.hurt(level.damageSources().source(net.minecraft.world.damagesource.DamageTypes.FIREBALL, caster, hit), damage);
		if (fire > 0.0f) {
			hit.igniteForSeconds(Math.round(fire));
		}
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		return new NodeSetting[] {
				new NodeSetting("power", "focus.common.power", new NodeSetting.NodeSettingIntRange(1, 5)),
				new NodeSetting("duration", "focus.fire.burn", new NodeSetting.NodeSettingIntRange(0, 5))
		};
	}

	/** Real modern equivalent of the true original's own {@code GenPart}-configured {@code
	 * drawGenericParticles} call. Builds a plain, common-safe {@link FocusGenericFx} and hands the real
	 * construction off to {@link thaumcraft.client.fx.ClientFxHandlers#spawnGeneric} - see that method's own
	 * doc comment for why this indirection is required (not just style) after a real dedicated-server crash. */
	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		FocusGenericFx cfg = new FocusGenericFx();
		cfg.grav = -0.2f;
		cfg.age = 10;
		cfg.alpha = new float[] { 0.7f };
		cfg.partStart = 640;
		cfg.partNum = 10;
		cfg.slowDown = 0.75;
		cfg.scale = new float[] { (float) (1.5 + world.random.nextGaussian() * 0.2) };
		thaumcraft.client.fx.ClientFxHandlers.spawnGeneric(world, posX, posY, posZ, motionX, motionY, motionZ, cfg);
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f,
				1.0f + (float) (caster.level().random.nextGaussian() * 0.05));
	}
}

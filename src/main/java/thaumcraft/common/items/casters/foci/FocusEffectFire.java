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
 * Replaces the old (1.12.2) {@code FocusEffectFire}. Drops the network FX packet broadcast (see
 * {@link FocusMediumTouch}) and the client-only particle rendering (renderParticleFX is now a no-op) - the
 * gameplay effect (damage + ignite, or placing fire on a clicked block) is unchanged.
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

		if (!(target instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof LivingEntity living)) {
			return false;
		}
		if (living.fireImmune()) return false;

		int fireTicks = 1 + getSettingValue("duration") * getSettingValue("duration");
		float damage = getDamageForDisplay(finalPower);
		Entity caster = getPackage().getCaster();
		living.hurt(level.damageSources().indirectMagic(caster, caster), damage);
		if (fireTicks > 0) {
			living.igniteForSeconds(fireTicks * finalPower);
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

	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		// no-op for now - depends on the not-yet-ported client particle dispatcher (FXDispatcher)
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f,
				1.0f + (float) (caster.level().random.nextGaussian() * 0.05));
	}
}

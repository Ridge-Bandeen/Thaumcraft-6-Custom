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
 * Replaces the old (1.12.2) {@code FocusEffectFrost}, as the original: thrown damage and Slowness to what it hits, and
 * on a block it freezes still water within {@code 2 x power} (at most 16) into frosted ice that melts again.
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
		if (target instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
			freezeWater(level, blockHit, finalPower);
			return false;
		}
		if (!(target instanceof EntityHitResult entityHit)) return false;
		Entity caster = getPackage().getCaster();
		float damage = getDamageForDisplay(finalPower);
		int duration = 20 * getSettingValue("duration");
		int potency = (int) (1.0f + getSettingValue("power") * finalPower / 3.0f);
		entityHit.getEntity().hurt(level.damageSources().thrown(caster, entityHit.getEntity()), damage);
		if (entityHit.getEntity() instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, potency));
		}
		return false;
	}

	/** The original's frost on a block: still water within the radius becomes frosted ice, melting in 3-6 seconds. */
	private void freezeWater(Level level, net.minecraft.world.phys.BlockHitResult hit, float finalPower) {
		float f = Math.min(16.0f, 2 * getSettingValue("power") * finalPower);
		int r = (int) f;
		net.minecraft.core.BlockPos center = hit.getBlockPos();
		net.minecraft.world.phys.Vec3 at = hit.getLocation();
		net.minecraft.world.level.block.state.BlockState ice = net.minecraft.world.level.block.Blocks.FROSTED_ICE.defaultBlockState();
		for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			if (net.minecraft.world.phys.Vec3.atCenterOf(p).distanceToSqr(at) > f * f) continue;
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(p);
			if (!state.is(net.minecraft.world.level.block.Blocks.WATER) || !state.getFluidState().isSource()
					|| !level.isUnobstructed(ice, p, net.minecraft.world.phys.shapes.CollisionContext.empty())) continue;
			level.setBlockAndUpdate(p, ice);
			level.scheduleTick(p.immutable(), net.minecraft.world.level.block.Blocks.FROSTED_ICE, net.minecraft.util.Mth.nextInt(level.random, 60, 120));
		}
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

package thaumcraft.common.items.casters.foci;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusEffectCurse}. Damages the target and stacks a cascade of debuffs
 * on it (poison always, then slowness/weakness/mining fatigue/hunger/unluck each with decreasing odds -
 * same probabilities and durations as the original). Field names changed under the hood:
 * {@code MobEffects.SLOWNESS}/{@code MINING_FATIGUE} are {@link MobEffects#MOVEMENT_SLOWDOWN}/
 * {@link MobEffects#DIG_SLOWDOWN} now, and {@code PotionEffect} is {@link MobEffectInstance} taking a
 * {@code Holder<MobEffect>} instead of a raw {@code Potion}.
 *
 * <p>Real block-hit branch, restored 2026-09-15 now that {@link BlocksTC#effectSap} is real: blankets every
 * real full-cube block surface within a real radius (capped at 8, scaled by power/finalPower, matching the
 * original's own {@code min(8, 1.5*power*finalPower)} exactly) of the hit point with a real
 * {@code effect_sap} trap, provided the space directly above each candidate is real air - a real "sow a
 * minefield around the impact" area-denial effect, distinct from the entity-hit branch's direct damage.
 */
public class FocusEffectCurse extends FocusEffect {

	@Override
	public String getResearch() {
		return "FOCUSCURSE";
	}

	@Override
	public String getKey() {
		return "thaumcraft.CURSE";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.DEATH;
	}

	@Override
	public int getComplexity() {
		return getSettingValue("duration") + getSettingValue("power") * 3;
	}

	@Override
	public float getDamageForDisplay(float finalPower) {
		return (1.0f + getSettingValue("power")) * finalPower;
	}

	@Override
	public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) {
		Level level = getPackage().world;
		if (level == null) return false;
		sendBamfFx(level, target.getLocation());

		if (target instanceof BlockHitResult blockHit) {
			float radius = Math.min(8.0f, 1.5f * getSettingValue("power") * finalPower);
			BlockPos center = blockHit.getBlockPos();
			Vec3 hitVec = blockHit.getLocation();
			int r = Mth.ceil(radius);
			boolean placedAny = false;
			for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
				if (p.distToCenterSqr(hitVec.x, hitVec.y, hitVec.z) <= radius * radius
						&& level.getBlockState(p.above()).isAir()
						&& level.getBlockState(p).isCollisionShapeFullBlock(level, p)) {
					level.setBlock(p.above(), BlocksTC.effectSap.get().defaultBlockState(), 3);
					placedAny = true;
				}
			}
			return placedAny;
		}

		if (!(target instanceof EntityHitResult entityHit)) return false;

		// as the original: magic damage to anything hit (the potency truncated, not rounded); the curses only take on the living
		float damage = getDamageForDisplay(finalPower);
		int duration = 20 * getSettingValue("duration");
		int amplifier = Math.max(0, (int) (getSettingValue("power") * finalPower / 2.0f));

		Entity caster = getPackage().getCaster();
		entityHit.getEntity().hurt(level.damageSources().indirectMagic(caster, entityHit.getEntity()), damage);
		if (!(entityHit.getEntity() instanceof LivingEntity living)) return true;

		living.addEffect(new MobEffectInstance(MobEffects.POISON, duration, amplifier));
		float chance = 0.85f;
		if (level.random.nextFloat() < chance) {
			living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, amplifier));
			chance -= 0.15f;
		}
		if (level.random.nextFloat() < chance) {
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier));
			chance -= 0.15f;
		}
		if (level.random.nextFloat() < chance) {
			living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration * 2, amplifier));
			chance -= 0.15f;
		}
		if (level.random.nextFloat() < chance) {
			living.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration * 3, amplifier));
			chance -= 0.15f;
		}
		if (level.random.nextFloat() < chance) {
			living.addEffect(new MobEffectInstance(MobEffects.UNLUCK, duration * 3, amplifier));
		}
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		return new NodeSetting[] {
				new NodeSetting("power", "focus.common.power", new NodeSetting.NodeSettingIntRange(1, 5)),
				new NodeSetting("duration", "focus.common.duration", new NodeSetting.NodeSettingIntRange(1, 10))
		};
	}

	/** Real {@code PacketFXBlockBamf} "curse flash" restored 2026-09-16, matching the true original's own
	 * real unconditional-at-cast send exactly (colour {@code 6946821}, sound+flair both on) - reuses the
	 * packet/renderer {@code EntityFluxRift}'s own collapse effect already established. */
	private void sendBamfFx(Level level, Vec3 pos) {
		if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
		try {
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(serverLevel, null, pos.x, pos.y, pos.z, 64.0,
					new thaumcraft.common.lib.network.fx.PacketFXBlockBamf(pos.x, pos.y, pos.z, 6946821, true, true));
		} catch (Exception e) {
			thaumcraft.Thaumcraft.LOGGER.debug("Curse focus bamf FX packet failed to send (no real client connection?)", e);
		}
	}

	/** See {@link thaumcraft.client.fx.ClientFxHandlers#spawnGeneric}'s own doc comment for why this builds a
	 * plain {@link FocusGenericFx} rather than an {@code FXGeneric} directly. */
	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		FocusGenericFx cfg = new FocusGenericFx();
		cfg.age = 8;
		cfg.redStart = cfg.redEnd = 0.41f + world.random.nextFloat() * 0.2f;
		cfg.greenStart = cfg.greenEnd = 0.0f;
		cfg.blueStart = cfg.blueEnd = 0.019f + world.random.nextFloat() * 0.2f;
		cfg.alpha = new float[] { 0.0f, world.random.nextFloat(), world.random.nextFloat(), world.random.nextFloat(), 0.0f };
		cfg.grid = 16;
		cfg.partStart = 72 + world.random.nextInt(4);
		cfg.scale = new float[] { 2.0f + world.random.nextFloat() * 4.0f };
		cfg.loop = false;
		cfg.slowDown = 0.9;
		cfg.grav = 0.0f;
		cfg.rotstart = world.random.nextFloat();
		cfg.rot = 0.0f;
		cfg.delay = world.random.nextInt(4);
		thaumcraft.client.fx.ClientFxHandlers.spawnGeneric(world, posX, posY, posZ, motionX, motionY, motionZ, cfg);
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.15f,
				1.0f + caster.level().random.nextFloat() / 2.0f);
	}
}

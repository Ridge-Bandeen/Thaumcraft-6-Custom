package thaumcraft.common.entities.monster;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code EntityWisp} - a small flying elemental, the most common
 * {@code EntityFluxRift} rift-event spawn (see that entity's own doc comment for the weighted event
 * table this feeds). {@code EntityFlying} has no direct modern equivalent name, but the shape is identical:
 * {@link FlyingMob} (Ghast/Phantom's own base) skips gravity/fall-damage in exactly the same way, so this
 * still just hand-rolls its own motion every tick like the original did - there was never any vanilla
 * Goal-based AI to replace, {@code registerGoals()} is intentionally empty.
 *
 * On first tick it randomly locks in a {@link #getWispType} (a synced Aspect tag - 9 in 10 a primal aspect,
 * 1 in 10 a compound one, same odds as the original), which drives its death-drop (a matching essentia
 * crystal, same {@code makeCrystal} stand-in {@code ToolEvents} already uses locally rather than porting
 * the whole unconverted {@code ThaumcraftApiHelper} for one method) - see {@link #dropCustomDeathLoot}.
 *
 * Combat is a short-range "zap": once a target is in sight for 20 ticks, it deals 3-4 damage (a straight
 * translation of the original's per-target motion-based damage roll) and sends the real {@code
 * PacketFXWispZap}/{@link thaumcraft.client.fx.beams.FXBolt} pair for the visual cue, restored 2026-09-15
 * (see {@code PacketFXWispZap}'s own doc comment) - this port had previously reused the more generic {@code
 * PacketFXSlash}/{@code ClientFxHandlers#handleSlash} pair as a documented stand-in while there was no real
 * bolt renderer to show, including the real per-aspect colour the true original's own zap always had.
 *
 * Real visible glow restored 2026-09-16 - see {@link thaumcraft.client.fx.particles.FXWispGlow}'s own doc
 * comment (this entity had genuinely no visible model at all before, not a deliberate simplification: the
 * true original's own real {@code RenderWisp} never drew a skeletal model either, just 3 real billboarded
 * glow quads every frame).
 *
 * As the original it puffs as it appears and as it dies, and spawns naturally (see {@code WorldConfigHooks}). Real custom
 * {@code zap}/{@code wisplive}/{@code wispdead} sounds restored 2026-09-15 (were vanilla stand-ins) - see
 * {@link thaumcraft.common.registry.ThaumcraftSounds}'s own doc comment.
 */
public class EntityWisp extends FlyingMob implements Enemy {

	private static final EntityDataAccessor<String> DATA_TYPE =
			SynchedEntityData.defineId(EntityWisp.class, EntityDataSerializers.STRING);

	private BlockPos currentFlightTarget;
	private int aggroCooldown;
	private int attackCounter;

	public EntityWisp(EntityType<? extends EntityWisp> type, Level level) {
		super(type, level);
		xpReward = 5; // as the original
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 22.0)
				.add(Attributes.ATTACK_DAMAGE, 3.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TYPE, "");
	}

	@Override
	protected void registerGoals() {
		// intentionally empty - every behaviour below is hand-rolled in aiStep(), matching the original's
		// own onLivingUpdate approach; there's no vanilla Goal to replace.
	}

	public String getWispType() {
		return entityData.get(DATA_TYPE);
	}

	public void setWispType(String tag) {
		entityData.set(DATA_TYPE, tag);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.getEntity() instanceof LivingEntity attacker) {
			setTarget(attacker);
			aggroCooldown = 200;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide) {
			// as the original: a big puff as it appears
			if (tickCount <= 1) thaumcraft.client.fx.ClientFxHandlers.burst(level(), getX(), getY(), getZ(), 10.0f);
			thaumcraft.client.fx.ClientFxHandlers.maintainWisp(this);
			return;
		}
		ServerLevel level = (ServerLevel) level();

		if (Aspect.getAspect(getWispType()) == null) {
			List<Aspect> pool = level.random.nextInt(10) != 0 ? Aspect.getPrimalAspects() : Aspect.getCompoundAspects();
			setWispType(pool.get(level.random.nextInt(pool.size())).getTag());
		}
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			discard();
			return;
		}

		double attackRange = 16.0;
		LivingEntity target = getTarget();
		if (target == null || !hasLineOfSight(target)) {
			if (currentFlightTarget != null && (!level.isEmptyBlock(currentFlightTarget) || currentFlightTarget.getY() < 1
					|| currentFlightTarget.getY() > level.getHeight(Heightmap.Types.MOTION_BLOCKING, currentFlightTarget.getX(), currentFlightTarget.getZ()) + 8)) {
				currentFlightTarget = null;
			}
			if (currentFlightTarget == null || random.nextInt(30) == 0 || distanceToSqr(centerOf(currentFlightTarget)) < 4.0) {
				currentFlightTarget = new BlockPos((int) getX() + random.nextInt(7) - random.nextInt(7),
						(int) getY() + random.nextInt(6) - 2, (int) getZ() + random.nextInt(7) - random.nextInt(7));
			}
			steerToward(currentFlightTarget.getX() + 0.5 - getX(), currentFlightTarget.getY() + 0.1 - getY(),
					currentFlightTarget.getZ() + 0.5 - getZ(), 0.5, 0.15f);
		} else if (distanceToSqr(target) > attackRange * attackRange / 2.0 && hasLineOfSight(target)) {
			steerToward(target.getX() - getX(), target.getY() + target.getEyeHeight() * 0.66 - getY(),
					target.getZ() - getZ(), 0.5, 0.5f);
		}

		if (getTarget() instanceof Player player && player.getAbilities().invulnerable) {
			setTarget(null);
		}
		if (getTarget() != null && getTarget().isDeadOrDying()) {
			setTarget(null);
		}

		aggroCooldown--;
		if (level.random.nextInt(1000) == 0 && (getTarget() == null || aggroCooldown-- <= 0)) {
			Player nearest = thaumcraft.common.entities.PlayerTargeting.nearestTargetable(level, this, 16.0);
			setTarget(nearest);
			if (nearest != null) {
				aggroCooldown = 50;
			}
		}

		if (isAlive() && getTarget() != null && getTarget().distanceToSqr(this) < attackRange * attackRange) {
			LivingEntity currentTarget = getTarget();
			double dx = currentTarget.getX() - getX();
			double dz = currentTarget.getZ() - getZ();
			float yaw = (float) (-Mth.atan2(dx, dz) * 180.0 / Math.PI);
			setYRot(yaw);
			yBodyRot = yaw;
			if (hasLineOfSight(currentTarget)) {
				attackCounter++;
				if (attackCounter == 20) {
					playSound(thaumcraft.common.registry.ThaumcraftSounds.ZAP.get(), 1.0f, 1.1f);
					sendZapFx(currentTarget);
					float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
					if (Math.abs(currentTarget.getDeltaMovement().x) > 0.1 || Math.abs(currentTarget.getDeltaMovement().y) > 0.1
							|| Math.abs(currentTarget.getDeltaMovement().z) > 0.1) {
						if (level.random.nextFloat() < 0.4f) {
							currentTarget.hurt(damageSources().mobAttack(this), damage);
						}
					} else if (level.random.nextFloat() < 0.66f) {
						currentTarget.hurt(damageSources().mobAttack(this), damage + 1.0f);
					}
					attackCounter = -20 + level.random.nextInt(20);
				}
			} else if (attackCounter > 0) {
				attackCounter--;
			}
		}
	}

	/** {@code (double, double, double, double, float)} - nudges velocity toward a relative offset and turns
	 * to face it, the exact spring-toward-point formula the original repeated twice (once for wandering,
	 * once for closing on a target) with only the horizontal/vertical pull strength and forward speed
	 * differing between the two call sites. */
	private void steerToward(double dx, double dy, double dz, double pull, float forwardSpeed) {
		var motion = getDeltaMovement();
		double mx = motion.x + (Math.signum(dx) * pull - motion.x) * 0.1;
		double my = motion.y + (Math.signum(dy) * (pull + 0.2) - motion.y) * 0.1;
		double mz = motion.z + (Math.signum(dz) * pull - motion.z) * 0.1;
		setDeltaMovement(mx, my, mz);
		float yaw = (float) (Math.atan2(mz, mx) * 180.0 / Math.PI) - 90.0f;
		setYRot(getYRot() + Mth.wrapDegrees(yaw - getYRot()));
		zza = forwardSpeed;
	}

	private Vec3 centerOf(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	private void sendZapFx(LivingEntity target) {
		try {
			PacketDistributor.sendToPlayersNear((ServerLevel) level(), null,
					getX(), getY(), getZ(), 32.0, new thaumcraft.common.lib.network.fx.PacketFXWispZap(getId(), target.getId()));
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Wisp zap FX packet failed to send (no real client connection?)", e);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return thaumcraft.common.registry.ThaumcraftSounds.WISPLIVE.get();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.LAVA_EXTINGUISH;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return thaumcraft.common.registry.ThaumcraftSounds.WISPDEAD.get();
	}

	@Override
	protected float getSoundVolume() {
		return 0.25f;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		Aspect aspect = Aspect.getAspect(getWispType());
		if (aspect != null) {
			spawnAtLocation(makeCrystal(aspect), 0.0f);
		}
	}

	/** Small, local stand-in for the original's {@code ThaumcraftApiHelper.makeCrystal} - same duplicate
	 * {@code ToolEvents} already carries locally rather than porting the whole unconverted class for it. */
	private static ItemStack makeCrystal(Aspect aspect) {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemGenericEssentiaContainer) stack.getItem()).setAspects(stack, new AspectList().add(aspect, 1));
		return stack;
	}

	/** The original's own {@code getCanSpawnHere}/{@code isValidLightLevel} darkness+density validity
	 * check, kept real (not simplified) since {@code EntityFluxRift}'s own direct-spawn call needs it even
	 * though this mob isn't wired into any natural world spawn list. */
	public boolean canSpawnHere() {
		Level level = level();
		int nearby = level.getEntitiesOfClass(EntityWisp.class, getBoundingBox().inflate(16.0)).size();
		return nearby < 8 && level.getDifficulty() != Difficulty.PEACEFUL && isValidLightLevel();
	}

	private boolean isValidLightLevel() {
		Level level = level();
		BlockPos pos = new BlockPos((int) getX(), (int) getBoundingBox().minY, (int) getZ());
		if (level.getBrightness(LightLayer.SKY, pos) > random.nextInt(32)) {
			return false;
		}
		int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
		return blockLight <= random.nextInt(8);
	}

	/** As the original, a small puff where it dies (the client hears of the death too). */
	@Override
	public void die(net.minecraft.world.damagesource.DamageSource source) {
		super.die(source);
		if (level().isClientSide) thaumcraft.client.fx.ClientFxHandlers.burst(level(), getX(), getY() + 0.45, getZ(), 1.0f);
	}
}

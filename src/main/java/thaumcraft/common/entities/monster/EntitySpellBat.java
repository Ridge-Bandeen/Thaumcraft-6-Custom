package thaumcraft.common.entities.monster;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code EntitySpellBat} - a real conjured familiar (the original was
 * {@code EntityMob}, not vanilla's own passive {@code Bat}, same "hostile-capable base" reasoning
 * {@link EntityFireBat}'s own doc comment already established), spawned by
 * {@code FocusMediumSpellBat#execute} to carry a real {@link FocusPackage} to a target and unleash it,
 * rather than dealing melee damage directly. Same hand-rolled erratic flight AI as
 * {@link EntityWisp}/{@link EntityFireBat} (no vanilla {@code Goal} to replace); self-expires after 600
 * ticks or once its owner is gone, matching the original exactly.
 *
 * <p>{@code friendly} mode (a real per-cast setting from {@code FocusMediumSpellBat}'s own {@code target}
 * node setting) targets entities allied with the owner instead of ones that aren't - real for beneficial
 * foci (Heal) cast at an ally rather than an enemy. Real, documented simplification: the original's own
 * {@code EntityUtils.isFriendly} ally check (team/ownership-aware) is replaced with vanilla's own real
 * {@link Entity#isAlliedTo}, which already covers the same team/tame-ownership cases without needing to
 * port that whole utility method.
 *
 * <p>As the original, its focus package goes to clients with the spawn ({@link #writeSpawnData}); there it trails
 * its effects' particles and takes their averaged colour ({@link #getColor}), which
 * {@link thaumcraft.client.render.RenderSpellBat} tints it with, see-through and full-bright.
 */
public class EntitySpellBat extends FlyingMob implements net.neoforged.neoforge.entity.IEntityWithComplexSpawn {

	private static final EntityDataAccessor<Boolean> DATA_FRIENDLY =
			SynchedEntityData.defineId(EntitySpellBat.class, EntityDataSerializers.BOOLEAN);

	private BlockPos currentFlightTarget;
	private LivingEntity owner;
	private UUID ownerUUID;
	private FocusPackage focusPackage;
	private int attackTime;

	public EntitySpellBat(EntityType<? extends EntitySpellBat> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 5.0)
				.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	/** Real secondary construction step, standing in for the original's own second constructor
	 * ({@code FocusPackage}, {@code boolean}) - modern entities only ever get the one real
	 * {@code (EntityType, Level)} constructor the registry calls, so extra setup happens here right after,
	 * matching this port's own established convention (e.g. {@code EntityThaumcraftGolem#initializeGolem}).
	 * {@code pack} is real but can legitimately be {@code null} - {@code FocusNode#getRemainingPackage()}
	 * returns {@code null} when this medium was the last node in its own package (nothing left for the bat
	 * to carry), matching the original's own {@code focusPackage} field going equally unset in that case; a
	 * spell bat with no real payload just discards itself on its next {@link #tick} instead of casting. */
	public void configureFocus(FocusPackage pack, boolean friendly) {
		this.focusPackage = pack;
		setOwner(pack != null ? pack.getCaster() : null);
		setFriendly(friendly);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FRIENDLY, false);
	}

	@Override
	protected void registerGoals() {
		// intentionally empty - see this class's own doc comment.
	}

	public boolean isFriendly() {
		return entityData.get(DATA_FRIENDLY);
	}

	public void setFriendly(boolean friendly) {
		entityData.set(DATA_FRIENDLY, friendly);
	}

	public void setOwner(LivingEntity owner) {
		this.owner = owner;
		this.ownerUUID = owner == null ? null : owner.getUUID();
	}

	public LivingEntity getOwner() {
		if (owner == null && ownerUUID != null && level() instanceof ServerLevel serverLevel) {
			Entity found = serverLevel.getEntity(ownerUUID);
			if (found instanceof LivingEntity livingEntity) owner = livingEntity;
		}
		return owner;
	}

	@Override
	public PlayerTeam getTeam() {
		LivingEntity realOwner = getOwner();
		return realOwner != null ? realOwner.getTeam() : super.getTeam();
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		LivingEntity realOwner = getOwner();
		if (other == realOwner) return true;
		if (realOwner != null && (realOwner.isAlliedTo(other) || other.isAlliedTo(realOwner))) return true;
		return super.isAlliedTo(other);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
		// no fall damage, matches the original's own no-op fall() override.
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide && (tickCount > 600 || getOwner() == null)) {
			discard();
		}
	}

	/** Client: the package's effects and their averaged colour, worked out once (the original's effects/color). */
	@javax.annotation.Nullable
	private thaumcraft.api.casters.FocusEffect[] effects;
	private int color = 0xFFFFFF;

	public int getColor() {
		return color;
	}

	@Override
	public void writeSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
		buffer.writeNbt(focusPackage != null ? focusPackage.serialize() : new net.minecraft.nbt.CompoundTag());
	}

	@Override
	public void readSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
		try {
			net.minecraft.nbt.CompoundTag tag = buffer.readNbt();
			if (tag != null && !tag.isEmpty()) {
				focusPackage = new FocusPackage();
				focusPackage.deserialize(tag, level());
			}
		} catch (Exception e) {
			thaumcraft.Thaumcraft.LOGGER.debug("Spell bat package could not be read", e);
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide) {
			if (isAlive() && focusPackage != null) {
				if (effects == null) {
					effects = focusPackage.getFocusEffects();
					if (effects.length > 0) {
						int r = 0, g = 0, b = 0;
						for (thaumcraft.api.casters.FocusEffect ef : effects) {
							int c = thaumcraft.api.casters.FocusEngine.getElementColor(ef.getKey());
							r += (c >> 16) & 255;
							g += (c >> 8) & 255;
							b += c & 255;
						}
						color = (r / effects.length) << 16 | (g / effects.length) << 8 | (b / effects.length);
					}
				}
				if (effects.length > 0) {
					thaumcraft.api.casters.FocusEffect eff = effects[random.nextInt(effects.length)];
					eff.renderParticleFX(level(), getX() + random.nextGaussian() * 0.125, getY() + getBbHeight() / 2.0f + random.nextGaussian() * 0.125,
							getZ() + random.nextGaussian() * 0.125, 0.0, 0.0, 0.0);
				}
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (attackTime > 0) {
			attackTime--;
		}

		if (getTarget() == null) {
			if (currentFlightTarget != null && (!level.isEmptyBlock(currentFlightTarget) || currentFlightTarget.getY() < 1)) {
				currentFlightTarget = null;
			}
			if (currentFlightTarget == null || random.nextInt(30) == 0 || distanceToSqr(centerOf(currentFlightTarget)) < 4.0) {
				currentFlightTarget = new BlockPos((int) getX() + random.nextInt(7) - random.nextInt(7),
						(int) getY() + random.nextInt(6) - 2, (int) getZ() + random.nextInt(7) - random.nextInt(7));
			}
			steerToward(currentFlightTarget.getX() + 0.5 - getX(), currentFlightTarget.getY() + 0.1 - getY(), currentFlightTarget.getZ() + 0.5 - getZ());
		} else {
			LivingEntity target = getTarget();
			steerToward(target.getX() - getX(), target.getY() + target.getEyeHeight() * 0.66 - getY(), target.getZ() - getZ());
		}

		if (getTarget() == null) {
			setTarget(findTargetToAttack());
		} else if (getTarget().isAlive()) {
			if (isAlive() && hasLineOfSight(getTarget())) {
				castAt(getTarget());
			}
		} else {
			setTarget(null);
		}
	}

	private void steerToward(double dx, double dy, double dz) {
		var motion = getDeltaMovement();
		double mx = motion.x + (Math.signum(dx) * 0.5 - motion.x) * 0.1;
		double my = motion.y + (Math.signum(dy) * 0.7 - motion.y) * 0.1;
		double mz = motion.z + (Math.signum(dz) * 0.5 - motion.z) * 0.1;
		setDeltaMovement(mx, my, mz);
		float yaw = (float) (Math.atan2(mz, mx) * 180.0 / Math.PI) - 90.0f;
		setYRot(getYRot() + Mth.wrapDegrees(yaw - getYRot()));
	}

	private Vec3 centerOf(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}

	/** Real translation of the original's own {@code attackEntity} - fires the carried {@link FocusPackage}
	 * at the target instead of dealing melee damage, then loses 1 HP itself (matches the original exactly -
	 * a spell bat wears itself out casting). */
	private void castAt(LivingEntity target) {
		if (focusPackage == null) {
			return;
		}
		if (attackTime > 0 || distanceTo(target) >= Math.max(2.5f, target.getBbWidth() * 1.1f)) {
			return;
		}
		if (target.getBoundingBox().maxY <= getBoundingBox().minY || target.getBoundingBox().minY >= getBoundingBox().maxY) {
			return;
		}
		attackTime = 40;
		Vec3 hitPos = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
		HitResult hit = new BlockHitResult(hitPos, net.minecraft.core.Direction.UP, target.blockPosition(), false);
		Trajectory trajectory = new Trajectory(position(), hitPos.subtract(position()));
		FocusEngine.runFocusPackage(focusPackage.copy(getOwner() != null ? getOwner() : this), new Trajectory[] { trajectory }, new HitResult[] { hit });
		setHealth(getHealth() - 1.0f);
		playSound(SoundEvents.BAT_HURT, 0.5f, 0.9f + random.nextFloat() * 0.2f);
	}

	private LivingEntity findTargetToAttack() {
		List<LivingEntity> nearby = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12.0));
		double best = Double.MAX_VALUE;
		LivingEntity found = null;
		for (LivingEntity candidate : nearby) {
			if (!candidate.isAlive() || candidate == this) continue;
			boolean allied = isAlliedTo(candidate);
			if (isFriendly() != allied) continue;
			double distSq = distanceToSqr(candidate);
			if (distSq < best) {
				best = distSq;
				found = candidate;
			}
		}
		return found;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BAT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.BAT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BAT_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.1f;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		if (ownerUUID != null) nbt.putUUID("OwnerUUID", ownerUUID);
		nbt.putBoolean("friendly", isFriendly());
		if (focusPackage != null) nbt.put("pack", focusPackage.serialize());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.hasUUID("OwnerUUID")) ownerUUID = nbt.getUUID("OwnerUUID");
		setFriendly(nbt.getBoolean("friendly"));
		if (nbt.contains("pack")) {
			focusPackage = new FocusPackage();
			focusPackage.deserialize(nbt.getCompound("pack"), level());
		}
	}

	/** Real darkness-based spawn check, same {@code LightLayer.BLOCK} query
	 * {@link EntityFireBat#canSpawnHere} already established - kept ready even though this mob is only ever
	 * conjured, never naturally spawned. */
	public boolean canSpawnHere() {
		return level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, blockPosition()) <= random.nextInt(7);
	}
}

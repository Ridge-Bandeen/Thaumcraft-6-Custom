package thaumcraft.common.entities.projectile;

import java.util.Comparator;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.lib.utils.EntityChecks;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityFocusProjectile}, backing {@link thaumcraft.common.items.casters.foci.FocusMediumProjectile}:
 * a bolt carrying the rest of the spell (the nodes after the Projectile medium), which runs against whatever it hits.
 * As the original, its "option" ({@link #getSpecial}) makes it plain (0), bouncy (1: rebounds off solid blocks,
 * losing a tenth of its speed each time, until it is too slow), or seeking (2: the nearest hostile, 3: the nearest
 * friendly thing in a cone ahead and in sight, re-checked every 5 ticks); it falls slowly (0.01 a tick, 0.005 when
 * seeking), lasts at most a minute, and vanishes if its caster is gone. The spell travels to the client with it, so the
 * trail is a mote in the colour of one of its effects, plus that effect's own particles.
 */
public class EntityFocusProjectile extends ThrowableProjectile implements IEntityWithComplexSpawn {

	private static final EntityDataAccessor<Integer> SPECIAL = SynchedEntityData.defineId(EntityFocusProjectile.class, EntityDataSerializers.INT);

	private FocusPackage remainingPackage;
	private Entity target;
	private FocusEffect[] effects;

	public EntityFocusProjectile(EntityType<? extends EntityFocusProjectile> type, Level level) {
		super(type, level);
	}

	public EntityFocusProjectile(LivingEntity caster, FocusPackage remainingPackage, Vec3 source, Vec3 direction, float speed, int special) {
		super(ThaumcraftEntities.FOCUS_PROJECTILE.get(), caster.level());
		setOwner(caster);
		this.remainingPackage = remainingPackage;
		double off = caster.getBbWidth() * 2.1;
		setPos(source.x + direction.x * off, source.y + direction.y * off, source.z + direction.z * off);
		shoot(direction.x, direction.y, direction.z, speed, 0.0f);
		setSpecial(special);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SPECIAL, 0);
	}

	public int getSpecial() {
		return entityData.get(SPECIAL);
	}

	public void setSpecial(int special) {
		entityData.set(SPECIAL, special);
	}

	@Override
	protected double getDefaultGravity() {
		return getSpecial() > 1 ? 0.005 : 0.01;
	}

	@Override
	public void tick() {
		super.tick();
		if (isRemoved()) return;
		if (tickCount > 1200 || (!level().isClientSide && getOwner() == null)) {
			discard();
			return;
		}
		if (level().isClientSide) {
			trail();
			return;
		}
		if (target == null && tickCount % 5 == 0 && getSpecial() > 1) {
			Vec3 look = getDeltaMovement();
			List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(16.0), LivingEntity::isAlive);
			near.sort(Comparator.comparingDouble(this::distanceToSqr));
			for (LivingEntity candidate : near) {
				if (distanceToSqr(candidate) > 256.0 || !EntityChecks.isInCone(1.75f, position(), look, candidate, 16.0f)
						|| !EntityChecks.canSee(this, position(), candidate)) continue;
				boolean friendly = EntityChecks.isFriendly(getOwner(), candidate);
				if ((friendly && getSpecial() == 3) || (!friendly && getSpecial() == 2)) {
					target = candidate;
					break;
				}
			}
		}
		if (target != null) {
			Vec3 to = new Vec3(target.getX() - getX(), target.getBoundingBox().minY + target.getBbHeight() * 0.6 - getY(), target.getZ() - getZ()).normalize();
			Vec3 mv = getDeltaMovement();
			double speed = mv.length();
			setDeltaMovement(mv.normalize().add(to.scale(0.275)).normalize().scale(speed));
			if (tickCount % 5 == 0 && (!target.isAlive() || !EntityChecks.isInCone(1.75f, position(), getDeltaMovement(), target, 16.0f)
					|| !EntityChecks.canSee(this, position(), target))) {
				target = null;
			}
		}
	}

	/** A mote in the colour of one of the spell's effects, and that effect's own particles. */
	private void trail() {
		if (effects == null) effects = remainingPackage != null ? remainingPackage.getFocusEffects() : new FocusEffect[0];
		if (effects == null || effects.length == 0) return;
		FocusEffect effect = effects[random.nextInt(effects.length)];
		int c = FocusEngine.getElementColor(effect.getKey());
		double y = getY() + getBbHeight() / 2.0;
		thaumcraft.client.fx.ClientFxHandlers.spawnFireMote(level(), getX(), y, getZ(),
				0.0125 * (random.nextFloat() - 0.5), 0.0125 * (random.nextFloat() - 0.5), 0.0125 * (random.nextFloat() - 0.5),
				((c >> 16) & 255) / 255.0f, ((c >> 8) & 255) / 255.0f, (c & 255) / 255.0f, 7.0f);
		effect.renderParticleFX(level(), getX() + random.nextGaussian() * 0.1, y + random.nextGaussian() * 0.1, getZ() + random.nextGaussian() * 0.1,
				random.nextGaussian() * 0.01, random.nextGaussian() * 0.01, random.nextGaussian() * 0.01);
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		if (getSpecial() == 1) {
			// bouncy: off anything solid (passing through grass and the like), slowing a tenth each time
			if (level().getBlockState(result.getBlockPos()).getCollisionShape(level(), result.getBlockPos()).isEmpty()) return;
			Direction side = result.getDirection();
			Vec3 m = getDeltaMovement();
			double mx = side.getStepX() != 0 ? -m.x : m.x;
			double my = side.getStepY() != 0 ? -m.y * 0.9 : m.y;
			double mz = side.getStepZ() != 0 ? -m.z : m.z;
			setDeltaMovement(mx * 0.9, my * 0.9, mz * 0.9);
			if (!level().isClientSide) {
				playSound(SoundEvents.LEASH_KNOT_PLACE, 0.25f, 1.0f);
				if (getDeltaMovement().length() < 0.2) discard();
			}
			return;
		}
		super.onHitBlock(result);
		resolveHit(result);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		resolveHit(new EntityHitResult(result.getEntity(), position()));
	}

	/** As the original: the rest of the spell runs against the hit next tick, from here, along the flight. */
	private void resolveHit(HitResult result) {
		if (level().isClientSide) return;
		if (remainingPackage != null) {
			FocusPackage pack = remainingPackage;
			Vec3 from = position();
			Vec3 direction = getDeltaMovement().lengthSqr() > 0 ? getDeltaMovement().normalize() : new Vec3(0, -1, 0);
			thaumcraft.common.lib.scheduling.BlockScheduler.addRunnableServer(level(),
					() -> FocusEngine.runFocusPackage(pack, new Trajectory[] { new Trajectory(from, direction) }, new HitResult[] { result }), 0);
		}
		discard();
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeNbt(remainingPackage != null ? remainingPackage.serialize() : new CompoundTag());
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf buffer) {
		CompoundTag tag = buffer.readNbt();
		if (tag != null && !tag.isEmpty()) {
			try {
				remainingPackage = new FocusPackage();
				remainingPackage.deserialize(tag, level());
			} catch (Exception e) {
				remainingPackage = null;
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (remainingPackage != null) tag.put("pack", remainingPackage.serialize());
		tag.putInt("special", getSpecial());
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setSpecial(tag.getInt("special"));
		if (tag.contains("pack")) {
			try {
				remainingPackage = new FocusPackage();
				remainingPackage.deserialize(tag.getCompound("pack"), level());
			} catch (Exception e) {
				remainingPackage = null;
			}
		}
	}
}

package thaumcraft.common.entities.projectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
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
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityFocusCloud}, backing {@link thaumcraft.common.items.casters.foci.FocusMediumCloud}:
 * a lingering cloud holding the rest of the spell. As the original, every 5 ticks it runs the spell on each living
 * thing within its radius and on blocks found along {@code radius} random rays out from it - each living thing or
 * block at most once every 2 seconds (shared by all clouds) - and draws nearby clouds in towards itself. It lasts its
 * duration (seconds), or until its caster is gone, and shows coloured puffs and its effects' particles.
 */
public class EntityFocusCloud extends Entity implements IEntityWithComplexSpawn {

	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(EntityFocusCloud.class, EntityDataSerializers.FLOAT);
	/** The original's cooldownMap: entity ids and block positions, to the time they may be hit again. */
	private static final Map<Long, Long> COOLDOWN = new ConcurrentHashMap<>();

	private FocusPackage focusPackage;
	@Nullable
	private UUID ownerId;
	@Nullable
	private LivingEntity owner;
	private int duration;
	private FocusEffect[] effects;

	public EntityFocusCloud(EntityType<? extends EntityFocusCloud> type, Level level) {
		super(type, level);
	}

	public EntityFocusCloud(LivingEntity caster, FocusPackage focusPackage, Trajectory trajectory, float radius, int durationSeconds) {
		super(ThaumcraftEntities.FOCUS_CLOUD.get(), caster.level());
		this.focusPackage = focusPackage;
		this.owner = caster;
		this.ownerId = caster.getUUID();
		this.duration = durationSeconds;
		setPos(trajectory.source.x, trajectory.source.y, trajectory.source.z);
		setRadius(radius);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(RADIUS, 0.5f);
	}

	public float getRadius() {
		return entityData.get(RADIUS);
	}

	public void setRadius(float radius) {
		entityData.set(RADIUS, radius);
		refreshDimensions();
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (RADIUS.equals(key)) refreshDimensions();
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return EntityDimensions.scalable(getRadius() * 2.0f, 0.5f);
	}

	@Nullable
	private LivingEntity getOwner() {
		if (owner == null && ownerId != null && level() instanceof ServerLevel server && server.getEntity(ownerId) instanceof LivingEntity living) {
			owner = living;
		}
		return owner;
	}

	@Override
	public void tick() {
		super.tick();
		float rad = getRadius();
		if (!level().isClientSide && (tickCount > duration * 20 || getOwner() == null)) {
			discard();
			return;
		}
		if (level().isClientSide) {
			if (effects == null) effects = focusPackage != null ? focusPackage.getFocusEffects() : new FocusEffect[0];
			if (effects == null || effects.length == 0) return;
			for (int a = 0; a < rad; a++) {
				FocusEffect effect = effects[random.nextInt(effects.length)];
				thaumcraft.client.fx.ClientFxHandlers.spawnFocusCloud(level(), getX() + random.nextGaussian() * rad / 2.0 * 0.85,
						getY() + random.nextGaussian() * rad / 2.0 * 0.85, getZ() + random.nextGaussian() * rad / 2.0 * 0.85,
						random.nextGaussian() * 0.01, random.nextGaussian() * 0.01, random.nextGaussian() * 0.01, FocusEngine.getElementColor(effect.getKey()));
				effect.renderParticleFX(level(), getX() + random.nextGaussian() * rad / 2.0, getY() + random.nextGaussian() * rad / 2.0,
						getZ() + random.nextGaussian() * rad / 2.0, random.nextGaussian() * 0.01, random.nextGaussian() * 0.01, random.nextGaussian() * 0.01);
			}
			return;
		}
		if (tickCount % 5 != 0 || focusPackage == null) return;
		long now = System.currentTimeMillis();
		List<Trajectory> trajectories = new ArrayList<>();
		List<HitResult> targets = new ArrayList<>();
		Vec3 here = position();
		// (the original's getEntitiesInRange: a box {@code rad} out each way)
		for (Entity e : level().getEntities(this, new AABB(here, here).inflate(rad), Entity::isAlive)) {
			if (e instanceof EntityFocusCloud other) {
				Vec3 v = other.position().subtract(here);
				other.move(MoverType.SELF, new Vec3(v.x / 50.0, v.y / 50.0, v.z / 50.0));
			}
			if (!(e instanceof LivingEntity)) continue;
			Long until = COOLDOWN.get((long) e.getId());
			if (until != null && until > now) continue;
			COOLDOWN.put((long) e.getId(), now + 2000L);
			Vec3 at = e.position().add(0, e.getBbHeight() / 2.0, 0);
			targets.add(new EntityHitResult(e, at));
			trajectories.add(new Trajectory(here, at.subtract(here)));
		}
		for (int a = 0; a < rad; a++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			BlockHitResult hit = level().clip(new ClipContext(here, here.add(dir.scale(rad)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (hit.getType() != HitResult.Type.BLOCK) continue;
			long key = hit.getBlockPos().asLong();
			Long until = COOLDOWN.get(key);
			if (until != null) {
				if (until > now) continue;
				COOLDOWN.remove(key);
			}
			targets.add(hit);
			trajectories.add(new Trajectory(here, dir));
			COOLDOWN.put(key, now + 2000L);
		}
		if (COOLDOWN.size() > 4096) COOLDOWN.values().removeIf(t -> t <= now);
		LivingEntity caster = getOwner();
		if (!targets.isEmpty() && caster != null) {
			FocusPackage copy = focusPackage.copy(caster);
			thaumcraft.common.lib.scheduling.BlockScheduler.addRunnableServer(level(),
					() -> FocusEngine.runFocusPackage(copy, trajectories.toArray(new Trajectory[0]), targets.toArray(new HitResult[0])), 0);
		}
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeNbt(focusPackage != null ? focusPackage.serialize() : new CompoundTag());
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf buffer) {
		focusPackage = readPackage(buffer.readNbt());
	}

	private FocusPackage readPackage(CompoundTag tag) {
		if (tag == null || tag.isEmpty()) return null;
		try {
			FocusPackage p = new FocusPackage();
			p.deserialize(tag, level());
			return p;
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putInt("Age", tickCount);
		tag.putInt("Duration", duration);
		tag.putFloat("Radius", getRadius());
		if (ownerId != null) tag.putUUID("OwnerUUID", ownerId);
		if (focusPackage != null) tag.put("pack", focusPackage.serialize());
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		tickCount = tag.getInt("Age");
		duration = tag.getInt("Duration");
		setRadius(tag.getFloat("Radius"));
		ownerId = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
		focusPackage = readPackage(tag.getCompound("pack"));
	}
}

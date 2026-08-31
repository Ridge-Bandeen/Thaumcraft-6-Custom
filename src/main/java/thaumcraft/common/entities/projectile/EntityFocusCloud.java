package thaumcraft.common.entities.projectile;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityFocusCloud} - a stationary, lingering "aura" sibling to
 * {@link EntityFocusProjectile}/{@link EntityFocusMine}: sits in place for its duration, periodically
 * re-triggering its remaining focus chain against every living entity currently inside its radius (a copy
 * of the chain per trigger pass, same as the original - the remaining package isn't consumed the way
 * Projectile/Mine's is, since a cloud can hit the same target more than once over its lifetime).
 *
 * Simplified for this pass, more so than {@code EntityFocusProjectile}/{@code EntityFocusMine}: the
 * original also raytraced random directions each pass to occasionally hit nearby blocks, and tracked a
 * per-target/per-block cooldown map to avoid re-triggering the same thing every single pass - both dropped
 * here in favor of "hit every living entity in range, every 5 ticks, no cooldown", which is simpler but a
 * real behavior difference worth remembering (a cloud focus will feel noticeably stronger/spammier against
 * anything that stays inside it than the original's cooldown-throttled version did). No client spawn-data
 * sync or particle rendering, same "no client FX" pattern as the other two carrier entities.
 */
public class EntityFocusCloud extends Entity {

	private FocusPackage focusPackage;
	private LivingEntity owner;
	private float radius = 0.5f;
	private int durationTicks;

	public EntityFocusCloud(EntityType<? extends EntityFocusCloud> type, Level level) {
		super(type, level);
	}

	public EntityFocusCloud(LivingEntity caster, FocusPackage focusPackage, Trajectory trajectory, float radius, int durationSeconds) {
		super(ThaumcraftEntities.FOCUS_CLOUD.get(), caster.level());
		this.focusPackage = focusPackage;
		this.owner = caster;
		this.radius = radius;
		this.durationTicks = durationSeconds * 20;
		setPos(trajectory.source.x, trajectory.source.y, trajectory.source.z);
		refreshDimensions();
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return EntityDimensions.scalable(radius * 2.0f, 0.5f);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		// no per-entity synced state - the carried FocusPackage is server-only, see the class doc comment.
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) return;

		if (tickCount > durationTicks || owner == null || focusPackage == null) {
			discard();
			return;
		}

		if (tickCount % 5 != 0) return;

		AABB searchBox = new AABB(position(), position()).inflate(radius);
		List<LivingEntity> nearby = level().getEntitiesOfClass(LivingEntity.class, searchBox, LivingEntity::isAlive);
		if (nearby.isEmpty()) return;

		Trajectory[] trajectories = new Trajectory[nearby.size()];
		HitResult[] targets = new HitResult[nearby.size()];
		for (int i = 0; i < nearby.size(); i++) {
			LivingEntity target = nearby.get(i);
			Vec3 hitPos = target.position().add(0, target.getBbHeight() / 2.0, 0);
			trajectories[i] = new Trajectory(position(), hitPos.subtract(position()).normalize());
			targets[i] = new EntityHitResult(target, hitPos);
		}
		FocusEngine.runFocusPackage(focusPackage.copy(owner), trajectories, targets);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		// noSave() on the entity type - nothing to persist, see the class doc comment.
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}

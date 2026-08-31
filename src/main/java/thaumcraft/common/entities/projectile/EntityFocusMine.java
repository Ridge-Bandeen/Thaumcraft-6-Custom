package thaumcraft.common.entities.projectile;

import java.util.List;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityFocusMine} - a "planted trap" sibling to
 * {@link EntityFocusProjectile}: flies out, lands and arms itself on the first thing it touches (block or
 * entity - it doesn't damage whatever it initially hits), then waits, scanning a short radius each tick for
 * a matching living entity to trigger its remaining focus chain against before disarming itself.
 *
 * Simplified for this pass, same category of cuts as {@code EntityFocusProjectile}: no arming delay
 * countdown or trigger-radius tuning (the original's {@code counter}/timing knobs) - arms immediately on
 * landing and triggers on the very next matching entity within 1 block, every 5 ticks. No client spawn-data
 * sync (server-only FocusPackage, matches this project's "no client FX" pattern). The original's
 * {@code EntityUtils.isFriendly(caster, target)} check isn't ported (needs team/ownership logic this
 * project hasn't built) - substituted a coarser heuristic: any {@link Monster} counts as "foe", everything
 * else (players, passive/neutral mobs) counts as "friend". Doesn't survive a chunk save/reload mid-flight,
 * same as {@code EntityFocusProjectile}.
 */
public class EntityFocusMine extends ThrowableProjectile {

	private FocusPackage focusPackage;
	private boolean friendlyTarget;
	private boolean armed;

	public EntityFocusMine(EntityType<? extends EntityFocusMine> type, Level level) {
		super(type, level);
	}

	public EntityFocusMine(LivingEntity caster, FocusPackage focusPackage, Trajectory trajectory, boolean friendlyTarget) {
		super(ThaumcraftEntities.FOCUS_MINE.get(), caster.level());
		setOwner(caster);
		this.focusPackage = focusPackage;
		this.friendlyTarget = friendlyTarget;
		setPos(trajectory.source.x, trajectory.source.y, trajectory.source.z);
		setDeltaMovement(trajectory.direction.normalize());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		// no per-entity synced state - the carried FocusPackage/armed flag are server-only.
	}

	@Override
	protected double getDefaultGravity() {
		return 0.03;
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		arm();
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		arm();
	}

	private void arm() {
		armed = true;
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide || !armed || focusPackage == null) return;
		if (tickCount % 5 != 0) return;

		AABB searchBox = getBoundingBox().inflate(1.0);
		List<LivingEntity> nearby = level().getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != getOwner() && e.isAlive());
		for (LivingEntity target : nearby) {
			boolean hostile = target instanceof Monster;
			if (friendlyTarget == hostile) continue; // friendlyTarget wants a non-hostile match, or vice versa

			Vec3 hitPos = target.position().add(0, target.getBbHeight() / 2.0, 0);
			FocusEngine.runFocusPackage(focusPackage, new Trajectory[] { new Trajectory(position(), hitPos.subtract(position()).normalize()) },
					new HitResult[] { new EntityHitResult(target, hitPos) });
			discard();
			return;
		}
	}
}

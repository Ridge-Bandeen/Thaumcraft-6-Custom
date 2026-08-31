package thaumcraft.common.entities.projectile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityFocusProjectile} - this port's first real entity, backing
 * {@link thaumcraft.common.items.casters.foci.FocusMediumProjectile}. Flies until it hits something, then
 * runs the rest of its focus chain ({@link thaumcraft.api.casters.FocusNode#getRemainingPackage}, the
 * effect nodes after the Projectile medium in the wand's node list) against whatever it hit, the same way
 * {@code FocusMediumTouch}'s raytrace hands a target straight to the next node - just resolved on impact
 * instead of instantly.
 *
 * Extends vanilla's {@link ThrowableProjectile} rather than reimplementing flight/collision by hand - it
 * already provides a complete, correct {@code tick()} (movement, gravity, water drag, hit detection via
 * {@code ProjectileUtil.getHitResultOnMoveVector}) that only needs {@link #onHitEntity}/{@link #onHitBlock}
 * overridden to plug in what happens on impact.
 *
 * Simplified for this pass versus the original:
 * <ul>
 * <li>No bounce/homing options ({@code FocusMediumProjectile}'s "option" setting) - a straight-flying bolt
 * only. The original's bounce/seeking behavior was a real feature, not a stand-in for something unported,
 * so this is a genuine scope cut rather than a missing-dependency workaround - worth adding properly
 * later rather than half-porting the physics.</li>
 * <li>No client spawn-data sync ({@code IEntityAdditionalSpawnData} in the original) for the carried
 * {@link FocusPackage} - it's only ever read server-side (on impact), and this project's established
 * "no client FX" simplification means there's nothing client-side that would need it anyway.</li>
 * <li>Doesn't survive a chunk save/reload mid-flight - {@code noSave()} on the entity type. A projectile
 * that's actually in flight when a chunk unloads is a rare enough edge case that dropping it entirely
 * (rather than persisting a serialized {@code FocusPackage}, which the original didn't do either) isn't
 * worth the complexity right now.</li>
 * <li>No particle trail or impact FX, same as every other focus in this package.</li>
 * </ul>
 */
public class EntityFocusProjectile extends ThrowableProjectile {

	private FocusPackage remainingPackage;

	public EntityFocusProjectile(EntityType<? extends EntityFocusProjectile> type, Level level) {
		super(type, level);
	}

	public EntityFocusProjectile(LivingEntity caster, FocusPackage remainingPackage, Vec3 source, Vec3 direction, float speed) {
		super(ThaumcraftEntities.FOCUS_PROJECTILE.get(), caster.level());
		setOwner(caster);
		this.remainingPackage = remainingPackage;
		setPos(source.x, source.y, source.z);
		setDeltaMovement(direction.normalize().scale(speed));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		// no per-entity synced state - the carried FocusPackage is server-only, see the class doc comment.
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0; // a magic bolt, not a thrown item - flies straight, matching the original's low (not zero) drop
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		resolveHit(result);
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		resolveHit(result);
	}

	private void resolveHit(HitResult result) {
		if (!level().isClientSide && remainingPackage != null) {
			Vec3 direction = getDeltaMovement().lengthSqr() > 0 ? getDeltaMovement().normalize() : new Vec3(0, -1, 0);
			FocusEngine.runFocusPackage(remainingPackage, new Trajectory[] { new Trajectory(position(), direction) }, new HitResult[] { result });
		}
		discard();
	}
}

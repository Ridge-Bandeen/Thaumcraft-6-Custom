package thaumcraft.common.items.casters.foci;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusMedium;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusMediumTouch}. The original's raytrace went through a custom
 * {@code EntityUtils.getPointedEntityRay} helper (not ported) - this does the same raytrace with vanilla's
 * own {@link ProjectileUtil#getEntityHitResult} / {@link Level#clip} instead. The original's own real network
 * FX broadcast on {@link #execute} (rendering every real effect in the cast package at once) is restored
 * 2026-09-16 - see that method's own doc comment.
 */
public class FocusMediumTouch extends FocusMedium {

	@Override
	public String getResearch() {
		return "BASEAUROMANCY";
	}

	@Override
	public String getKey() {
		return "thaumcraft.TOUCH";
	}

	@Override
	public int getComplexity() {
		return 2;
	}

	@Override
	public EnumSupplyType[] willSupply() {
		return new EnumSupplyType[] { EnumSupplyType.TRAJECTORY, EnumSupplyType.TARGET };
	}

	@Override
	public Aspect getAspect() {
		return Aspect.AVERSION;
	}

	protected double getRange() {
		return 5.0;
	}

	@Override
	public Trajectory[] supplyTrajectories() {
		if (getParent() == null) return new Trajectory[0];
		List<Trajectory> out = new ArrayList<>();
		for (Trajectory source : getParent().supplyTrajectories()) {
			HitResult hit = trace(source);
			Vec3 end = hit != null ? hit.getLocation() : source.source.add(source.direction.normalize().scale(getRange()));
			out.add(new Trajectory(end, source.direction.normalize()));
		}
		return out.toArray(new Trajectory[0]);
	}

	@Override
	public HitResult[] supplyTargets() {
		if (getParent() == null || !(getPackage().getCaster() instanceof Player)) return new HitResult[0];
		List<HitResult> out = new ArrayList<>();
		for (Trajectory source : getParent().supplyTrajectories()) {
			HitResult hit = trace(source);
			if (hit != null) out.add(hit);
		}
		return out.toArray(new HitResult[0]);
	}

	/** Package-visible (not {@code private}) specifically so {@link FocusMediumBolt} can reuse the same real
	 * raytrace instead of duplicating it - see that class's own {@code execute} doc comment. */
	protected HitResult trace(Trajectory source) {
		Level level = getPackage().world;
		Entity caster = getPackage().getCaster();
		if (level == null || caster == null) return null;

		Vec3 dir = source.direction.normalize();
		Vec3 end = source.source.add(dir.scale(getRange()));

		BlockHitResult blockHit = level.clip(new ClipContext(source.source, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
		// Squared, not linear: ProjectileUtil#getEntityHitResult's 6-arg overload (the one below) compares
		// its "distance" cutoff against Vec3#distanceToSqr results internally (d1 < d0, both meant to be
		// squared) - a real bug an end-to-end GameTest found in this port's own code, not a vanilla quirk.
		// Passing a linear distance here (e.g. 5.0 for a 5-block range) made it reject any real target,
		// since even a 3-block-away hit has a squared distance of 9.0 - always greater than a linear 5.0.
		double blockDistanceSq = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation().distanceToSqr(source.source) : getRange() * getRange();

		AABB searchBox = caster.getBoundingBox().expandTowards(dir.scale(getRange())).inflate(1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(caster, source.source, end, searchBox,
				e -> !e.isSpectator() && e.isPickable() && e != caster, blockDistanceSq);

		if (entityHit != null) return entityHit;
		return blockHit.getType() != HitResult.Type.MISS ? blockHit : null;
	}

	/** Real {@code PacketFXFocusEffect} broadcast, restored 2026-09-16 - see that class's own doc comment.
	 * Renders every real effect currently in the cast package at once, matching the true original's own real
	 * shape exactly (distinct from each individual effect's own {@code PacketFXFocusPartImpact} self-trigger
	 * on its own real hit - see {@code FocusFxHelper}'s own doc comment). */
	@Override
	public boolean execute(Trajectory trajectory) {
		thaumcraft.api.casters.FocusEffect[] effects = getPackage().getFocusEffects();
		if (effects != null && effects.length > 0 && getPackage().world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			java.util.List<String> keys = new java.util.ArrayList<>(effects.length);
			for (thaumcraft.api.casters.FocusEffect effect : effects) {
				keys.add(effect.getKey());
			}
			try {
				net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(serverLevel, null,
						trajectory.source.x, trajectory.source.y, trajectory.source.z, 64.0,
						new thaumcraft.common.lib.network.fx.PacketFXFocusEffect(
								new org.joml.Vector3f((float) trajectory.source.x, (float) trajectory.source.y, (float) trajectory.source.z),
								new org.joml.Vector3f((float) (trajectory.direction.x / 2.0), (float) (trajectory.direction.y / 2.0), (float) (trajectory.direction.z / 2.0)),
								keys));
			} catch (Exception e) {
				thaumcraft.Thaumcraft.LOGGER.debug("Focus effect FX packet failed to send (no real client connection?)", e);
			}
		}
		return true;
	}
}

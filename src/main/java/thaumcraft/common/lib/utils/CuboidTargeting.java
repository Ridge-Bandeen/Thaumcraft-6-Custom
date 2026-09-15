package thaumcraft.common.lib.utils;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Real, minimal modern replacement for the true original's {@code thaumcraft.codechicken.lib.raytracer}
 * package ({@code RayTracer}/{@code IndexedCuboid6}) - the library the true original leaned on for its own
 * real "which of several small labeled hitboxes is the player actually looking at" click-precision UI
 * (the Tube family's per-face toggles and central facing-cycle button; Redstone Relay's 2 separate dials;
 * the Levitator's own single button nub).
 *
 * <p>That whole library existed to work around a real 1.12.2 limitation this port doesn't have: a block
 * back then only ever had one fixed collision/selection box, so precise sub-block click targeting needed a
 * hand-rolled secondary raytrace against a tile's own registered small boxes, done independently of the
 * block's real collision shape. {@code IndexedCuboid6} was just an {@code AABB} plus a label; {@code
 * RayTracer#rayTraceCuboid} hand-rolled a per-face-plane ray/box intersection test to find the nearest one
 * a ray actually hit. Modern vanilla's own {@link AABB#clip(Vec3, Vec3)} already does that exact
 * computation, so this class is only the "iterate several labeled boxes, keep the nearest real hit" loop
 * around it - the real equivalent of {@code RayTracer#rayTraceCuboids}, in about a tenth of the code, no
 * third-party library needed.
 *
 * <p>Deliberately independent of whatever the block's own general collision/interaction shape is (matching
 * the true original's own real design: {@code RayTracer.retraceBlock} always did its own fresh raytrace
 * rather than reusing the game's already-resolved general "what block is the player looking at" result) -
 * every real call site here traces fresh from the player's own current eye position and look vector, out to
 * their own real block-interaction reach.
 */
public final class CuboidTargeting {

	/** One real candidate hitbox: a label plus its real world-space {@link AABB}. */
	public record Candidate<T>(T label, AABB box) {
	}

	/**
	 * Returns the label of whichever real candidate the player's own current line of sight hits nearest,
	 * within their own real block-interaction reach, or {@code null} if none of them are actually being
	 * looked at right now.
	 */
	@Nullable
	public static <T> T pick(Player player, List<Candidate<T>> candidates) {
		Vec3 from = player.getEyePosition(1.0f);
		Vec3 to = from.add(player.getViewVector(1.0f).scale(player.blockInteractionRange()));

		T best = null;
		double bestDistanceSq = Double.MAX_VALUE;
		for (Candidate<T> candidate : candidates) {
			Optional<Vec3> hit = candidate.box().clip(from, to);
			if (hit.isEmpty()) continue;
			double distanceSq = hit.get().distanceToSqr(from);
			if (distanceSq < bestDistanceSq) {
				bestDistanceSq = distanceSq;
				best = candidate.label();
			}
		}
		return best;
	}

	private CuboidTargeting() {
	}
}

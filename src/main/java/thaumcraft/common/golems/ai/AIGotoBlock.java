package thaumcraft.common.golems.ai;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code AIGotoBlock} - the {@link AIGoto} that seeks out block-position tasks
 * (currently {@code SealLumber}/{@code SealHarvest}/{@code SealBreaker}'s own block-position tasks, and
 * {@code SealProvide}'s "fetch"/"deliver" tasks - see each class's own doc comment). Direct translation of
 * the original's own "stand in whichever open horizontal space is closest to the target block" approach
 * ({@link #getAdjacentSpace}); {@link #canEasilyReach} restores the original's own real pathfinding-based
 * check - see that method's own doc comment.
 */
public class AIGotoBlock extends AIGoto {

	public AIGotoBlock(EntityThaumcraftGolem golem) {
		super(golem);
	}

	@Override
	protected void moveTo() {
		if (targetBlock != null) {
			golem.getNavigation().moveTo(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5, golem.getGolemMoveSpeed());
		} else if (golem.getTask() != null) {
			BlockPos p = golem.getTask().getPos();
			golem.getNavigation().moveTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, golem.getGolemMoveSpeed());
		}
	}

	@Override
	protected boolean findDestination() {
		ArrayList<Task> list = TaskHandler.getBlockTasksSorted(golem.level().dimension(), golem.getUUID(), golem);
		for (Task ticket : list) {
			BlockPos pos = ticket.getPos();
			if (areGolemTagsValidForTask(ticket) && ticket.canGolemPerformTask(golem)
					&& golem.isWithinRestriction(pos)
					&& isValidDestination(golem.level(), pos)
					&& canEasilyReach(pos)) {
				targetBlock = getAdjacentSpace(pos);
				golem.setTask(ticket);
				ticket.setReserved(true);
				golem.emote(thaumcraft.common.golems.EntityThaumcraftGolem.EMOTE_ORDER);
				return true;
			}
		}
		return false;
	}

	/** Picks whichever of the 4 horizontal neighbors of {@code pos} is both open (doesn't block motion) and
	 * closest to the golem - real translation of the original's own {@code getAdjacentSpace}. */
	private BlockPos getAdjacentSpace(BlockPos pos) {
		double best = Double.MAX_VALUE;
		BlockPos closest = null;
		for (Direction face : Direction.Plane.HORIZONTAL) {
			BlockPos candidate = pos.relative(face);
			if (!golem.level().getBlockState(candidate).blocksMotion()) {
				double dist = golem.distanceToSqr(Vec3.atCenterOf(candidate));
				if (dist < best) {
					best = dist;
					closest = candidate;
				}
			}
		}
		return closest;
	}

	/** Real port of the original's own real check, with one deliberate real addition: close enough outright,
	 * or - for anything up to a flat 6-block outer bound - the navigator's own computed path actually has to
	 * land within about a block and a half of the real target. Pure straight-line distance alone can't tell
	 * "6 blocks away in the same room" from "6 blocks away through a solid wall", so a golem could "reach" a
	 * task it could never actually walk to; a real computed path can't be fooled by a wall the same way. The
	 * outer 6-block bound itself is new versus the original (which had none) - {@code getBlockTasksSorted}
	 * hands back every block task in the whole dimension, so pathfinding unconditionally against all of them
	 * would be a real performance risk for busy worlds, and it's also the original's own real precedent for
	 * "too generous" (see the earlier doc comment history on this method) reused here as the outer cutoff. */
	private boolean canEasilyReach(BlockPos pos) {
		double distSq = golem.distanceToSqr(Vec3.atCenterOf(pos));
		if (distSq < minDist) return true;
		if (distSq >= Math.max(minDist, 36.0)) return false;
		Path path = golem.getNavigation().createPath(pos, 0);
		if (path == null) return false;
		Node end = path.getEndNode();
		if (end == null) return false;
		int dx = end.x - pos.getX();
		int dz = end.z - pos.getZ();
		int dy = end.y - pos.getY();
		if (dx == 0 && dz == 0 && dy == 2) dy--;
		return dx * dx + dy * dy + dz * dz < 2.25;
	}
}

package thaumcraft.common.golems.ai;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code AIGotoBlock} - the {@link AIGoto} that seeks out block-position tasks
 * (currently {@code SealLumber}'s "chop this log" tasks - see that class's own doc comment). Direct
 * translation of the original's own "stand in whichever open horizontal space is closest to the target
 * block" approach ({@link #getAdjacentSpace}); {@link #canEasilyReach} is simplified the same way
 * {@link AIGotoEntity}'s own is - a generous flat distance ceiling instead of inspecting the navigator's
 * own computed path endpoint.
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

	private boolean canEasilyReach(BlockPos pos) {
		return golem.distanceToSqr(Vec3.atCenterOf(pos)) < Math.max(minDist, 256.0);
	}
}

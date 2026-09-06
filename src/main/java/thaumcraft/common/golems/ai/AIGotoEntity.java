package thaumcraft.common.golems.ai;

import java.util.ArrayList;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code AIGotoEntity} - the {@link AIGoto} that seeks out entity-targeted tasks
 * (currently {@code SealGuard}/{@code SealButcher}'s "target this entity" tasks and {@code SealPickup}'s
 * "grab this dropped item" tasks - see each class's own doc comment). Direct translation, including its
 * sibling {@link AIGotoBlock#canEasilyReach}'s own real pathfinding-based reachability check - see that
 * method's own doc comment.
 */
public class AIGotoEntity extends AIGoto {

	public AIGotoEntity(EntityThaumcraftGolem golem) {
		super(golem);
	}

	@Override
	protected void moveTo() {
		Task task = golem.getTask();
		if (task != null && task.getEntity() != null) {
			golem.getNavigation().moveTo(task.getEntity(), golem.getGolemMoveSpeed());
		}
	}

	@Override
	protected boolean findDestination() {
		ArrayList<Task> list = TaskHandler.getEntityTasksSorted(golem.level().dimension(), golem.getUUID(), golem);
		for (Task ticket : list) {
			Entity target = ticket.getEntity();
			if (target == null) continue;
			if (areGolemTagsValidForTask(ticket) && ticket.canGolemPerformTask(golem)
					&& golem.isWithinRestriction(target.blockPosition())
					&& isValidDestination(golem.level(), target.blockPosition())
					&& canEasilyReach(target)) {
				golem.setTask(ticket);
				ticket.setReserved(true);
				minDist = 3.5 + target.getBbWidth() / 2.0 * (target.getBbWidth() / 2.0);
				return true;
			}
		}
		return false;
	}

	/** See {@link AIGotoBlock#canEasilyReach}'s own doc comment - same real pathfinding-based check, same
	 * outer 6-block bound. */
	private boolean canEasilyReach(Entity target) {
		double distSq = golem.distanceToSqr(target);
		if (distSq < minDist) return true;
		if (distSq >= Math.max(minDist, 36.0)) return false;
		Path path = golem.getNavigation().createPath(target, 0);
		if (path == null) return false;
		Node end = path.getEndNode();
		if (end == null) return false;
		var pos = target.blockPosition();
		int dx = end.x - pos.getX();
		int dz = end.z - pos.getZ();
		int dy = end.y - pos.getY();
		if (dx == 0 && dz == 0 && dy == 2) dy--;
		return dx * dx + dy * dy + dz * dz < 2.25;
	}
}

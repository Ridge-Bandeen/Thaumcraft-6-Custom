package thaumcraft.common.golems.ai;

import java.util.ArrayList;

import net.minecraft.world.entity.Entity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code AIGotoEntity} - the {@link AIGoto} that seeks out entity-targeted tasks
 * (currently only {@code SealGuard}'s "watch this entity" tasks - see that class's own doc comment). Direct
 * translation, with {@link #canEasilyReach} simplified: the original checked how close the navigator's own
 * computed path actually ended up to the target; this just checks real distance against a generous
 * (256-block-squared) ceiling instead - simpler, and {@code SealGuard} tasks resolve instantly in
 * {@code onTaskStarted} (they never actually need the golem to finish walking anywhere), so the fine
 * path-endpoint check the original made barely mattered for the one seal type this port ships so far.
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

	private boolean canEasilyReach(Entity target) {
		return golem.distanceToSqr(target) < Math.max(minDist, 256.0);
	}
}

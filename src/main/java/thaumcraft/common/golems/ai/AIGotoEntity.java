package thaumcraft.common.golems.ai;

import java.util.ArrayList;

import net.minecraft.world.entity.Entity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code AIGotoEntity} - the {@link AIGoto} that seeks out entity-targeted tasks
 * (currently {@code SealGuard}/{@code SealButcher}'s "target this entity" tasks and {@code SealPickup}'s
 * "grab this dropped item" tasks - see each class's own doc comment). Direct translation, with
 * {@link #canEasilyReach} simplified: the original checked how close the navigator's own computed path
 * actually ended up to the target; this checks real distance against a flat ceiling instead (see that
 * method's own doc comment for the real bug a GameTest caught in an earlier, too-generous version of this
 * check).
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
		// See AIGotoBlock's own doc comment on its sibling method - same real fix, tightened from a 256
		// (16-block) ceiling down to 36 (6 blocks), caught by cross-seal task interference in a GameTest.
		return golem.distanceToSqr(target) < Math.max(minDist, 36.0);
	}
}

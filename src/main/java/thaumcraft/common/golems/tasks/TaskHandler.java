package thaumcraft.common.golems.tasks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;

/**
 * Replaces the old (1.12.2) {@code TaskHandler}. Direct translation on top of {@link GolemHelper}'s own
 * real per-dimension task map (was this class's own {@code ConcurrentHashMap} field - {@code GolemHelper}
 * holds the canonical state now so api-package code never needs to depend on common-package code, see that
 * class's own doc comment).
 *
 * {@link #getBlockTasksSorted}/{@link #getEntityTasksSorted} both do the same real "closest unreserved
 * task for this golem, priority-weighted (each priority point worth 256 blocks-squared of distance
 * advantage)" insertion sort as the original.
 */
public final class TaskHandler {

	public static void addTask(ResourceKey<Level> dim, Task task) {
		GolemHelper.addGolemTask(dim, task);
	}

	public static Task getTask(ResourceKey<Level> dim, int id) {
		return GolemHelper.getTasks(dim).get(id);
	}

	public static Map<Integer, Task> getTasks(ResourceKey<Level> dim) {
		return GolemHelper.getTasks(dim);
	}

	public static ArrayList<Task> getBlockTasksSorted(ResourceKey<Level> dim, UUID golemId, Entity golemEntity) {
		return sortTasks(dim, golemId, golemEntity, (byte) 0);
	}

	public static ArrayList<Task> getEntityTasksSorted(ResourceKey<Level> dim, UUID golemId, Entity golemEntity) {
		return sortTasks(dim, golemId, golemEntity, (byte) 1);
	}

	private static ArrayList<Task> sortTasks(ResourceKey<Level> dim, UUID golemId, Entity golemEntity, byte type) {
		ArrayList<Task> out = new ArrayList<>();
		for (Task ticket : GolemHelper.getTasks(dim).values()) {
			if (ticket.isReserved() || ticket.getType() != type) continue;
			if (golemId != null && ticket.getGolemUUID() != null && !golemId.equals(ticket.getGolemUUID())) continue;
			if (type == 1) {
				if (ticket.getEntity() == null || !ticket.getEntity().isAlive()) {
					ticket.setSuspended(true);
					continue;
				}
			}
			insertSorted(out, ticket, golemEntity);
		}
		return out;
	}

	private static void insertSorted(ArrayList<Task> out, Task ticket, Entity golemEntity) {
		double d = ticket.getPos().distSqr(golemEntity.blockPosition()) - ticket.getPriority() * 256.0;
		for (int a = 0; a < out.size(); a++) {
			double d2 = out.get(a).getPos().distSqr(golemEntity.blockPosition()) - out.get(a).getPriority() * 256.0;
			if (d < d2) {
				out.add(a, ticket);
				return;
			}
		}
		out.add(ticket);
	}

	public static void completeTask(Task task, EntityThaumcraftGolem golem) {
		if (task.isCompleted() || task.isSuspended()) return;
		ISealEntity se = GolemHelper.getSealEntity(golem.level().dimension(), task.getSealPos());
		if (se != null) {
			task.setCompletion(se.getSeal().onTaskCompletion(golem.level(), golem, task));
		} else {
			task.setCompletion(true);
		}
	}

	/** Called once every 20 ticks per dimension (see {@code Thaumcraft#tickGolemSeals}) - ages down every
	 * still-active task's lifespan, and drops any suspended-or-expired task after giving its owning seal
	 * (if any) a chance to react via {@code onTaskSuspension}. */
	public static void clearSuspendedOrExpiredTasks(Level level) {
		ResourceKey<Level> dim = level.dimension();
		Iterator<Task> it = GolemHelper.getTasks(dim).values().iterator();
		while (it.hasNext()) {
			Task ticket = it.next();
			if (!ticket.isSuspended() && ticket.getLifespan() > 0L) {
				ticket.setLifespan((short) (ticket.getLifespan() - 1));
			} else {
				ISealEntity se = GolemHelper.getSealEntity(dim, ticket.getSealPos());
				if (se != null && se.getSeal() != null) {
					se.getSeal().onTaskSuspension(level, ticket);
				}
				it.remove();
			}
		}
	}

	private TaskHandler() {
	}
}

package thaumcraft.common.golems.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code AIGoto} - the shared base a task-seeking golem {@link Goal} extends
 * (currently just {@link AIGotoEntity}; a future {@code AIGotoBlock} for block-position tasks, needed by
 * seal types like the still-unported Harvest/Lumber, can extend this the same way). {@code shouldExecute}→
 * {@link #canUse}, {@code startExecuting}→{@link #start}, {@code shouldContinueExecuting}→
 * {@link #canContinueToUse}, {@code updateTask}→{@link #tick}, {@code resetTask}→{@link #stop} - modern
 * {@link Goal}'s lifecycle is conceptually identical to the original's own pre-cursor {@code EntityAIBase},
 * just renamed.
 *
 * Real, documented simplification: the original's {@code updateTask} detected a "stuck" golem (hadn't
 * moved in 5 ticks) and re-pathed toward a random point near the task instead of repeating the same
 * {@link #moveTo}; this just re-issues {@link #moveTo} every 5 ticks unconditionally - simpler, and
 * pathfinding recovers on its own in the overwhelming majority of real cases anyway.
 */
public abstract class AIGoto extends Goal {

	protected final EntityThaumcraftGolem golem;
	protected int taskCounter = -1;
	protected double minDist = 4.0;
	protected BlockPos targetBlock;
	private int cooldown;
	private int pause;

	protected AIGoto(EntityThaumcraftGolem golem) {
		this.golem = golem;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		cooldown = 5;
		if (golem.getTask() != null && !golem.getTask().isSuspended()) {
			return false;
		}
		targetBlock = null;
		boolean start = findDestination();
		if (start && golem.getTask() != null && golem.getTask().getSealPos() != null) {
			ISealEntity se = GolemHelper.getSealEntity(golem.level().dimension(), golem.getTask().getSealPos());
			if (se != null) {
				se.getSeal().onTaskStarted(golem.level(), golem, golem.getTask());
			}
		}
		return start;
	}

	@Override
	public void start() {
		moveTo();
		taskCounter = 0;
	}

	protected abstract void moveTo();

	@Override
	public boolean canContinueToUse() {
		Task task = golem.getTask();
		return taskCounter >= 0 && taskCounter <= 1000 && task != null && !task.isSuspended() && isValidDestination(golem.level(), task.getPos());
	}

	@Override
	public void tick() {
		Task task = golem.getTask();
		if (task == null) return;
		if (pause-- > 0) return;
		double dist = task.getType() == 0
				? golem.distanceToSqr(Vec3.atCenterOf(targetBlock != null ? targetBlock : task.getPos()))
				: (task.getEntity() != null ? golem.distanceToSqr(task.getEntity()) : Double.MAX_VALUE);
		if (dist > minDist) {
			task.setCompletion(false);
			taskCounter++;
			if (taskCounter % 5 == 0) {
				moveTo();
			}
		} else {
			TaskHandler.completeTask(task, golem);
			if (golem.getTask() != null && golem.getTask().isCompleted()) {
				if (taskCounter >= 0) taskCounter = 0;
				pause = 0;
			} else {
				pause = 10;
				taskCounter++;
			}
			taskCounter--;
		}
	}

	@Override
	public void stop() {
		Task task = golem.getTask();
		if (task != null) {
			if (!task.isCompleted() && task.isReserved()) {
				golem.emote(thaumcraft.common.golems.EntityThaumcraftGolem.EMOTE_GAVE_UP);
			}
			if (task.isCompleted() && !task.isSuspended()) {
				task.setSuspended(true);
			}
			task.setReserved(false);
		}
	}

	protected abstract boolean findDestination();

	protected boolean isValidDestination(Level level, BlockPos pos) {
		return true;
	}

	/** Checks the task's own seal (if any) actually allows this golem to work it - locked to a different
	 * owner, or missing/carrying a required/forbidden trait. */
	protected boolean areGolemTagsValidForTask(Task ticket) {
		ISealEntity se = GolemHelper.getSealEntity(golem.level().dimension(), ticket.getSealPos());
		if (se == null || se.getSeal() == null) return true;
		if (se.isLocked() && (golem.getOwnerUUID() == null || !golem.getOwnerUUID().toString().equals(se.getOwner()))) {
			return false;
		}
		if (se.getSeal().getRequiredTags() != null) {
			for (EnumGolemTrait tag : se.getSeal().getRequiredTags()) {
				if (!golem.getProperties().hasTrait(tag)) return false;
			}
		}
		if (se.getSeal().getForbiddenTags() != null) {
			for (EnumGolemTrait tag : se.getSeal().getForbiddenTags()) {
				if (golem.getProperties().hasTrait(tag)) return false;
			}
		}
		return true;
	}
}

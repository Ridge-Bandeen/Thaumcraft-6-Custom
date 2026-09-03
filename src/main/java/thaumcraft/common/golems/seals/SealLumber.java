package thaumcraft.common.golems.seals;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealConfigArea;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.ISealGui;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealLumber} - the first block-position {@link ISeal} this port ships
 * (see {@code thaumcraft.common.golems.ai.AIGotoBlock}, built for exactly this seal). Scans its area for
 * real log blocks (the modern {@link BlockTags#LOGS} tag - a clean real replacement for the original's own
 * {@code Utils.isWoodLog} triple-check of {@code isWood}/{@code canSustainLeaves}/ore-dictionary lookup,
 * all three of which were really just trying to answer "is this a log" without a shared tag to lean on
 * back then) and turns each one into a real chop task.
 *
 * Real, documented simplification: the original's own {@code onTaskCompletion} called
 * {@code BlockUtils.breakFurthestBlock} - a real tree-felling search that walked the whole connected trunk
 * and broke the farthest-out log first, working back down (so a golem would fell an entire tree, not just
 * the one logged block). {@code BlockUtils}/that search algorithm aren't ported (still 1.12.2, and a
 * genuinely separate, reusable piece of infrastructure). This just breaks the exact logged block for real
 * drops via {@code Level#destroyBlock} - still a real, working lumberjack (it finds every log in its area
 * over time, not just one), just without the single-swing whole-tree felling.
 */
public class SealLumber implements ISeal, ISealGui, ISealConfigArea {

	private int delay = new Random().nextInt(33);
	/** Positions with an already-active, untracked-by-id chop task - a real simplification of the original's
	 * own {@code HashMap<Integer, Long>} (task id -> position); tracking by position directly is simpler and
	 * just as effective at preventing duplicate tasks for the same log. */
	private final Set<Long> tracked = new HashSet<>();
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_lumber");

	@Override
	public String getKey() {
		return "thaumcraft:lumber";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay % 100 == 0) {
			// self-heal: a tracked position whose task got suspended/expired without ever reaching
			// onTaskCompletion (golem died, task timed out, etc.) would otherwise block that log forever.
			Iterator<Long> it = tracked.iterator();
			while (it.hasNext()) {
				BlockPos p = BlockPos.of(it.next());
				if (!isLog(world, p)) it.remove();
			}
		}
		delay++;
		BlockPos p = GolemHelper.getPosInArea(seal, delay);
		if (!tracked.contains(p.asLong()) && isLog(world, p)) {
			Task task = new Task(seal.getSealPos(), p);
			task.setPriority(seal.getPriority());
			TaskHandler.addTask(world.dimension(), task);
			tracked.add(p.asLong());
		}
	}

	private static boolean isLog(Level world, BlockPos pos) {
		return world.getBlockState(pos).is(BlockTags.LOGS);
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		if (isLog(world, task.getPos())) {
			golem.swingArm();
			world.destroyBlock(task.getPos(), true);
			golem.addRankXp(1);
		}
		tracked.remove(task.getPos().asLong());
		task.setSuspended(true);
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		if (isLog(golem.getGolemWorld(), task.getPos())) {
			return true;
		}
		task.setSuspended(true);
		return false;
	}

	@Override
	public void onTaskSuspension(Level world, Task task) {
		tracked.remove(task.getPos().asLong());
	}

	@Override
	public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
	}

	@Override
	public void readCustomNBT(CompoundTag nbt) {
	}

	@Override
	public void writeCustomNBT(CompoundTag nbt) {
	}

	@Override
	public boolean canPlaceAt(Level world, BlockPos pos, Direction side) {
		return !world.isEmptyBlock(pos);
	}

	@Override
	public ResourceLocation getSealIcon() {
		return ICON;
	}

	@Override
	public void onRemoval(Level world, BlockPos pos, Direction side) {
	}

	@Override
	public Object returnContainer(Level world, Player player, BlockPos pos, Direction side, ISealEntity seal) {
		return null;
	}

	@Override
	public Object returnGui(Level world, Player player, BlockPos pos, Direction side, ISealEntity seal) {
		return null;
	}

	@Override
	public EnumGolemTrait[] getRequiredTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.BREAKER, EnumGolemTrait.SMART };
	}

	@Override
	public EnumGolemTrait[] getForbiddenTags() {
		return null;
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_AREA, CAT_PRIORITY, CAT_TAGS };
	}
}

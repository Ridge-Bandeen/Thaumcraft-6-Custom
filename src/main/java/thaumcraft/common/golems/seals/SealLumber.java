package thaumcraft.common.golems.seals;

import java.util.Random;

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
 * As the original, a chop fells the whole tree: each time the golem gets to the task it breaks the log furthest
 * along the trunk from the one it was sent to ({@link thaumcraft.common.lib.utils.BlockSearch}), keeping the task
 * open until that log itself is gone.
 */
public class SealLumber implements ISeal, ISealGui, ISealConfigArea {

	private int delay = new Random().nextInt(33);
	/** The original's cache: task id -> the log it chops; a log with a live task gets no second one. */
	private final java.util.Map<Integer, Long> cache = new java.util.HashMap<>();
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_lumber");

	@Override
	public String getKey() {
		return "thaumcraft:lumber";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay % 100 == 0) {
			cache.keySet().removeIf(id -> TaskHandler.getTask(world.dimension(), id) == null);
		}
		delay++;
		BlockPos p = GolemHelper.getPosInArea(seal, delay);
		if (!cache.containsValue(p.asLong()) && isLog(world, p)) {
			Task task = new Task(seal.getSealPos(), p);
			task.setPriority(seal.getPriority());
			TaskHandler.addTask(world.dimension(), task);
			cache.put(task.getId(), p.asLong());
		}
	}

	private static boolean isLog(Level world, BlockPos pos) {
		return world.getBlockState(pos).is(BlockTags.LOGS);
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		if (cache.containsKey(task.getId()) && isLog(world, task.getPos())) {
			golem.swingArm();
			net.minecraft.world.level.block.state.BlockState state = world.getBlockState(task.getPos());
			BlockPos furthest = thaumcraft.common.lib.utils.BlockSearch.findFurthest(world, task.getPos(), state.getBlock(), 2);
			if (world.destroyBlock(furthest, true, golem.getGolemEntity())) {
				task.setLifespan((short) Math.max(task.getLifespan(), 10L));
				golem.addRankXp(1);
				return false;
			}
			cache.remove(task.getId());
		}
		task.setSuspended(true);
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		if (cache.containsKey(task.getId()) && isLog(golem.getGolemWorld(), task.getPos())) {
			return true;
		}
		task.setSuspended(true);
		return false;
	}

	@Override
	public void onTaskSuspension(Level world, Task task) {
		cache.remove(task.getId());
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

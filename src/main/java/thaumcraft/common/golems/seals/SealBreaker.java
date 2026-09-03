package thaumcraft.common.golems.seals;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISealConfigArea;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealBreaker} - the first real {@code SealFiltered} subclass this port
 * ships (see that class's own doc comment for the real item-filter mechanism). Scans its area for real
 * breakable, non-air blocks matching the filter (blacklist or whitelist, see {@link #isValidBlock}) and
 * turns each into a break task; the golem's own {@link EnumGolemTrait#BREAKER} trait is all that's
 * required - unlike {@code SealLumber}/{@code SealHarvest}, no {@code SMART}/{@code DEFT} requirement,
 * matching the original exactly (breaking generic blocks needs strength, not finesse or judgement).
 *
 * Real, documented simplification: the original's own {@code onTaskCompletion} was a real multi-tick mining
 * simulation - block-hardness-based progress accumulated across repeated task completions, with visible
 * crack-stage updates, all driven through a {@code FakePlayer}'s simulated harvest. None of that
 * (progressive mining, crack stages) is ported - this breaks the matched block in one action via
 * {@link #breakBlock} (default: {@code Level#destroyBlock} for real loot-table drops, arguably a *more*
 * correct drop path than the original's own FakePlayer-harvest simulation). {@link #breakBlock} is a real,
 * protected seam - {@link SealBreakerAdvanced} overrides it for real silk-touch dropping, the one piece of
 * the original's silk-touch toggle that IS ported (just not the progressive mining alongside it). Real
 * ore-dictionary-style fuzzy/metadata matching is also dropped, see {@code SealFiltered}'s own doc comment
 * for why (exact item identity only).
 */
public class SealBreaker extends SealFiltered implements ISealConfigArea {

	private int delay = new Random().nextInt(42);
	protected final Set<Long> tracked = new HashSet<>();
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_breaker");

	@Override
	public String getKey() {
		return "thaumcraft:breaker";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay % 100 == 0) {
			Iterator<Long> it = tracked.iterator();
			while (it.hasNext()) {
				if (!isValidBlock(world, BlockPos.of(it.next()))) it.remove();
			}
		}
		delay++;
		BlockPos p = GolemHelper.getPosInArea(seal, delay);
		if (!tracked.contains(p.asLong()) && isValidBlock(world, p)) {
			Task task = new Task(seal.getSealPos(), p);
			task.setPriority(seal.getPriority());
			TaskHandler.addTask(world.dimension(), task);
			tracked.add(p.asLong());
		}
	}

	/** A block is a real candidate if it's not air, not "unbreakable" ({@code getDestroySpeed} < 0, e.g.
	 * bedrock), and matches the real filter (blacklist: break anything NOT listed; whitelist: break only
	 * what IS listed). */
	protected boolean isValidBlock(Level world, BlockPos pos) {
		if (world.isEmptyBlock(pos)) return false;
		BlockState state = world.getBlockState(pos);
		if (state.getDestroySpeed(world, pos) < 0.0f) return false;
		boolean matches = matchesFilter(state.getBlock().asItem());
		return isBlacklist() ? !matches : matches;
	}

	/** Real, overridable seam for how the matched block is actually removed - see this class's own doc
	 * comment for why {@link SealBreakerAdvanced} overrides it. */
	protected void breakBlock(Level world, BlockPos pos) {
		world.destroyBlock(pos, true);
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		BlockPos pos = task.getPos();
		if (isValidBlock(world, pos)) {
			golem.swingArm();
			breakBlock(world, pos);
			golem.addRankXp(1);
		}
		tracked.remove(pos.asLong());
		task.setSuspended(true);
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		if (isValidBlock(golem.getGolemWorld(), task.getPos())) {
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
	public EnumGolemTrait[] getRequiredTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.BREAKER };
	}

	@Override
	public EnumGolemTrait[] getForbiddenTags() {
		return null;
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_AREA, CAT_FILTER, CAT_PRIORITY, CAT_TAGS };
	}
}

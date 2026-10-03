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
 * As the original it mines progressively: each task starts with ten times the block's hardness of work, and each
 * time the golem gets to it it knocks off 21 (7 when breaking silky), the block cracking and clicking as it goes
 * ({@link #onTaskCompletion}), until it breaks ({@link #breakBlock}; {@link SealBreakerAdvanced} can do it
 * silky). Filters match exact items (see {@code SealFiltered}).
 */
public class SealBreaker extends SealFiltered implements ISealConfigArea, thaumcraft.api.golems.seals.ISealConfigToggles {

	/** The original's toggles: match damage (and, on the advanced seal, silky). */
	protected SealToggle[] props = { new SealToggle(true, "pmeta", "golem.prop.meta") };

	@Override
	public SealToggle[] getToggles() {
		return props;
	}

	@Override
	public void setToggle(int index, boolean value) {
		props[index].setValue(value);
	}

	/** As the original: the block's item, compared by damage when "match damage" is on, never by NBT. */
	@Override
	protected SealFilters.Match match() {
		return new SealFilters.Match(!props[0].getValue(), true, false, false);
	}

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
			// as the original: the work it takes, ten times the block's hardness
			task.setData((int) (world.getBlockState(p).getDestroySpeed(world, p) * 10.0f));
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
		// (the original's whitelist demanded a match with every filled slot, so two different whitelisted blocks
		// broke nothing; here any one will do)
		return passes(new net.minecraft.world.item.ItemStack(state.getBlock().asItem()));
	}

	/** Real, overridable seam for how the matched block is actually removed - see this class's own doc
	 * comment for why {@link SealBreakerAdvanced} overrides it. */
	protected void breakBlock(Level world, BlockPos pos) {
		world.destroyBlock(pos, true);
	}

	/** Whether this seal breaks silky (slower going) - {@link SealBreakerAdvanced}'s toggle. */
	protected boolean isSilky() {
		return false;
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		BlockPos pos = task.getPos();
		if (isValidBlock(world, pos)) {
			golem.swingArm();
			int speed = isSilky() ? 7 : 21;
			BlockState state = world.getBlockState(pos);
			int id = golem.getGolemEntity().getId();
			if (task.getData() > speed) {
				// as the original: another bite out of it, with the crack and the clink; the task stays open
				float work = state.getDestroySpeed(world, pos) * 10.0f;
				task.setLifespan((short) Math.max(task.getLifespan(), 10L));
				task.setData(task.getData() - speed);
				int progress = (int) (9.0f * (1.0f - task.getData() / work));
				var sound = state.getSoundType(world, pos, golem.getGolemEntity());
				world.playSound(null, pos, sound.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, (sound.getVolume() + 0.7f) / 8.0f, sound.getPitch() * 0.5f);
				world.destroyBlockProgress(id, pos, progress);
				return false;
			}
			world.destroyBlockProgress(id, pos, 10);
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
		return new int[] { CAT_AREA, CAT_FILTER, CAT_TOGGLES, CAT_PRIORITY, CAT_TAGS };
	}
}

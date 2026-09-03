package thaumcraft.common.golems.seals;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISealConfigToggles;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealEmpty} - the "pull a matching item out of a container into what a
 * golem carries" half of the fill/empty pair (see {@link SealFill} for the other half). Every 20 ticks it
 * checks the real {@link IItemHandler} at its own position/face for a filter match and, if found, opens a
 * real short-lifespan task (matches the original's own {@code lifespan 5}) caching which stack was matched
 * (by id, same {@code Map<Integer, ItemStack>} approach the original used) so {@link #onTaskCompletion} knows
 * what to actually extract once a golem gets there.
 *
 * Real, documented simplifications: the original's own matching went through {@code InventoryUtils}/
 * {@code ThaumcraftInvHelper.InvFilter} (4 real toggles: metadata/NBT/ore-dictionary/same-mod matching) -
 * not ported, same call {@code SealFiltered} already made for every other filtered seal in this port (exact
 * {@link net.minecraft.world.item.Item} identity only). The original's own round-robin "cycle through filter
 * slots" field ({@code filterInc}) is dropped too - it was already dead code in the original itself (its own
 * {@code getInv(int)} override discarded the index and just returned the full filter every time), so nothing
 * real is lost by not porting it at THIS tier - {@link SealEmptyAdvanced} does implement it for real, see
 * that class's own doc comment. The real "leave one behind" toggle IS kept (see {@code pleave} below,
 * un-GUI'd but functional at this tier - same real-toggle-without-a-screen-yet pattern {@code SealGuard}
 * already established, {@code SealEmptyAdvanced} is what exposes both in the real config GUI).
 */
public class SealEmpty extends SealFiltered implements ISealConfigToggles {

	private int delay = new Random().nextInt(30);
	/** Task id -> the specific stack matched when that task was created - the original's own tracking map. */
	private final Map<Integer, ItemStack> cache = new HashMap<>();
	/** {@code pcycle} is real but inert at this tier (matches the original exactly - its own base
	 * {@code SealEmpty} never consulted it either, only the Advanced variant's own overridden match-finding
	 * did) - present here only so {@link SealEmptyAdvanced} inherits the field. */
	protected final ISealConfigToggles.SealToggle[] props = {
			new ISealConfigToggles.SealToggle(false, "pleave", "golem.prop.leave"),
			new ISealConfigToggles.SealToggle(false, "pcycle", "golem.prop.cycle"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_empty");

	@Override
	public String getKey() {
		return "thaumcraft:empty";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay % 100 == 0) {
			cache.keySet().removeIf(id -> TaskHandler.getTask(world.dimension(), id) == null);
		}
		if (delay++ % 20 != 0) return;
		IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, seal.getSealPos().pos, seal.getSealPos().face);
		if (inv == null) return;
		ItemStack match = findFirstMatching(inv);
		if (!match.isEmpty()) {
			Task task = new Task(seal.getSealPos(), seal.getSealPos().pos);
			task.setPriority(seal.getPriority());
			task.setLifespan((short) 5);
			TaskHandler.addTask(world.dimension(), task);
			cache.put(task.getId(), match);
		}
	}

	protected boolean matches(ItemStack stack) {
		boolean m = matchesFilter(stack.getItem());
		return isBlacklist() ? !m : m;
	}

	/** Real, overridable seam - {@link SealEmptyAdvanced} overrides this for real round-robin cycling through
	 * the filter's own matching items instead of always picking the first, gated on {@code pcycle}. */
	protected ItemStack findFirstMatching(IItemHandler inv) {
		for (int i = 0; i < inv.getSlots(); i++) {
			ItemStack stack = inv.getStackInSlot(i);
			if (!stack.isEmpty() && matches(stack)) return stack.copy();
		}
		return ItemStack.EMPTY;
	}

	protected static ItemStack extractMatching(IItemHandler inv, ItemStack want) {
		for (int i = 0; i < inv.getSlots(); i++) {
			ItemStack slotStack = inv.getStackInSlot(i);
			if (!slotStack.isEmpty() && slotStack.getItem() == want.getItem()) {
				ItemStack extracted = inv.extractItem(i, want.getCount(), false);
				if (!extracted.isEmpty()) return extracted;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		ItemStack want = cache.remove(task.getId());
		if (want == null || want.isEmpty()) {
			task.setSuspended(true);
			return true;
		}
		IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, task.getSealPos().pos, task.getSealPos().face);
		if (inv != null) {
			if (props[0].getValue()) {
				int have = SealStock.countMatching(inv, want.getItem());
				if (have <= want.getCount()) {
					want = want.copy();
					want.setCount(have - 1);
				}
			}
			if (!want.isEmpty()) {
				int limit = Math.min(want.getCount(), golem.canCarryAmount(want));
				if (limit > 0) {
					ItemStack toTake = want.copy();
					toTake.setCount(limit);
					ItemStack extracted = extractMatching(inv, toTake);
					if (!extracted.isEmpty()) {
						ItemStack leftover = golem.holdItem(extracted);
						if (!leftover.isEmpty()) {
							ItemHandlerHelper.insertItemStacked(inv, leftover, false);
						}
						golem.getGolemEntity().playSound(SoundEvents.ITEM_PICKUP, 0.125f, 2.0f);
						golem.addRankXp(1);
						golem.swingArm();
					}
				}
			}
		}
		task.setSuspended(true);
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		ItemStack want = cache.get(task.getId());
		return want != null && !want.isEmpty() && golem.canCarry(want, true);
	}

	@Override
	public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
	}

	@Override
	public void onTaskSuspension(Level world, Task task) {
		cache.remove(task.getId());
	}

	@Override
	public boolean canPlaceAt(Level world, BlockPos pos, Direction side) {
		return world.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null;
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
		return null;
	}

	@Override
	public EnumGolemTrait[] getForbiddenTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.CLUMSY };
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_FILTER, CAT_PRIORITY, CAT_TAGS };
	}

	@Override
	public ISealConfigToggles.SealToggle[] getToggles() {
		return props;
	}

	@Override
	public void setToggle(int index, boolean value) {
		props[index].setValue(value);
	}
}

package thaumcraft.common.golems.seals;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
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
 * Replaces the old (1.12.2) {@code SealFill} - the "push what a golem carries into a container" half of the
 * fill/empty pair (see {@link SealEmpty} for the other half). Unlike every other task-generating seal this
 * port has shipped, it doesn't scan for candidates itself - it keeps exactly one standing task open at its
 * own position (re-created whenever the previous one finishes, matching the original's own
 * {@code watchedTask} throttle) for any passing golem to pick up and fulfill.
 *
 * Real, documented simplifications: the original's own matching went through {@code InventoryUtils}/
 * {@code ThaumcraftInvHelper.InvFilter} (4 real toggles: metadata/NBT/ore-dictionary/same-mod matching, plus
 * a real per-filter-slot stacksize-limiter that capped how much of an item to send) - neither is ported,
 * same call {@code SealFiltered} already made for every other filtered seal in this port (exact
 * {@link net.minecraft.world.item.Item} identity only, no stacksize cap - a matching carried item is always
 * deposited in full). {@link #canPlaceAt} keeps the original's own real, looser rule (any non-air block, not
 * just ones with a real inventory) - {@link #onTaskCompletion} drops the item on the ground next to the seal
 * when there's no real {@link IItemHandler} there, exactly like the original's own {@code EntityItem} spawn.
 *
 * <p>One real toggle survives the {@code SealFiltered} matching simplification above: {@code pexist}
 * ("only top up a container that already has some") - real and kept, just like {@link SealGuard}'s own real
 * toggles, only reachable via NBT/creative tools at this tier (no {@code CAT_TOGGLES} in this seal's own
 * {@link #getGuiCategories}) - {@link SealFillAdvanced} is what exposes it in the real config GUI.
 */
public class SealFill extends SealFiltered implements ISealConfigToggles {

	private int delay = new Random().nextInt(50);
	private int watchedTask = Integer.MIN_VALUE;
	protected final SealToggle[] props = {
			new SealToggle(false, "pexist", "golem.prop.exist"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_fill");

	@Override
	public String getKey() {
		return "thaumcraft:fill";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay++ % 20 != 0) return;
		Task oldTask = TaskHandler.getTask(world.dimension(), watchedTask);
		if (oldTask == null || oldTask.isReserved() || oldTask.isSuspended() || oldTask.isCompleted()) {
			Task task = new Task(seal.getSealPos(), seal.getSealPos().pos);
			task.setPriority(seal.getPriority());
			TaskHandler.addTask(world.dimension(), task);
			watchedTask = task.getId();
		}
	}

	private boolean matches(ItemStack stack) {
		boolean m = matchesFilter(stack.getItem());
		return isBlacklist() ? !m : m;
	}

	/** First carried item (main-hand or off-hand carry slot) matching this seal's real filter. */
	private ItemStack findMatchingCarried(IGolemAPI golem) {
		for (ItemStack stack : golem.getCarrying()) {
			if (!stack.isEmpty() && matches(stack)) return stack;
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		ItemStack match = findMatchingCarried(golem);
		if (!match.isEmpty()) {
			ItemStack taken = golem.dropItem(match);
			if (!taken.isEmpty()) {
				IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, task.getSealPos().pos, task.getSealPos().face);
				if (inv != null) {
					ItemStack remainder = ItemHandlerHelper.insertItemStacked(inv, taken, false);
					if (!remainder.isEmpty()) {
						golem.holdItem(remainder);
					}
				} else {
					BlockPos dropPos = task.getSealPos().pos.relative(task.getSealPos().face);
					ItemEntity entity = new ItemEntity(world, dropPos.getX() + 0.5, dropPos.getY() + 0.5, dropPos.getZ() + 0.5, taken);
					entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.2, 0.5, 0.2));
					world.addFreshEntity(entity);
				}
				golem.getGolemEntity().playSound(SoundEvents.ITEM_PICKUP, 0.125f, 1.0f);
				golem.addRankXp(1);
				golem.swingArm();
			}
		}
		task.setSuspended(true);
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		ItemStack match = findMatchingCarried(golem);
		if (match.isEmpty()) return false;
		if (props[0].getValue()) {
			IItemHandler inv = golem.getGolemWorld().getCapability(Capabilities.ItemHandler.BLOCK, task.getSealPos().pos, task.getSealPos().face);
			if (inv != null && SealStock.countMatching(inv, match.getItem()) <= 0) return false;
		}
		return true;
	}

	@Override
	public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
	}

	@Override
	public void onTaskSuspension(Level world, Task task) {
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
	public SealToggle[] getToggles() {
		return props;
	}

	@Override
	public void setToggle(int index, boolean value) {
		props[index].setValue(value);
	}
}

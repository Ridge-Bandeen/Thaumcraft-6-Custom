package thaumcraft.common.golems.seals;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISealConfigToggles;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.GolemInteractionHelper;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealUse} - the most general logistics seal: makes a golem right- or
 * left-click its own position with a real filter-matched carried item (or an empty hand), via {@link
 * GolemInteractionHelper}'s real {@link net.neoforged.neoforge.common.util.FakePlayer} simulation. Same
 * "keep exactly one standing task open" throttle {@link SealFill} already established, gated on the
 * {@code pempty} toggle deciding whether this seal only fires against an empty (air) position or a
 * non-empty one.
 *
 * Real toggles kept (dropping the meta/NBT/ore/mod matching toggles superseded by {@link SealFiltered}'s own
 * simplification, same as every other filtered seal): {@code pempty} (fire on an empty/air position instead
 * of a non-empty one - e.g. for placing rather than using), {@code pemptyhand} (click with an empty hand
 * instead of requiring a filter match), {@code pleft} (left-click/attack instead of right-click/use),
 * {@code psneak} (sneak while clicking), {@code ppro} (request provisioning of the filter's first item when
 * whitelisted and none is currently carried - same real {@code GolemHelper.requestProvisioning} mechanism
 * {@link SealStock} already established).
 */
public class SealUse extends SealFiltered implements ISealConfigToggles {

	private int delay = new Random().nextInt(49);
	private int watchedTask = Integer.MIN_VALUE;
	private final SealToggle[] props = {
			new SealToggle(false, "pempty", "golem.prop.empty"),
			new SealToggle(false, "pemptyhand", "golem.prop.emptyhand"),
			new SealToggle(false, "pleft", "golem.prop.left"),
			new SealToggle(false, "psneak", "golem.prop.sneak"),
			new SealToggle(false, "ppro", "golem.prop.provision.wl"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_use");

	@Override
	public String getKey() {
		return "thaumcraft:use";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay++ % 5 != 0) return;
		Task oldTask = TaskHandler.getTask(world.dimension(), watchedTask);
		if (oldTask == null || oldTask.isSuspended() || oldTask.isCompleted()) {
			if (props[0].getValue() != world.isEmptyBlock(seal.getSealPos().pos)) return;
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

	private ItemStack findMatchingCarried(IGolemAPI golem) {
		for (ItemStack stack : golem.getCarrying()) {
			if (!stack.isEmpty() && matches(stack)) return stack;
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		if (props[0].getValue() == world.isEmptyBlock(task.getPos())) {
			ItemStack clickStack = findMatchingCarried(golem);
			if (!clickStack.isEmpty() || props[1].getValue()) {
				ItemStack toUse = ItemStack.EMPTY;
				if (!clickStack.isEmpty()) {
					toUse = golem.dropItem(clickStack);
				}
				GolemInteractionHelper.golemClick(world, golem, task.getPos(), task.getSealPos().face,
						props[1].getValue() ? ItemStack.EMPTY : toUse, props[3].getValue(), !props[2].getValue());
			}
		}
		task.setSuspended(true);
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		if (props[1].getValue()) return true;
		boolean found = !findMatchingCarried(golem).isEmpty();
		if (!found && props[4].getValue() && !isBlacklist()) {
			ItemStack want = getInv().get(0);
			if (!want.isEmpty()) {
				GolemHelper.requestProvisioning(golem.getGolemWorld(), task.getSealPos().pos, task.getSealPos().face, want.copy());
			}
		}
		return found;
	}

	@Override
	public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
	}

	@Override
	public void onTaskSuspension(Level world, Task task) {
	}

	@Override
	public boolean canPlaceAt(Level world, BlockPos pos, Direction side) {
		return true;
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
		return new EnumGolemTrait[] { EnumGolemTrait.DEFT, EnumGolemTrait.SMART };
	}

	@Override
	public EnumGolemTrait[] getForbiddenTags() {
		return null;
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_FILTER, CAT_TOGGLES, CAT_PRIORITY, CAT_TAGS };
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

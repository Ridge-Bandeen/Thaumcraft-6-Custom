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
 * left-click its own position with a carried item (or an empty hand), via {@link GolemInteractionHelper}'s
 * {@link net.neoforged.neoforge.common.util.FakePlayer}. As the original it keeps one standing task open, made only
 * while the position is empty or not as "empty" says. The item is the first carried one passing the filter (with its
 * toggles: damage, NBT, ore, mod) - or, with nothing in the filter, simply the first one carried. The other toggles:
 * "left click", "empty" (work on an empty position), "empty hand", "sneak", and "provision" (on a whitelist, ask a
 * Provider seal for the filter's item when the golem has none).
 */
public class SealUse extends SealFiltered implements ISealConfigToggles {

	private int delay = new Random().nextInt(49);
	private int watchedTask = Integer.MIN_VALUE;
	protected final SealToggle[] props = {
			new SealToggle(true, "pmeta", "golem.prop.meta"),
			new SealToggle(true, "pnbt", "golem.prop.nbt"),
			new SealToggle(false, "pore", "golem.prop.ore"),
			new SealToggle(false, "pmod", "golem.prop.mod"),
			new SealToggle(false, "pleft", "golem.prop.left"),
			new SealToggle(false, "pempty", "golem.prop.empty"),
			new SealToggle(false, "pemptyhand", "golem.prop.emptyhand"),
			new SealToggle(false, "psneak", "golem.prop.sneak"),
			new SealToggle(false, "ppro", "golem.prop.provision.wl"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_use");

	@Override
	protected SealFilters.Match match() {
		return SealFilters.Match.fromToggles(props[0].getValue(), props[1].getValue(), props[2].getValue(), props[3].getValue());
	}

	@Override
	public String getKey() {
		return "thaumcraft:use";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay++ % 5 != 0) return;
		Task oldTask = TaskHandler.getTask(world.dimension(), watchedTask);
		if (oldTask == null || oldTask.isSuspended() || oldTask.isCompleted()) {
			if (props[5].getValue() != world.isEmptyBlock(seal.getSealPos().pos)) return;
			Task task = new Task(seal.getSealPos(), seal.getSealPos().pos);
			task.setPriority(seal.getPriority());
			TaskHandler.addTask(world.dimension(), task);
			watchedTask = task.getId();
		}
	}

	private ItemStack findMatchingCarried(IGolemAPI golem) {
		return SealFilters.findFirst(getInv(), getSizes(), isBlacklist(), golem.getCarrying(), match()).stack();
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		if (props[5].getValue() == world.isEmptyBlock(task.getPos())) {
			ItemStack clickStack = golem.getCarrying().get(0);
			if (!getInv().get(0).isEmpty()) {
				clickStack = findMatchingCarried(golem);
			}
			if (!clickStack.isEmpty() || props[6].getValue()) {
				ItemStack ss = ItemStack.EMPTY;
				if (!clickStack.isEmpty()) {
					ss = clickStack.copy();
					golem.dropItem(clickStack.copy());
				}
				GolemInteractionHelper.golemClick(world, golem, task.getPos(), task.getSealPos().face,
						props[6].getValue() ? ItemStack.EMPTY : ss, props[7].getValue(), !props[4].getValue());
			}
		}
		task.setSuspended(true);
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		if (props[6].getValue()) return true;
		boolean found = !findMatchingCarried(golem).isEmpty();
		if (!found && props[8].getValue() && !isBlacklist() && !getInv().get(0).isEmpty()) {
			ISealEntity se = SealHandler.getSealEntity(golem.getGolemWorld().dimension(), task.getSealPos());
			if (se != null) {
				// (the original also asked for any damage when "match damage" was off - a wildcard there is no
				// longer; the Provider matches the request through its own filter toggles)
				GolemHelper.requestProvisioning(golem.getGolemWorld(), se, getInv().get(0).copy());
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

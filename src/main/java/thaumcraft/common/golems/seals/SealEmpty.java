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
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISealConfigToggles;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealEmpty}, as the original: every 20 ticks it looks in the container it is on for
 * a stack that passes the filter (with "leave one", only kinds there are two or more of) and opens a short task for
 * it; the golem takes as much as it can carry (keeping one back with "leave one"), and anything it then can't hold
 * is dropped out in front of the seal. The toggles - match damage, NBT, ore, mod, cycle, leave one - are the
 * original's, shown only on {@link SealEmptyAdvanced}, which alone cycles through its whitelist.
 */
public class SealEmpty extends SealFiltered implements ISealConfigToggles {

	private int delay = new Random().nextInt(30);
	/** The original's filterInc: advances with each completed task, for {@link SealEmptyAdvanced}'s cycling. */
	protected int filterInc;
	/** Task id -> the stack matched when that task was made. */
	private final Map<Integer, ItemStack> cache = new HashMap<>();
	protected final ISealConfigToggles.SealToggle[] props = {
			new ISealConfigToggles.SealToggle(true, "pmeta", "golem.prop.meta"),
			new ISealConfigToggles.SealToggle(true, "pnbt", "golem.prop.nbt"),
			new ISealConfigToggles.SealToggle(false, "pore", "golem.prop.ore"),
			new ISealConfigToggles.SealToggle(false, "pmod", "golem.prop.mod"),
			new ISealConfigToggles.SealToggle(false, "pcycle", "golem.prop.cycle"),
			new ISealConfigToggles.SealToggle(false, "pleave", "golem.prop.leave"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_empty");

	@Override
	protected SealFilters.Match match() {
		return SealFilters.Match.fromToggles(props[0].getValue(), props[1].getValue(), props[2].getValue(), props[3].getValue());
	}

	/** The filter as this tick sees it - {@link SealEmptyAdvanced} narrows it to one whitelisted item at a time. */
	protected net.minecraft.core.NonNullList<ItemStack> getInv(int c) {
		return getInv();
	}

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
		ItemStack match = SealFilters.findFirstIn(getInv(filterInc), isBlacklist(), inv, match(), props[5].getValue());
		if (!match.isEmpty()) {
			Task task = new Task(seal.getSealPos(), seal.getSealPos().pos);
			task.setPriority(seal.getPriority());
			task.setLifespan((short) 5);
			TaskHandler.addTask(world.dimension(), task);
			cache.put(task.getId(), match.copy());
		}
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		ItemStack stack = cache.get(task.getId());
		IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, task.getSealPos().pos, task.getSealPos().face);
		if (stack != null && !stack.isEmpty() && inv != null) {
			int have = SealFilters.countIn(inv, stack, match());
			if (props[5].getValue() && have <= stack.getCount()) {
				stack = stack.copyWithCount(have - 1);
			}
			if (!stack.isEmpty()) {
				int limit = golem.canCarryAmount(stack);
				if (limit > 0) {
					ItemStack taken = SealFilters.removeFrom(inv, stack.copyWithCount(Math.min(stack.getCount(), limit)), match(), false);
					ItemStack leftover = golem.holdItem(taken);
					if (!leftover.isEmpty()) {
						// as the original: what it can't hold is dropped out in front of the seal
						BlockPos front = task.getSealPos().pos.relative(task.getSealPos().face);
						net.minecraft.world.entity.item.ItemEntity out = new net.minecraft.world.entity.item.ItemEntity(world,
								front.getX() + 0.5, front.getY() + 0.5, front.getZ() + 0.5, leftover);
						world.addFreshEntity(out);
					}
					golem.getGolemEntity().playSound(SoundEvents.ITEM_PICKUP, 0.125f,
							((world.random.nextFloat() - world.random.nextFloat()) * 0.7f + 1.0f) * 2.0f);
					golem.swingArm();
				}
			}
		}
		cache.remove(task.getId());
		filterInc++;
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

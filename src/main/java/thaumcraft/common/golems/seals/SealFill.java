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
 * fill/empty pair (see {@link SealEmpty} for the other half). As the original it keeps one standing task open at its
 * own position (a fresh one queued as soon as a golem starts the last, unless redstone stops the seal) for any
 * passing golem that carries something passing the filter. On a whitelist each filter slot's size is a limit: the
 * golem only tops the container up to that many. With no inventory at the seal, the item is dropped in front of it
 * instead (the limit then counting the matching items lying around). The toggles - match damage, NBT, ore, mod,
 * "only where some already are" - are the original's, shown only on {@link SealFillAdvanced}.
 */
public class SealFill extends SealFiltered {

	private int delay = new Random().nextInt(50);
	private int watchedTask = Integer.MIN_VALUE;
	protected final ISealConfigToggles.SealToggle[] props = {
			new ISealConfigToggles.SealToggle(true, "pmeta", "golem.prop.meta"),
			new ISealConfigToggles.SealToggle(true, "pnbt", "golem.prop.nbt"),
			new ISealConfigToggles.SealToggle(false, "pore", "golem.prop.ore"),
			new ISealConfigToggles.SealToggle(false, "pmod", "golem.prop.mod"),
			new ISealConfigToggles.SealToggle(false, "pexist", "golem.prop.exist"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_fill");

	@Override
	protected SealFilters.Match match() {
		return SealFilters.Match.fromToggles(props[0].getValue(), props[1].getValue(), props[2].getValue(), props[3].getValue());
	}

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

	@Override
	public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
		ISealEntity se = SealHandler.getSealEntity(world.dimension(), task.getSealPos());
		if (se != null && !se.isStoppedByRedstone(world)) {
			Task newTask = new Task(task.getSealPos(), task.getSealPos().pos);
			newTask.setPriority(se.getPriority());
			TaskHandler.addTask(world.dimension(), newTask);
			watchedTask = newTask.getId();
		}
	}

	/** The first carried stack passing the filter, with its slot's size limit. */
	private SealFilters.Hit carriedMatch(IGolemAPI golem) {
		return SealFilters.findFirst(getInv(), getSizes(), isBlacklist(), golem.getCarrying(), match());
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		SealFilters.Hit hit = carriedMatch(golem);
		if (!hit.stack().isEmpty()) {
			BlockPos pos = task.getSealPos().pos;
			Direction face = task.getSealPos().face;
			IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
			int limit = hit.stack().getCount();
			if (hasStacksizeLimiters() && hit.limit() > 0) {
				int c = inv == null ? SealFilters.countInWorld(world, pos, hit.stack(), 1.5, match()) : SealFilters.countIn(inv, hit.stack(), match());
				limit = c < hit.limit() ? hit.limit() - c : 0;
			}
			if (limit > 0) {
				ItemStack s = golem.dropItem(hit.stack().copyWithCount(limit));
				if (inv == null) {
					ItemEntity ie = new ItemEntity(world, pos.getX() + 0.5 + face.getStepX(), pos.getY() + 0.5 + face.getStepY(),
							pos.getZ() + 0.5 + face.getStepZ(), s);
					ie.setDeltaMovement(ie.getDeltaMovement().multiply(0.2, 0.5, 0.2));
					world.addFreshEntity(ie);
				} else {
					golem.holdItem(ItemHandlerHelper.insertItemStacked(inv, s, false));
				}
				golem.getGolemEntity().playSound(SoundEvents.ITEM_PICKUP, 0.125f, (world.random.nextFloat() - world.random.nextFloat()) * 0.7f + 1.0f);
				golem.addRankXp(1);
				golem.swingArm();
			}
		}
		task.setSuspended(true);
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		SealFilters.Hit hit = carriedMatch(golem);
		if (hit.stack().isEmpty()) return false;
		Level world = golem.getGolemWorld();
		BlockPos pos = task.getSealPos().pos;
		boolean limited = hasStacksizeLimiters() && hit.limit() > 0;
		IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, task.getSealPos().face);
		if (inv == null) {
			return !limited || SealFilters.countInWorld(world, pos, hit.stack(), 1.5, match()) < hit.limit();
		}
		if (props[4].getValue() && SealFilters.countIn(inv, hit.stack(), match()) <= 0) return false;
		ItemStack rest = ItemHandlerHelper.insertItemStacked(inv, hit.stack().copy(), true);
		if (rest.getCount() == hit.stack().getCount()) return false;
		return !limited || SealFilters.countIn(inv, hit.stack(), match()) < hit.limit();
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
	public boolean hasStacksizeLimiters() {
		return !isBlacklist();
	}
}

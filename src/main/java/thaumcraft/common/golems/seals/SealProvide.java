package thaumcraft.common.golems.seals;

import java.util.List;
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
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.ProvisionRequest;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealProvide} - the real consumer of {@link SealStock}'s own provisioning
 * requests (via the already-real {@code GolemHelper.requestProvisioning}/{@code provisionRequests}). Two
 * real block-position tasks chained through {@code thaumcraft.common.golems.ai.AIGotoBlock} per fulfilled
 * request ({@link Task#getData()} 0 then 1): first a golem walks to THIS seal's own container and holds the
 * matching item ({@link #onTaskCompletion}'s data-0 branch, which immediately queues the second task),
 * then walks to the requester's own position and deposits it there (data-1 branch).
 *
 * Real, documented simplification: the original also serviced requests sourced from a seal directly (e.g.
 * {@code SealHarvest}'s own replant provisioning) or from an entity (a golem needing an item mid-task) -
 * this only handles the original's own {@code requestProvisioning(Level, BlockPos, Direction, ItemStack)}
 * overload (the one {@link SealStock} actually calls), since no seal or entity source currently exists in
 * this port that calls either of the other two overloads yet. Real {@link IItemHandler} lookup via
 * NeoForge's own {@code Capabilities.ItemHandler.BLOCK}, not the original's still-1.12.2
 * {@code ThaumcraftInvHelper}.
 */
public class SealProvide extends SealFiltered {

	private int delay = new Random().nextInt(88);
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_provider");

	@Override
	public String getKey() {
		return "thaumcraft:provider";
	}

	@Override
	public int getFilterSize() {
		return 9;
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		List<ProvisionRequest> requests = GolemHelper.provisionRequests.get(world.dimension());
		if (delay % 100 == 0 && requests != null) {
			requests.removeIf(pr -> pr.isInvalid() || pr.getTimeout() < System.currentTimeMillis()
					|| (pr.getLinkedTask() != null && pr.getLinkedTask().isSuspended() && pr.getLinkedTask().isCompleted()));
		}
		delay++;
		if (delay % 20 != 0 || requests == null) return;
		IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, seal.getSealPos().pos, seal.getSealPos().face);
		if (inv == null) return;
		for (ProvisionRequest pr : requests) {
			if (pr.isInvalid() || pr.getLinkedTask() != null || pr.getPos() == null) continue;
			if (!matches(pr.getStack())) continue;
			if (SealStock.countMatching(inv, pr.getStack().getItem()) <= 0) continue;
			Task task = new Task(seal.getSealPos(), seal.getSealPos().pos);
			task.setPriority(seal.getPriority());
			task.setLifespan((short) 31000);
			TaskHandler.addTask(world.dimension(), task);
			pr.setLinkedTask(task);
			task.setLinkedProvision(pr);
			break;
		}
	}

	/** Whether this seal's real filter (blacklist or whitelist, see {@code SealFiltered}'s own doc comment)
	 * allows fulfilling a request for the given stack's item - same "respect blacklist/whitelist mode"
	 * matching {@code SealBreaker}/{@code SealPickup} already use, not just raw filter-list membership. */
	private boolean matches(ItemStack stack) {
		boolean m = matchesFilter(stack.getItem());
		return isBlacklist() ? !m : m;
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		ProvisionRequest pr = task.getLinkedProvision();
		if (pr == null) {
			task.setSuspended(true);
			return true;
		}
		if (task.getData() == 0) {
			IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, task.getSealPos().pos, task.getSealPos().face);
			ItemStack extracted = inv != null ? extractMatching(inv, pr.getStack()) : ItemStack.EMPTY;
			if (!extracted.isEmpty()) {
				ItemStack leftover = golem.holdItem(extracted);
				if (!leftover.isEmpty()) {
					ItemHandlerHelper.insertItemStacked(inv, leftover, false);
				}
				golem.addRankXp(1);
				golem.swingArm();
				golem.getGolemEntity().playSound(SoundEvents.ITEM_PICKUP, 0.125f, 2.0f);

				Task deliver = new Task(task.getSealPos(), pr.getPos());
				deliver.setPriority(task.getPriority());
				deliver.setData(1);
				deliver.setLifespan((short) 31000);
				TaskHandler.addTask(world.dimension(), deliver);
				pr.setLinkedTask(deliver);
				deliver.setLinkedProvision(pr);
			} else {
				pr.setInvalid(true);
			}
		} else {
			ItemStack carried = golem.dropItem(pr.getStack());
			if (!carried.isEmpty()) {
				IItemHandler destInv = world.getCapability(Capabilities.ItemHandler.BLOCK, pr.getPos(), pr.getSide());
				ItemStack remainder = destInv != null ? ItemHandlerHelper.insertItemStacked(destInv, carried, false) : carried;
				if (!remainder.isEmpty()) {
					golem.holdItem(remainder);
				}
			}
			golem.swingArm();
			golem.getGolemEntity().playSound(SoundEvents.ITEM_PICKUP, 0.125f, 1.0f);
			pr.setInvalid(true);
		}
		task.setSuspended(true);
		return true;
	}

	private static ItemStack extractMatching(IItemHandler inv, ItemStack want) {
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
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		ProvisionRequest pr = task.getLinkedProvision();
		if (pr == null || pr.getStack() == null || !(golem instanceof EntityThaumcraftGolem realGolem)) return false;
		if (!realGolem.isWithinRestriction(task.getPos())) return false;
		if (task.getData() == 0) {
			return !golem.isCarrying(pr.getStack()) && golem.canCarry(pr.getStack(), true);
		}
		return golem.isCarrying(pr.getStack());
	}

	@Override
	public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
	}

	@Override
	public void onTaskSuspension(Level world, Task task) {
		if (task.getLinkedProvision() != null) {
			task.getLinkedProvision().setLinkedTask(null);
		}
		task.setLinkedProvision(null);
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
}

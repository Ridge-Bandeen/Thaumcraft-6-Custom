package thaumcraft.common.golems.seals;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.ProvisionRequest;
import thaumcraft.api.golems.seals.ISealConfigToggles;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealProvide} - the answer to provisioning requests, as the original. Every 20
 * ticks it looks for an unanswered request within 64 blocks - from a seal (a Harvest seal wanting seeds), an entity
 * (a golem wanting an item for its job) or a container (a Stock seal) - for something that passes its filter (with
 * its toggles: damage, NBT, ore, mod) and that the container it is on holds (two or more with "leave one"), and opens
 * a task to fetch it. A golem takes it ("single" fetches one at a time); for an entity or a container a second task
 * then carries it there - handed over at an entity, put into a container (or dropped in front of something without
 * one), with whatever did not arrive asked for again.
 */
public class SealProvide extends SealFiltered implements ISealConfigToggles {

	private int delay = new Random().nextInt(88);
	protected final SealToggle[] props = {
			new SealToggle(true, "pmeta", "golem.prop.meta"),
			new SealToggle(true, "pnbt", "golem.prop.nbt"),
			new SealToggle(false, "pore", "golem.prop.ore"),
			new SealToggle(false, "pmod", "golem.prop.mod"),
			new SealToggle(false, "psing", "golem.prop.single"),
			new SealToggle(false, "pleave", "golem.prop.leave"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_provider");

	@Override
	protected SealFilters.Match match() {
		return SealFilters.Match.fromToggles(props[0].getValue(), props[1].getValue(), props[2].getValue(), props[3].getValue());
	}

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
			requests.removeIf(pr -> pr.isInvalid() || pr.getLinkedTask() == null || pr.getLinkedTask().isSuspended()
					|| pr.getLinkedTask().isCompleted() || pr.getTimeout() < System.currentTimeMillis());
		}
		if (delay++ % 20 != 0 || requests == null) return;
		BlockPos here = seal.getSealPos().pos;
		IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, here, seal.getSealPos().face);
		if (inv == null) return;
		Iterator<ProvisionRequest> it = requests.iterator();
		while (it.hasNext()) {
			ProvisionRequest pr = it.next();
			if (pr.isInvalid()) {
				it.remove();
				continue;
			}
			if (pr.getLinkedTask() != null) continue;
			boolean near = (pr.getSeal() != null && pr.getSeal().getSealPos().pos.distSqr(here) < 4096.0)
					|| (pr.getEntity() != null && here.distToLowCornerSqr(pr.getEntity().getX(), pr.getEntity().getY(), pr.getEntity().getZ()) < 4096.0)
					|| (pr.getPos() != null && here.distSqr(pr.getPos()) < 4096.0);
			if (!near) continue;
			if (passes(pr.getStack()) && SealFilters.countIn(inv, pr.getStack(), SealFilters.Match.STRICT) > (props[5].getValue() ? 1 : 0)) {
				Task task = new Task(seal.getSealPos(), here);
				task.setPriority(pr.getSeal() != null ? pr.getSeal().getPriority() : 5);
				task.setLifespan((short) (pr.getSeal() != null ? 10 : 31000));
				TaskHandler.addTask(world.dimension(), task);
				pr.setLinkedTask(task);
				task.setLinkedProvision(pr);
				break;
			}
		}
	}

	/** Whether this seal's filter (with its toggles) lets it answer a request for {@code stack}. */
	public boolean matchesFilters(ItemStack stack) {
		return passes(stack);
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		ProvisionRequest pr = task.getLinkedProvision();
		if (pr != null && task.getData() == 0) {
			IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, task.getSealPos().pos, task.getSealPos().face);
			if (inv != null) {
				ItemStack stack = pr.getStack().copy();
				if (props[4].getValue()) stack.setCount(1);
				int sa;
				if (!stack.isEmpty() && props[5].getValue() && (sa = SealFilters.countIn(inv, stack, SealFilters.Match.STRICT)) <= stack.getCount()) {
					stack.setCount(sa - 1);
				}
				if (!stack.isEmpty()) {
					int limit = golem.canCarryAmount(stack);
					if (limit > 0) {
						ItemStack s = golem.holdItem(SealFilters.removeFrom(inv, stack.copyWithCount(Math.min(stack.getCount(), limit)), SealFilters.Match.STRICT, false));
						if (!s.isEmpty()) {
							// the original's ejectStackAt, aimed back at this container: in again, or dropped in front
							ejectStackAt(world, task.getSealPos().pos.relative(task.getSealPos().face), task.getSealPos().face.getOpposite(), s, false);
						}
						golem.getGolemEntity().playSound(SoundEvents.ITEM_PICKUP, 0.125f,
								((world.random.nextFloat() - world.random.nextFloat()) * 0.7f + 1.0f) * 2.0f);
						golem.addRankXp(1);
						golem.swingArm();
						if (pr.getEntity() != null || pr.getPos() != null) {
							Task task2 = pr.getEntity() != null ? new Task(task.getSealPos(), pr.getEntity()) : new Task(task.getSealPos(), pr.getPos());
							task2.setPriority(task.getPriority());
							task2.setData(pr.getEntity() != null ? 1 : 2);
							task2.setLifespan((short) 31000);
							TaskHandler.addTask(world.dimension(), task2);
							pr.setLinkedTask(task2);
							task2.setLinkedProvision(pr);
						}
					}
				}
			}
		} else if (pr != null) {
			ItemStack cs = pr.getStack();
			ItemStack s2 = golem.dropItem(cs);
			if (s2.getCount() < cs.getCount()) {
				ItemStack ps = cs.copyWithCount(cs.getCount() - s2.getCount());
				if (task.getData() == 1) {
					GolemHelper.requestProvisioning(world, pr.getEntity(), ps);
				} else {
					GolemHelper.requestProvisioning(world, pr.getPos(), pr.getSide(), ps);
				}
			}
			if (task.getData() == 1) {
				if (!s2.isEmpty()) {
					Entity e = pr.getEntity();
					world.addFreshEntity(new ItemEntity(world, e.getX(), e.getY() + e.getEyeHeight() / 2.0f, e.getZ(), s2.copy()));
				}
			} else {
				ItemStack back = ejectStackAt(world, pr.getPos().relative(pr.getSide()), pr.getSide().getOpposite(), s2, true);
				if (!back.isEmpty()) golem.holdItem(back);
			}
			golem.getGolemEntity().playSound(SoundEvents.ITEM_PICKUP, 0.125f, (world.random.nextFloat() - world.random.nextFloat()) * 0.7f + 1.0f);
			golem.swingArm();
			pr.setInvalid(true);
		}
		task.setSuspended(true);
		return true;
	}

	/** The original's {@code InventoryUtils.ejectStackAt}: into the container on {@code side} of {@code pos}; what it
	 * will not take is dropped out that way with a push (from behind when that block is solid) - or, {@code smart},
	 * handed back when there is a container there at all. */
	static ItemStack ejectStackAt(Level world, BlockPos pos, Direction side, ItemStack out, boolean smart) {
		BlockPos target = pos.relative(side);
		IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, target, side.getOpposite());
		if (inv != null) {
			out = ItemHandlerHelper.insertItemStacked(inv, out, false);
			if (smart) return out;
		}
		if (!out.isEmpty()) {
			if (world.getBlockState(target).isCollisionShapeFullBlock(world, target)) pos = pos.relative(side.getOpposite());
			ItemEntity ei = new ItemEntity(world, pos.getX() + 0.5 + side.getStepX(), pos.getY() + side.getStepY(), pos.getZ() + 0.5 + side.getStepZ(), out);
			ei.setDeltaMovement(0.3 * side.getStepX(), 0.3 * side.getStepY(), 0.3 * side.getStepZ());
			world.addFreshEntity(ei);
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		ProvisionRequest pr = task.getLinkedProvision();
		if (pr == null || pr.getStack() == null || !(golem instanceof EntityThaumcraftGolem g)) return false;
		boolean b = (pr.getSeal() != null && g.isWithinRestriction(pr.getSeal().getSealPos().pos))
				|| (pr.getEntity() != null && g.isWithinRestriction(pr.getEntity().blockPosition()))
				|| (pr.getPos() != null && g.isWithinRestriction(pr.getPos()));
		if (!b || !areGolemTagsValidForTask(pr.getSeal(), g)) return false;
		if (task.getData() == 0) {
			return !golem.isCarrying(pr.getStack()) && golem.canCarry(pr.getStack(), true);
		}
		return golem.isCarrying(pr.getStack());
	}

	/** The original's check that a golem may serve a seal's request: its lock, required and forbidden traits. */
	private static boolean areGolemTagsValidForTask(ISealEntity se, EntityThaumcraftGolem golem) {
		if (se == null) return true;
		if (se.isLocked() && (golem.getOwnerUUID() == null || !golem.getOwnerUUID().toString().equals(se.getOwner()))) return false;
		if (se.getSeal().getRequiredTags() != null && !golem.getProperties().getTraits().containsAll(Arrays.asList(se.getSeal().getRequiredTags()))) {
			return false;
		}
		if (se.getSeal().getForbiddenTags() != null) {
			for (EnumGolemTrait tag : se.getSeal().getForbiddenTags()) {
				if (golem.getProperties().getTraits().contains(tag)) return false;
			}
		}
		return true;
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

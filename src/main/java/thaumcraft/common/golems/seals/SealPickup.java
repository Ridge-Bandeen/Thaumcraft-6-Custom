package thaumcraft.common.golems.seals;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISealConfigArea;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealPickup} - the first logistics {@link SealFiltered} subclass this
 * port ships (real translation of "watch for a matching dropped item, send a golem to grab it", running on
 * the same entity-position {@code thaumcraft.common.golems.ai.AIGotoEntity} infrastructure
 * {@code SealGuard}/{@code SealButcher} already use, since a task type doesn't care whether the "entity" is
 * hostile or a stack of items).
 *
 * Real, documented simplification: the original's own matching went through
 * {@code InventoryUtils.findFirstMatchFromFilter}/{@code ThaumcraftInvHelper.InvFilter} (4 real toggles:
 * metadata/NBT/ore-dictionary/same-mod matching) - neither is ported (still 1.12.2, and this port's own
 * {@link SealFiltered} already made the "match by real Item identity only, no ore-dictionary/metadata
 * fuzziness" call for {@code SealBreaker}, see that class's own doc comment for why). {@link #onTaskCompletion}
 * keeps the original's own real "immediately chain into the next pickup task in range" touch - a golem that
 * just grabbed one item doesn't wait for its next AI evaluation to grab another sitting right next to it.
 */
public class SealPickup extends SealFiltered implements ISealConfigArea {

	private int delay = new Random().nextInt(100);
	/** Task id -> the picked-item entity's own id - real translation of the original's own tracking map. */
	private final Map<Integer, Integer> itemEntities = new HashMap<>();
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_pickup");

	@Override
	public String getKey() {
		return "thaumcraft:pickup";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay++ % 5 != 0) return;
		AABB area = GolemHelper.getBoundsForArea(seal);
		List<ItemEntity> list = world.getEntitiesOfClass(ItemEntity.class, area);
		for (ItemEntity ent : list) {
			if (ent.onGround() && !itemEntities.containsValue(ent.getId()) && matches(ent.getItem())) {
				Task task = new Task(seal.getSealPos(), ent);
				task.setPriority(seal.getPriority());
				itemEntities.put(task.getId(), ent.getId());
				TaskHandler.addTask(world.dimension(), task);
				break;
			}
		}
		if (delay % 100 == 0) {
			Iterator<Integer> it = itemEntities.values().iterator();
			while (it.hasNext()) {
				Entity e = world.getEntity(it.next());
				if (e == null || !e.isAlive()) it.remove();
			}
		}
	}

	private boolean matches(ItemStack stack) {
		if (stack.isEmpty()) return false;
		boolean m = matchesFilter(stack.getItem());
		return isBlacklist() ? !m : m;
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		ItemEntity ei = getItemEntity(world, task);
		if (ei != null && !ei.getItem().isEmpty()) {
			ItemStack leftover = golem.holdItem(ei.getItem());
			if (leftover.isEmpty()) {
				ei.discard();
			} else {
				ei.setItem(leftover);
			}
			golem.getGolemEntity().playSound(SoundEvents.ITEM_PICKUP, 0.2f, 1.0f);
			golem.swingArm();
		}
		task.setSuspended(true);
		itemEntities.remove(task.getId());

		if (golem instanceof EntityThaumcraftGolem realGolem) {
			for (Task ticket : TaskHandler.getEntityTasksSorted(world.dimension(), null, realGolem)) {
				if (itemEntities.containsKey(ticket.getId()) && ticket.getEntity() != null
						&& ticket.canGolemPerformTask(golem) && realGolem.isWithinRestriction(ticket.getEntity().blockPosition())) {
					realGolem.setTask(ticket);
					ticket.setReserved(true);
					realGolem.emote(EntityThaumcraftGolem.EMOTE_ORDER);
					break;
				}
			}
		}
		return true;
	}

	private ItemEntity getItemEntity(Level world, Task task) {
		Integer id = itemEntities.get(task.getId());
		if (id == null) return null;
		return world.getEntity(id) instanceof ItemEntity ie ? ie : null;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		ItemEntity ei = getItemEntity(golem.getGolemWorld(), task);
		if (ei == null) return false;
		if (!ei.isAlive()) {
			task.setSuspended(true);
			return false;
		}
		return golem.canCarry(ei.getItem(), true);
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
		return new int[] { CAT_AREA, CAT_FILTER, CAT_PRIORITY, CAT_TAGS };
	}
}

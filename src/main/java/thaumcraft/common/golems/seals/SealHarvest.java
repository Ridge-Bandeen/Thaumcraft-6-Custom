package thaumcraft.common.golems.seals;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealConfigArea;
import thaumcraft.api.golems.seals.ISealConfigToggles;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.ISealGui;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealHarvest}, as the original. Every fifth tick it looks at one more spot of its
 * area, and a grown crop there ({@link thaumcraft.common.lib.utils.CropUtils#isGrownCrop}: grown wheat and the
 * like, melons, pumpkins, ripe wart, cane and cactus standing on their own kind...) becomes a harvest task: the
 * golem right-clicks it (berries come off that way) and, if it is still grown, breaks it.
 *
 * <p>With "replant" on, the crop's seed (what picking the crop gives: seeds, a potato, cocoa beans) is planted
 * again as a separate task the golem can only take while carrying that seed - so the seeds come out of the harvest
 * or a chest, not from nowhere. Dirt that had been farmland is tilled again first. With "request provisioning" on, a
 * golem without seeds asks for them. Pending replants survive a reload.
 */
public class SealHarvest implements ISeal, ISealGui, ISealConfigArea, ISealConfigToggles {

	private int delay = new Random().nextInt(33);
	/** The area-scan cursor, one step per scan. */
	private int count;
	/** Grown crops with a harvest task already open, so one isn't queued twice. */
	private final Set<Long> tracked = new HashSet<>();
	private final java.util.Map<Long, ReplantInfo> replantTasks = new java.util.HashMap<>();
	private final SealToggle[] props = {
			new SealToggle(true, "prep", "golem.prop.replant"),
			new SealToggle(false, "ppro", "golem.prop.provision"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_harvest");

	/** A spot to replant: which way to click from it, the seed, and whether it was farmland. */
	private static final class ReplantInfo {
		final BlockPos pos;
		final Direction face;
		int taskId;
		final net.minecraft.world.item.ItemStack stack;
		final boolean farmland;

		ReplantInfo(BlockPos pos, Direction face, int taskId, net.minecraft.world.item.ItemStack stack, boolean farmland) {
			this.pos = pos;
			this.face = face;
			this.taskId = taskId;
			this.stack = stack;
			this.farmland = farmland;
		}
	}

	@Override
	public String getKey() {
		return "thaumcraft:harvest";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay % 100 == 0) {
			Iterator<Long> it = tracked.iterator();
			while (it.hasNext()) {
				if (!thaumcraft.common.lib.utils.CropUtils.isGrownCrop(world, BlockPos.of(it.next()))) it.remove();
			}
			// replants left behind outside a resized area are dropped
			net.minecraft.world.phys.AABB area = GolemHelper.getBoundsForArea(seal);
			Iterator<java.util.Map.Entry<Long, ReplantInfo>> rt = replantTasks.entrySet().iterator();
			while (rt.hasNext()) {
				java.util.Map.Entry<Long, ReplantInfo> e = rt.next();
				BlockPos pp = BlockPos.of(e.getKey());
				if (!area.contains(pp.getX() + 0.5, pp.getY() + 0.5, pp.getZ() + 0.5)) {
					Task tt = TaskHandler.getTask(world.dimension(), e.getValue().taskId);
					if (tt != null) tt.setSuspended(true);
					rt.remove();
				}
			}
		}
		if (delay++ % 5 != 0) return;
		BlockPos p = GolemHelper.getPosInArea(seal, count++);
		if (thaumcraft.common.lib.utils.CropUtils.isGrownCrop(world, p)) {
			if (tracked.add(p.asLong())) {
				Task task = new Task(seal.getSealPos(), p);
				task.setPriority(seal.getPriority());
				TaskHandler.addTask(world.dimension(), task);
			}
		} else if (props[0].getValue() && replantTasks.containsKey(p.asLong()) && world.isEmptyBlock(p)) {
			ReplantInfo info = replantTasks.get(p.asLong());
			if (TaskHandler.getTask(world.dimension(), info.taskId) == null) {
				Task replant = new Task(seal.getSealPos(), info.pos);
				replant.setPriority(seal.getPriority());
				TaskHandler.addTask(world.dimension(), replant);
				info.taskId = replant.getId();
			}
		}
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		BlockPos pos = task.getPos();
		if (!(world instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
			task.setSuspended(true);
			return true;
		}
		if (thaumcraft.common.lib.utils.CropUtils.isGrownCrop(world, pos)) {
			BlockState state = world.getBlockState(pos);
			net.minecraft.world.item.ItemStack seed = state.getBlock().getCloneItemStack(world, pos, state);
			thaumcraft.common.golems.GolemInteractionHelper.golemClick(world, golem, pos, task.getSealPos().face, net.minecraft.world.item.ItemStack.EMPTY, false, true);
			if (thaumcraft.common.lib.utils.CropUtils.isGrownCrop(world, pos)) {
				world.destroyBlock(pos, true, golem.getGolemEntity());
				golem.addRankXp(1);
				golem.swingArm();
				if (props[0].getValue() && !seed.isEmpty()) {
					BlockState below = world.getBlockState(pos.below());
					Direction face = null;
					if (state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
						face = state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
					} else if (seed.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
							&& blockItem.getBlock().defaultBlockState().canSurvive(world, pos)) {
						face = Direction.DOWN;
					}
					if (face != null) {
						Task replant = new Task(task.getSealPos(), pos);
						replant.setPriority(task.getPriority());
						replant.setLifespan((short) 300);
						replantTasks.put(pos.asLong(), new ReplantInfo(pos, face, replant.getId(), seed.copyWithCount(1),
								below.getBlock() instanceof net.minecraft.world.level.block.FarmBlock));
						TaskHandler.addTask(world.dimension(), replant);
					}
				}
			}
		} else {
			ReplantInfo info = replantTasks.get(pos.asLong());
			if (info != null && info.taskId == task.getId() && world.isEmptyBlock(pos) && golem.isCarrying(info.stack)) {
				net.neoforged.neoforge.common.util.FakePlayer fp = thaumcraft.common.golems.GolemInteractionHelper.fakePlayer(serverLevel, golem);
				BlockState below = world.getBlockState(pos.below());
				if (info.farmland && (below.is(net.minecraft.world.level.block.Blocks.DIRT) || below.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK))) {
					// as the original: till it again first
					world.setBlockAndUpdate(pos.below(), net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState());
				}
				net.minecraft.world.item.ItemStack seed = info.stack.copyWithCount(1);
				fp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, seed.copy());
				BlockPos clicked = pos.relative(info.face);
				var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(clicked), info.face.getOpposite(), clicked, false);
				var result = fp.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
						.useOn(new net.minecraft.world.item.context.UseOnContext(fp, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
				fp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
				if (result.consumesAction()) {
					world.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(world.getBlockState(pos)));
					golem.dropItem(seed);
					golem.addRankXp(1);
					golem.swingArm();
				}
			}
		}
		tracked.remove(pos.asLong());
		task.setSuspended(true);
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		ReplantInfo info = replantTasks.get(task.getPos().asLong());
		if (info != null && info.taskId == task.getId()) {
			boolean carrying = golem.isCarrying(info.stack);
			if (!carrying && props[1].getValue()) {
				ISealEntity se = GolemHelper.getSealEntity(golem.getGolemWorld().dimension(), task.getSealPos());
				if (se != null) GolemHelper.requestProvisioning(golem.getGolemWorld(), se, info.stack);
			}
			return carrying;
		}
		return true;
	}

	@Override
	public void onTaskSuspension(Level world, Task task) {
		tracked.remove(task.getPos().asLong());
	}

	@Override
	public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
	}

	@Override
	public void readCustomNBT(CompoundTag nbt) {
		replantTasks.clear();
		net.minecraft.nbt.ListTag list = nbt.getList("replant", 10);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag tag = list.getCompound(i);
			long loc = tag.getLong("taskloc");
			Direction face = Direction.from3DDataValue(tag.getByte("taskface"));
			net.minecraft.world.item.ItemStack stack = net.minecraft.core.registries.BuiltInRegistries.ITEM
					.getOptional(ResourceLocation.tryParse(tag.getString("seed"))).map(net.minecraft.world.item.ItemStack::new)
					.orElse(net.minecraft.world.item.ItemStack.EMPTY);
			if (!stack.isEmpty()) replantTasks.put(loc, new ReplantInfo(BlockPos.of(loc), face, 0, stack, tag.getBoolean("farmland")));
		}
	}

	@Override
	public void writeCustomNBT(CompoundTag nbt) {
		if (!props[0].getValue()) return;
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (ReplantInfo info : replantTasks.values()) {
			CompoundTag tag = new CompoundTag();
			tag.putLong("taskloc", info.pos.asLong());
			tag.putByte("taskface", (byte) info.face.get3DDataValue());
			tag.putBoolean("farmland", info.farmland);
			tag.putString("seed", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(info.stack.getItem()).toString());
			list.add(tag);
		}
		nbt.put("replant", list);
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
	public Object returnContainer(Level world, Player player, BlockPos pos, Direction side, ISealEntity seal) {
		return null;
	}

	@Override
	public Object returnGui(Level world, Player player, BlockPos pos, Direction side, ISealEntity seal) {
		return null;
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
		return new int[] { CAT_AREA, CAT_TOGGLES, CAT_PRIORITY, CAT_TAGS };
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

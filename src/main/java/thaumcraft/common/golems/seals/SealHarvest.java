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
 * Replaces the old (1.12.2) {@code SealHarvest} - the second block-position {@link ISeal} this port ships
 * (see {@code SealLumber} for the first, and its own doc comment for the shared
 * {@code thaumcraft.common.golems.ai.AIGotoBlock} both run on). Scans its area for fully-grown standard
 * crops (wheat/carrots/potatoes/beetroot - real vanilla {@link CropBlock} subclasses, checked via that
 * class's own real {@code isMaxAge}) and turns each into a harvest task; completing one breaks the crop for
 * real drops and, if the real {@code prep}/replant toggle is on (default), immediately replants it at age 0.
 *
 * Real, documented simplification: the original's own {@code CropUtils.isGrownCrop}/{@code clickableCrops}
 * also covered melons, pumpkins, nether wart, and cocoa (harvested via a right-click-block simulation
 * through a {@code FakePlayer}, not a break) - {@code CropUtils}/that FakePlayer-interaction path aren't
 * ported (still 1.12.2, and this port has never needed FakePlayer-driven block-click emulation for anything
 * else either). The 4 standard replantable crops are still real and fully functional; the original's more
 * involved seed-facing/farmland-tilling replant logic (needed for those FakePlayer-clicked crops) isn't
 * needed here either, since replanting a real {@code CropBlock} is just resetting its own age property.
 *
 * Unlike {@code SealGuard}/{@code SealLumber}, this really does implement {@link ISealConfigToggles} (the
 * original declared it too) - the {@code prep} (replant) toggle is real and persists through
 * {@code SealEntity}'s own NBT round trip even without a config GUI to flip it from in-game yet (same
 * "real state, reachable via NBT/creative tools until a screen exists" pattern this whole arc has used).
 */
public class SealHarvest implements ISeal, ISealGui, ISealConfigArea, ISealConfigToggles {

	private int delay = new Random().nextInt(33);
	/** Separate from {@link #delay} (which only gates how often a scan happens) - this is the real area-scan
	 * cursor, incrementing once per scan so {@code getPosInArea} eventually walks every offset in the area.
	 * A real bug caught by this class's own GameTest: an earlier draft passed {@code delay} itself (which
	 * only ever advances by 5-tick-aligned jumps once gated) into {@code getPosInArea}, so the area cursor
	 * only ever landed on {@code count % 5 == 0} values - permanently skipping 4 out of every 5 offsets. */
	private int count;
	private final Set<Long> tracked = new HashSet<>();
	private final SealToggle[] props = {
			new SealToggle(true, "prep", "golem.prop.replant"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_harvest");

	@Override
	public String getKey() {
		return "thaumcraft:harvest";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay % 100 == 0) {
			Iterator<Long> it = tracked.iterator();
			while (it.hasNext()) {
				if (!isGrownCrop(world, BlockPos.of(it.next()))) it.remove();
			}
		}
		delay++;
		if (delay % 5 != 0) return;
		BlockPos p = GolemHelper.getPosInArea(seal, count++);
		if (!tracked.contains(p.asLong()) && isGrownCrop(world, p)) {
			Task task = new Task(seal.getSealPos(), p);
			task.setPriority(seal.getPriority());
			TaskHandler.addTask(world.dimension(), task);
			tracked.add(p.asLong());
		}
	}

	private static boolean isGrownCrop(Level world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		BlockPos pos = task.getPos();
		if (isGrownCrop(world, pos)) {
			BlockState state = world.getBlockState(pos);
			CropBlock crop = (CropBlock) state.getBlock();
			golem.swingArm();
			world.destroyBlock(pos, true);
			golem.addRankXp(1);
			if (props[0].getValue()) {
				world.setBlockAndUpdate(pos, crop.getStateForAge(0));
			}
		}
		tracked.remove(pos.asLong());
		task.setSuspended(true);
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		if (isGrownCrop(golem.getGolemWorld(), task.getPos())) {
			return true;
		}
		task.setSuspended(true);
		return false;
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
	}

	@Override
	public void writeCustomNBT(CompoundTag nbt) {
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
		return new int[] { CAT_AREA, CAT_PRIORITY, CAT_TOGGLES, CAT_TAGS };
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

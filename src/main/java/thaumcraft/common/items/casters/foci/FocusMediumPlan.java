package thaumcraft.common.items.casters.foci;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusMedium;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.api.items.IArchitect;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.items.casters.ItemCaster;

/**
 * Replaces the old (1.12.2) {@code FocusMediumPlan}, as the original: from the block the caster looks at (16 blocks),
 * the spell's next effect works on every block of an area - "full" takes every non-air block in a box reaching into
 * the clicked face, "surface" floods across matching blocks along that face that are open to the air - nearest first.
 * The area's size is the caster's own (0-3 each way, {@link ItemCaster#getArea}, set with the misc caster key), and as an
 * {@link IArchitect} it shows the blocks it will touch and which axes the key will change.
 */
public class FocusMediumPlan extends FocusMedium implements IArchitect {

	private final List<BlockPos> checked = new ArrayList<>();

	@Override
	public String getResearch() {
		return "FOCUSPLAN";
	}

	@Override
	public String getKey() {
		return "thaumcraft.PLAN";
	}

	@Override
	public int getComplexity() {
		return 4;
	}

	@Override
	public Aspect getAspect() {
		return Aspect.CRAFT;
	}

	@Override
	public NodeSetting[] createSettings() {
		return new NodeSetting[] { new NodeSetting("method", "focus.plan.method",
				new NodeSetting.NodeSettingIntList(new int[] { 0, 1 }, new String[] { "focus.plan.full", "focus.plan.surface" })) };
	}

	@Override
	public HitResult[] supplyTargets() {
		if (getParent() == null || !(getPackage().getCaster() instanceof Player player)) return new HitResult[0];
		Level level = getPackage().world;
		if (level == null) return new HitResult[0];
		ItemStack casterStack = player.getMainHandItem().getItem() instanceof ICaster ? player.getMainHandItem()
				: player.getOffhandItem().getItem() instanceof ICaster ? player.getOffhandItem() : ItemStack.EMPTY;

		List<HitResult> targets = new ArrayList<>();
		for (Trajectory source : getParent().supplyTrajectories()) {
			Vec3 end = source.source.add(source.direction.normalize().scale(16.0));
			BlockHitResult hit = level.clip(new ClipContext(source.source, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
			if (hit.getType() != HitResult.Type.BLOCK) continue;
			BlockPos origin = hit.getBlockPos();
			List<BlockPos> found = getArchitectBlocks(casterStack, level, origin, hit.getDirection(), player);
			found.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
			for (BlockPos p : found) {
				targets.add(new BlockHitResult(Vec3.atCenterOf(p), hit.getDirection(), p, false));
			}
		}
		return targets.toArray(new HitResult[0]);
	}

	@Override
	public HitResult getArchitectMOP(ItemStack stack, Level level, LivingEntity player) {
		Vec3 start = player.getEyePosition();
		return level.clip(new ClipContext(start, start.add(player.getLookAngle().scale(16.0)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
	}

	@Override
	public boolean useBlockHighlight(ItemStack stack) {
		return false;
	}

	@Override
	public boolean showAxis(ItemStack stack, Level level, Player player, Direction side, EnumAxis axis) {
		if (stack == null || stack.isEmpty()) return false;
		int dim = ItemCaster.getArea(stack).dim();
		if (getSettingValue("method") == 0) {
			return switch (axis) {
				case Y -> dim == 0 || dim == 3;
				case Z -> dim == 0 || dim == 2;
				case X -> dim == 0 || dim == 1;
			};
		}
		return switch (side.getAxis()) {
			case Y -> (axis == EnumAxis.X && (dim == 0 || dim == 1)) || (axis == EnumAxis.Z && (dim == 0 || dim == 2));
			case Z -> (axis == EnumAxis.Y && (dim == 0 || dim == 1)) || (axis == EnumAxis.X && (dim == 0 || dim == 2));
			case X -> (axis == EnumAxis.Y && (dim == 0 || dim == 1)) || (axis == EnumAxis.Z && (dim == 0 || dim == 2));
		};
	}

	@Override
	public ArrayList<BlockPos> getArchitectBlocks(ItemStack stack, Level level, BlockPos pos, Direction side, Player player) {
		ArrayList<BlockPos> out = new ArrayList<>();
		if (stack == null || stack.isEmpty()) return out;
		ThaumcraftDataComponents.CasterArea area = ItemCaster.getArea(stack);
		checked.clear();
		if (getSettingValue("method") == 0) {
			checkNeighboursFull(level, pos, pos, side, area.x(), area.y(), area.z(), out);
		} else {
			BlockState state = level.getBlockState(pos);
			if (side.getAxis() == Direction.Axis.Z) {
				checkNeighboursSurface(level, pos, state, pos, side, area.z(), area.y(), area.x(), out);
			} else {
				checkNeighboursSurface(level, pos, state, pos, side, area.x(), area.y(), area.z(), out);
			}
		}
		return out;
	}

	/** The original's checkNeighboursFull: every non-air block in a box shifted to reach into the clicked face. */
	private void checkNeighboursFull(Level level, BlockPos pos1, BlockPos pos2, Direction side, int sizeX, int sizeY, int sizeZ, List<BlockPos> list) {
		if (checked.contains(pos2)) return;
		checked.add(pos2);
		if (!level.getBlockState(pos2).isAir()) list.add(pos2);
		int xs = pos1.getX() - sizeX - sizeX * side.getStepX();
		int xe = pos1.getX() + sizeX - sizeX * side.getStepX();
		int ys = pos1.getY() - sizeY - sizeY * side.getStepY();
		int ye = pos1.getY() + sizeY - sizeY * side.getStepY();
		int zs = pos1.getZ() - sizeZ - sizeZ * side.getStepZ();
		int ze = pos1.getZ() + sizeZ - sizeZ * side.getStepZ();
		for (Direction dir : Direction.values()) {
			BlockPos q = pos2.relative(dir);
			if (q.getX() >= xs && q.getX() <= xe && q.getY() >= ys && q.getY() <= ye && q.getZ() >= zs && q.getZ() <= ze) {
				checkNeighboursFull(level, pos1, q, side, sizeX, sizeY, sizeZ, list);
			}
		}
	}

	/** The original's checkNeighboursSurface: a flood across the face of blocks matching the clicked one and open to the
	 * air (a neighbour that isn't a full opaque cube). */
	private void checkNeighboursSurface(Level level, BlockPos pos1, BlockState match, BlockPos pos2, Direction side, int sizeX, int sizeY, int sizeZ, List<BlockPos> list) {
		if (checked.contains(pos2)) return;
		checked.add(pos2);
		switch (side.getAxis()) {
			case Y -> {
				if (Math.abs(pos2.getX() - pos1.getX()) > sizeX || Math.abs(pos2.getZ() - pos1.getZ()) > sizeZ) return;
			}
			case Z -> {
				if (Math.abs(pos2.getX() - pos1.getX()) > sizeX || Math.abs(pos2.getY() - pos1.getY()) > sizeZ) return;
			}
			case X -> {
				if (Math.abs(pos2.getY() - pos1.getY()) > sizeX || Math.abs(pos2.getZ() - pos1.getZ()) > sizeZ) return;
			}
		}
		BlockState state = level.getBlockState(pos2);
		if (state.equals(match) && isExposed(level, pos2) && !state.isAir()) {
			list.add(pos2);
			for (Direction dir : Direction.values()) {
				if (dir != side && dir.getOpposite() != side) {
					checkNeighboursSurface(level, pos1, match, pos2.relative(dir), side, sizeX, sizeY, sizeZ, list);
				}
			}
		}
	}

	/** The original's BlockUtils.isBlockExposed: some neighbour isn't an opaque cube. */
	private static boolean isExposed(Level level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			BlockPos n = pos.relative(dir);
			if (!level.getBlockState(n).isSolidRender(level, n)) return true;
		}
		return false;
	}

	@Override
	public boolean execute(Trajectory trajectory) {
		return true;
	}
}

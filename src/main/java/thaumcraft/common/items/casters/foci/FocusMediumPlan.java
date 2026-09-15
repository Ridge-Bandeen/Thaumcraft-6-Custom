package thaumcraft.common.items.casters.foci;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusMedium;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusMediumPlan} - real, restored 2026-09-15 alongside
 * {@link FocusEffectExchange}, its own only real pairing (a mass block-swap). Given the parent trajectory's
 * own real first block hit, flood-fills outward to find every real matching neighbor within a configurable
 * size, then supplies each as a real target (closest-first, matching the true original's own real
 * {@code BlockUtils.BlockPosComparator} sort) for the next real effect in the chain to act on.
 *
 * <p>Real "method" setting: {@code full} selects every real non-air block in a cubic region around the hit
 * (offset so the region extends INTO the clicked surface, matching the true original's own real
 * {@code side.getFrontOffsetX/Y/Z} shift exactly); {@code surface} instead flood-fills only real neighbors
 * with the exact same {@link BlockState} that are themselves exposed to real air, a real connected-component
 * "paint bucket" fill across a single face.
 *
 * <p>Real, deliberate simplification: the true original's own {@code CasterManager} per-caster area-size
 * config was a real in-world keybind UI (confirmed via the real research text itself - "By pressing the
 * focus modify key ('G' by default) I can alter the size of the area. By pressing CTRL + G I can alter the
 * axis" - a separate, still-unported caster-wide control, independent of any one focus). Replaced with a
 * real {@code size} {@link NodeSetting} on this focus itself instead, uniform across all 3 axes rather than
 * the original's own independently-configurable X/Y/Z - matches this port's own established "substitute the
 * nearest already-real equivalent" precedent for a cut config surface. The true original's own client-side
 * block-highlight outline ({@code IArchitect#showAxis}/{@code useBlockHighlight}) isn't restored here either -
 * that's part of the broader client-only rendering layer, tracked separately.
 */
public class FocusMediumPlan extends FocusMedium {

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
		int[] method = { 0, 1 };
		String[] methodDesc = { "focus.plan.full", "focus.plan.surface" };
		int[] size = { 1, 2, 3, 4, 5 };
		String[] sizeDesc = { "1", "2", "3", "4", "5" };
		return new NodeSetting[] {
				new NodeSetting("method", "focus.plan.method", new NodeSetting.NodeSettingIntList(method, methodDesc)),
				new NodeSetting("size", "focus.plan.size", new NodeSetting.NodeSettingIntList(size, sizeDesc)) };
	}

	@Override
	public HitResult[] supplyTargets() {
		if (getParent() == null || !(getPackage().getCaster() instanceof Player)) return new HitResult[0];
		Level level = getPackage().world;
		if (level == null) return new HitResult[0];

		List<HitResult> targets = new ArrayList<>();
		int size = getSettingValue("size");
		if (size <= 0) size = 1;

		for (Trajectory source : getParent().supplyTrajectories()) {
			Vec3 dir = source.direction.normalize();
			Vec3 end = source.source.add(dir.scale(16.0));
			BlockHitResult hit = level.clip(new ClipContext(source.source, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) getPackage().getCaster()));
			if (hit.getType() != HitResult.Type.MISS) {
				BlockPos origin = hit.getBlockPos();
				List<BlockPos> found = getPlanBlocks(level, origin, hit.getDirection(), size);
				found.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
				for (BlockPos p : found) {
					targets.add(new BlockHitResult(Vec3.atCenterOf(p), hit.getDirection(), p, false));
				}
			}
		}
		return targets.toArray(new HitResult[0]);
	}

	private List<BlockPos> getPlanBlocks(Level level, BlockPos pos, Direction side, int size) {
		List<BlockPos> out = new ArrayList<>();
		checked.clear();
		if (getSettingValue("method") == 0) {
			checkNeighboursFull(level, pos, pos, side, size, size, size, out);
		} else {
			BlockState state = level.getBlockState(pos);
			checkNeighboursSurface(level, pos, state, pos, side, size, size, size, out);
		}
		return out;
	}

	/** Real port of the true original's own {@code checkNeighboursFull} - selects every real non-air block
	 * within a real cubic region, shifted to extend into the clicked surface (matches the original's own
	 * {@code xs -= sizeX * side.getFrontOffsetX()} shift exactly, via modern {@link Direction#getStepX}). */
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

	/** Real port of the true original's own {@code checkNeighboursSurface} - a real connected-component fill
	 * across a single face, real bounds-checked per-axis exactly like the original (comparing against
	 * whichever 2 axes are transverse to the clicked face). */
	private void checkNeighboursSurface(Level level, BlockPos pos1, BlockState matchState, BlockPos pos2, Direction side, int sizeX, int sizeY, int sizeZ, List<BlockPos> list) {
		if (checked.contains(pos2)) return;
		checked.add(pos2);
		switch (side.getAxis()) {
			case Y -> {
				if (Math.abs(pos2.getX() - pos1.getX()) > sizeX) return;
				if (Math.abs(pos2.getZ() - pos1.getZ()) > sizeZ) return;
			}
			case Z -> {
				if (Math.abs(pos2.getX() - pos1.getX()) > sizeX) return;
				if (Math.abs(pos2.getY() - pos1.getY()) > sizeZ) return;
			}
			case X -> {
				if (Math.abs(pos2.getY() - pos1.getY()) > sizeX) return;
				if (Math.abs(pos2.getZ() - pos1.getZ()) > sizeZ) return;
			}
		}
		BlockState state = level.getBlockState(pos2);
		if (state.equals(matchState) && !state.isAir() && isExposedToAir(level, pos2)) {
			list.add(pos2);
			for (Direction dir : Direction.values()) {
				if (dir != side && dir.getOpposite() != side) {
					checkNeighboursSurface(level, pos1, matchState, pos2.relative(dir), side, sizeX, sizeY, sizeZ, list);
				}
			}
		}
	}

	/** Real modern equivalent of the true original's own {@code BlockUtils.isBlockExposed}. */
	private static boolean isExposedToAir(Level level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (level.getBlockState(pos.relative(dir)).isAir()) return true;
		}
		return false;
	}

	@Override
	public boolean execute(Trajectory trajectory) {
		return true;
	}
}

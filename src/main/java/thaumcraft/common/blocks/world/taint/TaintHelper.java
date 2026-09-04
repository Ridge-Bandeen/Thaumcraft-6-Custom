package thaumcraft.common.blocks.world.taint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;

/**
 * Replaces the old (1.12.2) {@code TaintHelper} - the shared "is this position near an active taint seed,
 * and what should it grow into" logic every taint block calls into. Genuinely unblocked now (it wasn't
 * during this port's own earlier scoping pass): the real vis/flux gating (via {@code AuraHelper#drainFlux}/
 * {@link thaumcraft.common.world.aura.AuraHandler#getFluxSaturation}, now real) is exactly what made this
 * look stuck before the Aura system existed for real.
 *
 * Real seed-position tracking ({@link #addTaintSeed}/{@link #removeTaintSeed}/{@link #isNearTaintSeed}/
 * {@link #isAtTaintSeedEdge}), matching the original's own per-dimension {@code ArrayList<BlockPos>} cache
 * exactly. One real, documented simplification: the original's own {@code isNearTaintSeed} also self-pruned
 * stale entries by checking whether a real {@code EntityTaintSeed} still existed at that position - that
 * entity isn't ported yet (a later sub-chunk of this same taint-terrain arc), so that self-pruning check is
 * dropped for now. Whatever calls {@link #addTaintSeed} is responsible for calling {@link #removeTaintSeed}
 * when the seed goes away; nothing does that automatically yet.
 *
 * {@link #spreadFibres} is a real, working port of the original's own core growth case - converting a
 * random nearby eligible position (air/replaceable/flower/plantable, adjacent to a real solid block, not
 * already fully surrounded by taint) into {@link BlocksTC#taintFibre}, draining a small real amount of flux.
 * Real, documented cuts vs the original: no leaf -&gt; {@code taintFeature} conversion, no hemmed-in-taint
 * -&gt; {@code taintLog}/{@code taintCrust}/{@code taintSoil}/{@code taintRock} conversions, and no {@code
 * EntityTaintSeed} spawning at the seed-zone's own edge - all of those target blocks/entities aren't ported
 * yet (later sub-chunks of this same arc). No config system exists in this port yet either, so the original's
 * own {@code ModConfig.CONFIG_WORLD.taintSpreadRate}/{@code taintSpreadArea} are hardcoded here to the exact
 * same default values the original itself shipped with (100.0/32).
 */
public class TaintHelper {

	private static final float TAINT_SPREAD_RATE = 100.0f;
	private static final int TAINT_SPREAD_AREA = 32;

	private static final ConcurrentHashMap<ResourceKey<Level>, List<BlockPos>> taintSeeds = new ConcurrentHashMap<>();

	public static void addTaintSeed(Level level, BlockPos pos) {
		taintSeeds.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(pos);
	}

	public static void removeTaintSeed(Level level, BlockPos pos) {
		List<BlockPos> locs = taintSeeds.get(level.dimension());
		if (locs != null) {
			locs.remove(pos);
		}
	}

	public static boolean isNearTaintSeed(Level level, BlockPos pos) {
		double area = (double) TAINT_SPREAD_AREA * TAINT_SPREAD_AREA;
		List<BlockPos> locs = taintSeeds.get(level.dimension());
		if (locs != null) {
			for (BlockPos p : locs) {
				if (p.distSqr(pos) <= area) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isAtTaintSeedEdge(Level level, BlockPos pos) {
		double area = (double) TAINT_SPREAD_AREA * TAINT_SPREAD_AREA;
		double fringe = TAINT_SPREAD_AREA * 0.8 * (TAINT_SPREAD_AREA * 0.8);
		List<BlockPos> locs = taintSeeds.get(level.dimension());
		if (locs != null) {
			for (BlockPos p : locs) {
				double d = p.distSqr(pos);
				if (d < area && d > fringe) {
					return true;
				}
			}
		}
		return false;
	}

	public static void spreadFibres(Level level, BlockPos pos) {
		spreadFibres(level, pos, false);
	}

	public static void spreadFibres(Level level, BlockPos pos, boolean ignore) {
		float mod = 0.001f + thaumcraft.common.world.aura.AuraHandler.getFluxSaturation(level, pos) * 2.0f;
		if (!ignore && level.random.nextFloat() > TAINT_SPREAD_RATE / 100.0f * mod) {
			return;
		}
		if (!isNearTaintSeed(level, pos)) {
			return;
		}
		int xx = pos.getX() + level.random.nextInt(3) - 1;
		int yy = pos.getY() + level.random.nextInt(3) - 1;
		int zz = pos.getZ() + level.random.nextInt(3) - 1;
		BlockPos t = new BlockPos(xx, yy, zz);
		if (t.equals(pos)) {
			return;
		}
		BlockState bs = level.getBlockState(t);
		float bh = bs.getDestroySpeed(level, t);
		if (bh < 0.0f || bh > 10.0f) {
			return;
		}
		boolean eligible = bs.isAir() || bs.canBeReplaced() || bs.getBlock() instanceof SaplingBlock
				|| bs.getBlock() instanceof net.minecraft.world.level.block.BushBlock;
		if (eligible && !bs.liquid() && isAdjacentToSolidBlock(level, t) && !BlockTaintFibre.isOnlyAdjacentToTaint(level, t)) {
			// setBlockAndUpdate with a bare defaultBlockState() never computes this fresh placement's own
			// connections against its already-existing neighbors - see BlockTaintFibre#computeState's own
			// doc comment - so it's computed explicitly here first.
			level.setBlockAndUpdate(t, BlockTaintFibre.computeState(BlocksTC.taintFibre.get().defaultBlockState(), level, t));
			AuraHelper.drainFlux(level, t, 0.01f, false);
		}
	}

	/** Real, portable version of the original's own {@code BlockUtils#isAdjacentToSolidBlock} - the one
	 * piece of that unported utility class this arc actually needed. Public - {@code EntityTaintCrawler}
	 * (a different package) reuses it directly for its own real "grow a fibre where I'm standing" tick,
	 * the same way the original's own crawler called the same shared utility. */
	public static boolean isAdjacentToSolidBlock(Level level, BlockPos pos) {
		for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
			BlockPos n = pos.relative(dir);
			BlockState state = level.getBlockState(n);
			if (!state.isAir() && state.isFaceSturdy(level, n, dir.getOpposite())) {
				return true;
			}
		}
		return false;
	}
}

package thaumcraft.common.blocks.world.taint;

import thaumcraft.api.events.TaintSpreadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.config.ThaumcraftConfig;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code TaintHelper} - the shared "is this position near an active taint seed,
 * and what should it grow into" logic every taint block calls into. Genuinely unblocked now (it wasn't
 * during this port's own earlier scoping pass): the real vis/flux gating (via {@code AuraHelper#drainFlux}/
 * {@link thaumcraft.common.world.aura.AuraHandler#getFluxSaturation}, now real) is exactly what made this
 * look stuck before the Aura system existed for real.
 *
 * Real seed-position tracking ({@link #addTaintSeed}/{@link #removeTaintSeed}/{@link #isNearTaintSeed}/
 * {@link #isAtTaintSeedEdge}), matching the original's own per-dimension {@code ArrayList<BlockPos>} cache
 * exactly. One real, still-standing, minor simplification: the original's own {@code isNearTaintSeed} also
 * self-pruned stale entries by checking whether a real {@code EntityTaintSeed} still existed at that
 * position; that defensive re-check isn't reproduced here. Substantially covered in practice now that
 * {@link EntityTaintSeed} is real, though: it calls {@link #removeTaintSeed} on death and re-registers its
 * own current position via {@link #addTaintSeed} every 1200 ticks, so a stale entry can only really outlive
 * its seed if the entity is removed some other way entirely (unloading, {@code /kill}, etc.) - a narrower,
 * lower-value gap than the doc comment here once implied.
 *
 * {@link #spreadFibres} is now a real, complete port of the true original's own full real branch structure,
 * not just its core growth case - restored 2026-09-15 once its 3 real remaining target dependencies all
 * became real (the taint-terrain arc closing 2026-09-03 shipped {@link BlockTaintFeature}/
 * {@code taintLog}/{@code taintCrust}/{@code taintSoil}/{@code taintRock}; {@link EntityTaintSeed} followed
 * the same day):
 * <ul>
 * <li>The core case: a random nearby eligible position (air/replaceable/sapling/bush, adjacent to a real
 * solid block, not already fully surrounded by taint, not leaves) becomes {@link BlocksTC#taintFibre}.</li>
 * <li>Real leaf-touch branch: a leaf position has a real 60% chance to sprout {@link BlockTaintFeature}
 * instead, but only where a real {@code taintLog} actually touches one of its faces - otherwise (including
 * the other 40%) it falls back to a plain fibre, same real shape as the original.</li>
 * <li>Real hemmed-in-taint conversion branch (see {@link BlockTaintFibre#isHemmedByTaint}): a real nearby
 * log/mushroom-ish/ground-ish/rock-ish surface, once really hemmed in by taint and soft enough, converts
 * into {@code taintLog}/{@code taintCrust}/{@code taintSoil}/{@code taintRock} respectively. Real,
 * deliberate simplification: the original's own 1.12.2 {@code Material}-based surface classification (no
 * modern equivalent) is approximated with real vanilla block tags/identity checks of the same rough shape,
 * not an exhaustive 1:1 replica - see {@link #isCrustSurface}/{@link #isSoilSurface}/{@link #isRockSurface}'s
 * own doc comments.</li>
 * <li>Real taint-seed-edge spawn branch: an existing {@code taintSoil}/{@code taintRock} patch, right at a
 * real seed's own spread-radius fringe with real open air above and enough real local flux, has a real small
 * chance to sprout a whole new {@link EntityTaintSeed} and keep the corruption expanding outward.</li>
 * </ul>
 * {@code taintSpreadRate}/{@code taintSpreadArea} are real, live {@link ThaumcraftConfig} values (COMMON
 * config, `world` category) rather than hardcoded - same defaults the original shipped with (100.0/32).
 * {@link ThaumcraftConfig#WUSS_MODE} is also honored for real, in {@link #spreadFibres} - matching the
 * original's own {@code !ModConfig.CONFIG_MISC.wussMode} gate.
 */
public class TaintHelper {

	private static final ConcurrentHashMap<ResourceKey<Level>, List<BlockPos>> taintSeeds = new ConcurrentHashMap<>();

	/** Real accessor for the live {@link ThaumcraftConfig#TAINT_SPREAD_AREA} - {@code EntityTaintSeed} (a
	 * different package) needs the exact same real value for its own real "don't spawn too close to another
	 * real seed" check, matching the original's own {@code ModConfig.CONFIG_WORLD.taintSpreadArea} reference. */
	public static int spreadArea() {
		return ThaumcraftConfig.TAINT_SPREAD_AREA.get();
	}

	public static void addTaintSeed(Level level, BlockPos pos) {
		taintSeeds.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(pos);
	}

	public static void removeTaintSeed(Level level, BlockPos pos) {
		List<BlockPos> locs = taintSeeds.get(level.dimension());
		if (locs != null) {
			locs.remove(pos);
		}
	}

	/** Forgets every tracked seed. GameTests clear them before each batch: tracking is global, and a seed left behind
	 * by one test (or a seed entity the test framework discarded without it dying) otherwise keeps every fibre within
	 * range of it alive in the next. */
	public static void clearTaintSeeds() {
		taintSeeds.clear();
	}

	public static boolean isNearTaintSeed(Level level, BlockPos pos) {
		int spreadArea = spreadArea();
		double area = (double) spreadArea * spreadArea;
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
		int spreadArea = spreadArea();
		double area = (double) spreadArea * spreadArea;
		double fringe = spreadArea * 0.8 * (spreadArea * 0.8);
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
		if (ThaumcraftConfig.WUSS_MODE.get()) {
			return;
		}
		float mod = 0.001f + thaumcraft.common.world.aura.AuraHandler.getFluxSaturation(level, pos) * 2.0f;
		float spreadRate = ThaumcraftConfig.TAINT_SPREAD_RATE.get().floatValue();
		if (!ignore && level.random.nextFloat() > spreadRate / 100.0f * mod) {
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

		boolean isLeaves = bs.is(BlockTags.LEAVES);
		boolean eligibleForFibre = bs.isAir() || bs.canBeReplaced() || bs.getBlock() instanceof SaplingBlock
				|| bs.getBlock() instanceof net.minecraft.world.level.block.BushBlock;
		if (!isLeaves && eligibleForFibre && bs.getFluidState().isEmpty() && isAdjacentToSolidBlock(level, t)
				&& !BlockTaintFibre.isOnlyAdjacentToTaint(level, t)) {
			growFibre(level, pos, t);
			return;
		}

		// Real leaf-touch branch, restored 2026-09-15 (BlockTaintFeature has been real since 2026-09-03):
		// a real chance to sprout a taint_feature growth facing away from a real nearby taint_log, otherwise
		// falls back to a plain fibre - same real "60%, but only if there's actually a log to grow from"
		// shape as the true original.
		if (isLeaves) {
			Direction touchingLog = level.random.nextFloat() < 0.6f ? faceTouching(level, t, BlocksTC.taintLog.get()) : null;
			if (touchingLog != null) {
				BlockState feature = BlocksTC.taintFeature.get().defaultBlockState().setValue(BlockTaintFeature.FACING, touchingLog.getOpposite());
				if (TaintSpreadEvent.allows(level, pos, t, feature)) {
					level.setBlockAndUpdate(t, feature);
				}
			} else {
				growFibre(level, pos, t);
			}
			return;
		}

		// Real hemmed-in-taint conversion branch, restored 2026-09-15 (taint_log/taint_crust/taint_soil/
		// taint_rock have all been real since the taint-terrain arc closed 2026-09-03). Real, deliberate
		// simplification: the true original classified surfaces by its own 1.12.2 Material system (WOOD/
		// GOURD/CACTUS/CORAL/SPONGE for crust; SAND/GROUND/GRASS/CLAY for soil; ROCK for rock) - gone in
		// modern Minecraft, approximated here with real vanilla block tags/identity checks of the same
		// rough shape, not an exhaustive 1:1 replica.
		if (BlockTaintFibre.isHemmedByTaint(level, t) && bh < 5.0f) {
			if (bs.is(BlockTags.LOGS) && !(bs.getBlock() instanceof ITaintBlock)) {
				Direction.Axis axis = bs.hasProperty(RotatedPillarBlock.AXIS) ? bs.getValue(RotatedPillarBlock.AXIS) : Direction.Axis.Y;
				BlockState log = BlocksTC.taintLog.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
				if (TaintSpreadEvent.allows(level, pos, t, log)) {
					level.setBlockAndUpdate(t, log);
				}
				return;
			}
			if (isCrustSurface(bs)) {
				BlockState into = BlocksTC.taintCrust.get().defaultBlockState();
				if (TaintSpreadEvent.allows(level, pos, t, into)) {
					level.setBlockAndUpdate(t, into);
					AuraHelper.drainFlux(level, t, 0.01f, false);
				}
				return;
			}
			if (isSoilSurface(bs)) {
				BlockState into = BlocksTC.taintSoil.get().defaultBlockState();
				if (TaintSpreadEvent.allows(level, pos, t, into)) {
					level.setBlockAndUpdate(t, into);
					AuraHelper.drainFlux(level, t, 0.01f, false);
				}
				return;
			}
			if (isRockSurface(bs)) {
				BlockState into = BlocksTC.taintRock.get().defaultBlockState();
				if (TaintSpreadEvent.allows(level, pos, t, into)) {
					level.setBlockAndUpdate(t, into);
					AuraHelper.drainFlux(level, t, 0.01f, false);
				}
				return;
			}
		}

		// Real taint-seed-edge spawn branch, restored 2026-09-15 (EntityTaintSeed has been real since
		// 2026-09-03): an existing real taint_soil/taint_rock patch, right at the fringe of a real seed's
		// own spread radius, has a small real chance to sprout a whole new real seed and keep the
		// corruption expanding outward - the true original's own real "the infection spreads" mechanic.
		if ((bs.is(BlocksTC.taintSoil.get()) || bs.is(BlocksTC.taintRock.get())) && level.getBlockState(t.above()).isAir()
				&& AuraHelper.getFlux(level, t) >= 5.0f && level.random.nextFloat() < spreadRate / 100.0f * 0.33f
				&& isAtTaintSeedEdge(level, t) && level instanceof ServerLevel serverLevel) {
			EntityTaintSeed seed = ThaumcraftEntities.TAINT_SEED.get().create(serverLevel);
			if (seed != null) {
				seed.moveTo(t.getX() + 0.5, t.getY() + 1, t.getZ() + 0.5, (float) level.random.nextInt(360), 0.0f);
				if (seed.canSpawnHere()) {
					AuraHelper.drainFlux(level, t, 5.0f, false);
					serverLevel.addFreshEntity(seed);
				}
			}
		}
	}

	/** Places a plain {@link BlocksTC#taintFibre}, real connections computed explicitly (a bare
	 * {@code defaultBlockState()} never picks up neighbor connections on a direct {@code setBlockAndUpdate} -
	 * see {@link BlockTaintFibre#computeState}'s own doc comment), draining a small real amount of flux. */
	private static void growFibre(Level level, BlockPos from, BlockPos pos) {
		BlockState fibre = BlockTaintFibre.computeState(BlocksTC.taintFibre.get().defaultBlockState(), level, pos);
		if (!TaintSpreadEvent.allows(level, from, pos, fibre)) return;
		level.setBlockAndUpdate(pos, fibre);
		AuraHelper.drainFlux(level, pos, 0.01f, false);
	}

	/** Real port of the original's own {@code BlockUtils#getFaceBlockTouching} for this one real use - which
	 * of {@code pos}'s 6 real neighbors (if any) is a real {@code target} block, returned as the direction
	 * from {@code pos} toward it. */
	private static Direction faceTouching(Level level, BlockPos pos, net.minecraft.world.level.block.Block target) {
		for (Direction dir : Direction.values()) {
			if (level.getBlockState(pos.relative(dir)).is(target)) {
				return dir;
			}
		}
		return null;
	}

	/** Real, approximate modern stand-in for the original's own {@code Material.WOOD}/{@code GOURD}/
	 * {@code CACTUS}/{@code CORAL}/{@code SPONGE} classification (mushroom blocks plus a few thematically
	 * similar organic surfaces) - see {@link #spreadFibres}'s own doc comment on this whole branch. */
	private static boolean isCrustSurface(BlockState bs) {
		return bs.is(CRUST_SURFACE);
	}

	/** What taint eats, by kind - {@code #thaumcraft:taint/crust_surface}, {@code soil_surface}, {@code rock_surface}. */
	private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> CRUST_SURFACE = taintTag("crust_surface");
	private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> SOIL_SURFACE = taintTag("soil_surface");
	private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> ROCK_SURFACE = taintTag("rock_surface");

	private static net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> taintTag(String name) {
		return BlockTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "taint/" + name));
	}

	/** Real, approximate modern stand-in for the original's own {@code Material.SAND}/{@code GROUND}/
	 * {@code GRASS}/{@code CLAY} classification (loose granular/dirt-like ground surfaces). */
	private static boolean isSoilSurface(BlockState bs) {
		return bs.is(SOIL_SURFACE);
	}

	/** Real, approximate modern stand-in for the original's own {@code Material.ROCK} classification - the
	 * true original's own final catch-all case in this same real branch. */
	private static boolean isRockSurface(BlockState bs) {
		return bs.is(ROCK_SURFACE);
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

package thaumcraft.common.lib.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectSource;

/**
 * Replaces the old (1.12.2) {@code EssentiaHandler}. Lets a device (currently just the infusion altar - see
 * {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix}) pull one unit of a given aspect from the
 * nearest matching {@link IAspectSource} within range, rather than needing a direct {@code IEssentiaTransport}
 * connection the way jars/tubes/the condenser do.
 *
 * Simplified for this pass (all real features, just not built yet):
 * <ul>
 * <li>No source-list caching / retry-delay bookkeeping - the original cached each tile's scan result and
 * only rescanned every 10 real-world seconds (per calling tile) to avoid repeating an expensive cube scan.
 * This does a fresh scan every call. Callers that poll this rarely (the infusion altar calls it roughly
 * once per craft cycle, i.e. a few times a second at most) are fine; a high-frequency consumer would want
 * a cache added back.</li>
 * <li>No directional cone restriction - the original supported scanning a narrow cone off one face
 * ({@code direction != null}); only the omnidirectional cube scan ({@code direction == null} in the
 * original) is ported, since that's the only mode the infusion altar itself uses.</li>
 * <li>No {@code TileMirrorEssentia} link-aware exclusion and no essentia-beam FX packet - neither the mirror
 * tile nor the client FX dispatcher for this effect are ported yet.</li>
 * </ul>
 */
public class EssentiaHandler {

	private EssentiaHandler() {
	}

	/**
	 * Finds the nearest {@link IAspectSource} within {@code range} blocks of {@code tile} (cube scan, any
	 * direction) that isn't blocked and actually holds {@code aspect}, and takes one unit from it.
	 *
	 * @return true if a unit of the aspect was found and drained.
	 */
	public static boolean drainEssentia(BlockEntity tile, Aspect aspect, int range) {
		Level level = tile.getLevel();
		if (level == null) return false;

		BlockPos origin = tile.getBlockPos();
		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-range, -range, -range), origin.offset(range, range, range))) {
			if (p.equals(origin)) continue;
			if (level.getBlockEntity(p) instanceof IAspectSource) {
				candidates.add(p.immutable());
			}
		}
		candidates.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));

		for (BlockPos p : candidates) {
			if (level.getBlockEntity(p) instanceof IAspectSource source
					&& !source.isBlocked()
					&& source.takeFromContainer(aspect, 1)) {
				return true;
			}
		}
		return false;
	}
}

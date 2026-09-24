package thaumcraft.common.lib.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.common.lib.network.fx.PacketFXEssentiaSource;

/**
 * Replaces the old (1.12.2) {@code EssentiaHandler}. Lets a device pull ({@link #drainEssentia}) or push
 * ({@link #addEssentia}) one unit of a given aspect against the nearest matching {@link IAspectSource}
 * within range, rather than needing a direct {@code IEssentiaTransport} connection the way jars/tubes/the
 * condenser do. Real consumers: {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix} (drain only),
 * {@link thaumcraft.common.tiles.devices.TileEssentiaInput}/{@code TileEssentiaOutput} (both directions -
 * a real long-range bridge between a tube network and a distant jar/altar/etc., not the physical-inventory
 * item interaction the original's own dispenser/hopper-themed crafting recipe might suggest at a glance;
 * confirmed by reading the original's own real {@code EssentiaHandler} directly - both methods only ever
 * scan for {@code IAspectSource} block entities, never inventory slots).
 *
 * Simplified for this pass (all real features, just not built yet):
 * <ul>
 * <li>No source-list caching / retry-delay bookkeeping - the original cached each tile's scan result and
 * only rescanned every 10 real-world seconds (per calling tile) to avoid repeating an expensive cube scan.
 * This does a fresh scan every call. Callers that poll this rarely (the infusion altar calls it roughly
 * once per craft cycle, the essentia input/output tiles every 5 ticks) are fine; a high-frequency consumer
 * would want a cache added back.</li>
 * <li>No directional cone restriction - the original supported scanning a narrow cone off one face
 * ({@code direction != null}); only the omnidirectional cube scan ({@code direction == null} in the
 * original) is ported. Real, deliberate simplification for the essentia input/output tiles specifically
 * (their own real original behavior scanned a cone off their front face) - same "drop the directional
 * precision, keep the core mechanic" precedent this port has used elsewhere.</li>
 * <li>No essentia-beam FX packet - dropped like everywhere else this session under this port's old blanket
 * "no client FX" rule; restored 2026-09-17, see {@link #broadcastStreamFx}'s own doc comment.</li>
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
		BlockPos target = findNearest(tile, range, source -> source.doesContainerContainAmount(aspect, 1));
		if (target == null) return false;
		boolean taken = ((IAspectSource) tile.getLevel().getBlockEntity(target)).takeFromContainer(aspect, 1);
		if (taken) {
			broadcastStreamFx(tile.getLevel(), target, tile.getBlockPos(), aspect);
		}
		return taken;
	}

	/**
	 * Finds the nearest {@link IAspectSource} within {@code range} blocks of {@code tile} (cube scan, any
	 * direction) that isn't blocked and can accept {@code aspect}, and adds one unit to it. Real mirror of
	 * {@link #drainEssentia} - added for {@code TileEssentiaInput}'s own real "pull from the tube network,
	 * push into a distant source" mechanic, which the original itself built on this exact same class.
	 *
	 * @return true if a real acceptor was found and given one unit.
	 */
	public static boolean addEssentia(BlockEntity tile, Aspect aspect, int range) {
		BlockPos target = findNearest(tile, range, source -> source.doesContainerAccept(aspect));
		if (target == null) return false;
		boolean added = ((IAspectSource) tile.getLevel().getBlockEntity(target)).addToContainer(aspect, 1) == 0;
		if (added) {
			broadcastStreamFx(tile.getLevel(), tile.getBlockPos(), target, aspect);
		}
		return added;
	}

	/** Real client FX broadcast restored 2026-09-17 (was dropped under this port's old blanket "no client FX"
	 * rule) - a real vanilla-network client-bound packet ({@link PacketFXEssentiaSource}, matching the true
	 * original's own real packet-based mechanism exactly, not a shared-static-field trick that would break
	 * real multiplayer) notifying nearby clients that a unit of essentia just moved from {@code from} to
	 * {@code to}, so they can show a real flowing {@link thaumcraft.client.fx.particles.FXEssentiaStream}. Safe
	 * to call directly from this common-side class - {@link PacketFXEssentiaSource} is a plain data record with
	 * no client-only type anywhere in it, the same real dist-safety property {@code PacketFXBlockArc} already
	 * established. */
	private static void broadcastStreamFx(Level level, BlockPos from, BlockPos to, Aspect aspect) {
		if (!(level instanceof ServerLevel serverLevel)) return;
		try {
			PacketDistributor.sendToPlayersNear(serverLevel, null, to.getX(), to.getY(), to.getZ(), 32.0,
					PacketFXEssentiaSource.of(from, to, aspect.getColor(), 0));
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Essentia stream FX packet failed to send (no real client connection?)", e);
		}
	}

	private static BlockPos findNearest(BlockEntity tile, int range, java.util.function.Predicate<IAspectSource> matches) {
		Level level = tile.getLevel();
		if (level == null) return null;

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
			if (level.getBlockEntity(p) instanceof IAspectSource source && !source.isBlocked() && matches.test(source)) {
				return p;
			}
		}
		return null;
	}
}

package thaumcraft.common.lib.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.common.lib.network.fx.PacketFXEssentiaSource;
import thaumcraft.common.tiles.devices.TileMirrorEssentia;

/**
 * Replaces the old (1.12.2) {@code EssentiaHandler}, as the original: lets a device pull ({@link #drainEssentia}) or
 * push ({@link #addEssentia}) one unit of an aspect against the nearest {@link IAspectSource} in range, without a tube
 * connection. Used by the Infusion Matrix (any direction, range 12), the essentia input/output blocks (a box in front
 * of their face, range 16) and the Essentia Mirror (in front of its partner, range 8).
 *
 * <p>Each device's sources are found once and cached, nearest first; when none of them can serve a request the cache
 * is dropped and that device isn't rescanned for 10 seconds. A mirror never counts its own partner as a source, and
 * the mirror's own lookups skip every mirror. Adding fills containers already holding something before empty ones.
 * Every unit moved shows a stream between the two ({@code ext}: how long the stream lingers, in ticks).
 */
public final class EssentiaHandler {

	private static final long DELAY = 10000L;
	private static final Map<GlobalPos, List<BlockPos>> SOURCES = new HashMap<>();
	private static final Map<GlobalPos, Long> SOURCES_DELAY = new HashMap<>();

	@Nullable
	private static BlockEntity lat;
	@Nullable
	private static BlockEntity las;
	@Nullable
	private static Aspect lasp;
	private static int lext;

	private EssentiaHandler() {
	}

	private static GlobalPos key(BlockEntity tile) {
		return GlobalPos.of(tile.getLevel().dimension(), tile.getBlockPos());
	}

	/** The cached sources for {@code tile}, scanning for them if needed; null while none (or still in the delay). */
	@Nullable
	private static List<BlockPos> sources(BlockEntity tile, @Nullable Direction direction, int range) {
		GlobalPos key = key(tile);
		if (!SOURCES.containsKey(key)) getSources(tile, key, direction, range);
		return SOURCES.get(key);
	}

	private static void forget(GlobalPos key) {
		SOURCES.remove(key);
		SOURCES_DELAY.put(key, System.currentTimeMillis() + DELAY);
	}

	public static boolean drainEssentia(BlockEntity tile, Aspect aspect, @Nullable Direction direction, int range, int ext) {
		return drainEssentia(tile, aspect, direction, range, false, ext);
	}

	/** Takes one {@code aspect} from the nearest source that has it. */
	public static boolean drainEssentia(BlockEntity tile, Aspect aspect, @Nullable Direction direction, int range, boolean ignoreMirror, int ext) {
		Level level = tile.getLevel();
		if (level == null) return false;
		List<BlockPos> es = sources(tile, direction, range);
		if (es == null) return false;
		for (BlockPos source : es) {
			if (!(level.getBlockEntity(source) instanceof IAspectSource as)) break;
			if (as.isBlocked() || (ignoreMirror && as instanceof TileMirrorEssentia)) continue;
			if (as.takeFromContainer(aspect, 1)) {
				broadcastStreamFx(level, source, tile.getBlockPos(), aspect, ext);
				return true;
			}
		}
		forget(key(tile));
		return false;
	}

	/** As {@link #drainEssentia}, but only finds the source; {@link #confirmDrain} then takes the unit. */
	public static boolean drainEssentiaWithConfirmation(BlockEntity tile, Aspect aspect, @Nullable Direction direction, int range, boolean ignoreMirror, int ext) {
		Level level = tile.getLevel();
		if (level == null) return false;
		List<BlockPos> es = sources(tile, direction, range);
		if (es == null) return false;
		for (BlockPos source : es) {
			if (!(level.getBlockEntity(source) instanceof IAspectSource as)) break;
			if (as.isBlocked() || (ignoreMirror && as instanceof TileMirrorEssentia)) continue;
			if (as.doesContainerContainAmount(aspect, 1)) {
				las = (BlockEntity) as;
				lasp = aspect;
				lat = tile;
				lext = ext;
				return true;
			}
		}
		forget(key(tile));
		return false;
	}

	public static void confirmDrain() {
		if (las != null && lasp != null && lat != null && ((IAspectSource) las).takeFromContainer(lasp, 1)) {
			broadcastStreamFx(lat.getLevel(), las.getBlockPos(), lat.getBlockPos(), lasp, lext);
		}
		las = null;
		lasp = null;
		lat = null;
	}

	/** Gives one {@code aspect} to the nearest source that takes it - one already holding essentia before an empty one. */
	public static boolean addEssentia(BlockEntity tile, Aspect aspect, @Nullable Direction direction, int range, boolean ignoreMirror, int ext) {
		Level level = tile.getLevel();
		if (level == null) return false;
		List<BlockPos> es = sources(tile, direction, range);
		if (es == null) return false;
		List<BlockPos> empties = new ArrayList<>();
		for (BlockPos source : es) {
			if (!(level.getBlockEntity(source) instanceof IAspectSource as)) break;
			if (as.isBlocked() || (ignoreMirror && as instanceof TileMirrorEssentia)) continue;
			if (as.doesContainerAccept(aspect) && (as.getAspects() == null || as.getAspects().visSize() == 0)) {
				empties.add(source);
			} else if (as.doesContainerAccept(aspect) && as.addToContainer(aspect, 1) <= 0) {
				broadcastStreamFx(level, tile.getBlockPos(), source, aspect, ext);
				return true;
			}
		}
		for (BlockPos source : empties) {
			if (!(level.getBlockEntity(source) instanceof IAspectSource as)) break;
			if (as.doesContainerAccept(aspect) && as.addToContainer(aspect, 1) <= 0) {
				broadcastStreamFx(level, tile.getBlockPos(), source, aspect, ext);
				return true;
			}
		}
		forget(key(tile));
		return false;
	}

	/** Whether any source in range holds {@code aspect}. */
	public static boolean findEssentia(BlockEntity tile, Aspect aspect, @Nullable Direction direction, int range, boolean ignoreMirror) {
		Level level = tile.getLevel();
		if (level == null) return false;
		List<BlockPos> es = sources(tile, direction, range);
		if (es == null) return false;
		for (BlockPos source : es) {
			if (!(level.getBlockEntity(source) instanceof IAspectSource as)) break;
			if (as.isBlocked() || (ignoreMirror && as instanceof TileMirrorEssentia)) continue;
			if (as.doesContainerContainAmount(aspect, 1)) return true;
		}
		return false;
	}

	/** Whether any source in range would take {@code aspect}. (As the original, the very first call - before the
	 * sources are cached - answers whether one holds it instead.) */
	public static boolean canAcceptEssentia(BlockEntity tile, Aspect aspect, @Nullable Direction direction, int range, boolean ignoreMirror) {
		Level level = tile.getLevel();
		if (level == null) return false;
		GlobalPos key = key(tile);
		if (!SOURCES.containsKey(key)) {
			getSources(tile, key, direction, range);
			return SOURCES.containsKey(key) && findEssentia(tile, aspect, direction, range, ignoreMirror);
		}
		for (BlockPos source : SOURCES.get(key)) {
			if (!(level.getBlockEntity(source) instanceof IAspectSource as)) break;
			if (ignoreMirror && as instanceof TileMirrorEssentia) continue;
			if (!as.isBlocked() && as.doesContainerAccept(aspect)) return true;
		}
		return false;
	}

	/** Forgets {@code tile}'s cached sources, so the next request scans again. */
	public static void refreshSources(BlockEntity tile) {
		SOURCES.remove(key(tile));
	}

	/** A device was removed: forget its sources and its retry delay too, so whatever is placed there next starts
	 * fresh (the original kept them, so a new device could inherit a stale list or wait out a stale delay). */
	public static void forget(BlockEntity tile) {
		if (tile.getLevel() == null) return;
		SOURCES.remove(key(tile));
		SOURCES_DELAY.remove(key(tile));
	}

	/** The server stopped: the caches belong to its levels. */
	public static void clear() {
		SOURCES.clear();
		SOURCES_DELAY.clear();
		las = null;
		lasp = null;
		lat = null;
	}

	/** The original's getSources: a cube of {@code range} around {@code tile} with no direction, else the
	 * {@code range}-deep box in front of {@code direction}'s face; nearest first. */
	private static void getSources(BlockEntity tile, GlobalPos key, @Nullable Direction direction, int range) {
		Long delay = SOURCES_DELAY.get(key);
		if (delay != null) {
			if (delay > System.currentTimeMillis()) return;
			SOURCES_DELAY.remove(key);
		}
		Level level = tile.getLevel();
		BlockPos origin = tile.getBlockPos();
		int start = 0;
		if (direction == null) {
			start = -range;
			direction = Direction.UP;
		}
		List<BlockPos> found = new ArrayList<>();
		for (int aa = -range; aa <= range; aa++) {
			for (int bb = -range; bb <= range; bb++) {
				for (int cc = start; cc < range; cc++) {
					if (aa == 0 && bb == 0 && cc == 0) continue;
					BlockPos p;
					if (direction.getStepY() != 0) {
						p = origin.offset(aa, cc * direction.getStepY(), bb);
					} else if (direction.getStepX() == 0) {
						p = origin.offset(aa, bb, cc * direction.getStepZ());
					} else {
						p = origin.offset(cc * direction.getStepX(), aa, bb);
					}
					if (!(level.getBlockEntity(p) instanceof IAspectSource)) continue;
					// a mirror never draws through the mirror linked to it
					if (tile instanceof TileMirrorEssentia && level.getBlockEntity(p) instanceof TileMirrorEssentia other
							&& other.getLink() != null && other.getLink().pos().equals(origin) && other.getLink().dimension().equals(level.dimension())) {
						continue;
					}
					found.add(p);
				}
			}
		}
		if (found.isEmpty()) {
			SOURCES_DELAY.put(key, System.currentTimeMillis() + DELAY);
			return;
		}
		found.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
		SOURCES.put(key, found);
	}

	/** A stream of essentia from {@code from} to {@code to} on nearby clients (PacketFXEssentiaSource). */
	private static void broadcastStreamFx(Level level, BlockPos from, BlockPos to, Aspect aspect, int ext) {
		if (!(level instanceof ServerLevel serverLevel)) return;
		try {
			PacketDistributor.sendToPlayersNear(serverLevel, null, to.getX(), to.getY(), to.getZ(), 32.0,
					PacketFXEssentiaSource.of(from, to, aspect.getColor(), ext));
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Essentia stream FX packet failed to send (no real client connection?)", e);
		}
	}
}

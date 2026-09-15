package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileTube} - a single-unit essentia relay, letting two
 * {@code IEssentiaTransport} devices exchange essentia across more than one block by chaining tubes between
 * them (see {@link thaumcraft.common.tiles.essentia.TileJarFillable}'s own doc comment - direct adjacency
 * was proven first without any tube at all; this is what actually extends that reach).
 *
 * Every other tick, recomputes its own suction (the strongest demand pulling from any of its 6 neighbors);
 * every 5th tick, if it has real suction and is empty, pulls 1 unit of essentia from whichever neighbor has
 * lower suction and can output - the same core algorithm the original used
 * ({@code calculateSuction}/{@code equalizeWithNeighbours}), holding at most 1 unit at a time (a relay, not
 * storage).
 *
 * Simplified vs the original:
 * <ul>
 * <li>No per-face open/close toggling - the original configured this by right-clicking one of 6 small
 * per-face "nub" hitboxes with a wand, built on CodeChicken's raytracing library ({@code IndexedCuboid6}/
 * {@code RayTracer}) for that precise sub-block click detection. All 6 sides are always electrically open
 * here instead - the same real "drop the precise multi-hitbox click UI, keep the mechanic" simplification
 * {@link thaumcraft.common.blocks.devices.BlockLevitator}/{@link thaumcraft.common.blocks.devices.BlockRedstoneRelay}
 * already established for their own single-value cycles (see their own doc comments) - there's just no
 * single value left to cycle here once every side is always open, so this one has no click interaction at
 * all rather than a whole-block-click substitute.</li>
 * <li>No venting warning FX/sound when two neighbors want conflicting aspects (client FX + a flavor cue,
 * dropped like everywhere else this session).</li>
 * </ul>
 *
 * {@link TileTubeFilter} (aspect-locks a tube), {@link TileTubeRestrict} (halves its effective suction), and
 * {@link TileTubeOneway} (accepts suction from - and only feeds essentia toward - a single facing) all
 * extend this class, hooking into {@link #calculateSuction}/{@link #equalizeWithNeighbours} via a handful of
 * protected extension points rather than overriding those methods wholesale - a plain {@code TileTube}
 * exercises the exact same loop either way, just with every hook a no-op. {@code TileTubeOneway}'s own
 * "priority facing" configuration is the one case here that <i>does</i> get a real substitute click
 * interaction (see its own doc comment) - the same {@code useWithoutItem}-cycles-a-value trick as
 * Levitator/Redstone Relay, since it only has the one field to configure, not 6 independent per-face
 * toggles.
 *
 * The connecting-pipe blockstate ({@code data/thaumcraft/blockstates/tube.json}) already used vanilla's own
 * {@code multipart}/{@code when}/{@code apply} format unchanged since 1.12 - migrated with only a model-path
 * prefix fix, no format rewrite needed. {@code BlockTube} recomputes the connection booleans
 * ({@code BlockStateProperties.NORTH}/{@code SOUTH}/etc. - the exact same properties vanilla's own
 * {@code IronBarsBlock}/pane family use) from real neighbor state, purely for rendering; they don't gate
 * function, since there's no per-face toggle left to gate.
 */
public class TileTube extends BlockEntity implements IEssentiaTransport {

	private int count;
	protected Aspect essentiaType;
	protected int essentiaAmount;
	protected Aspect suctionType;
	protected int suction;

	public TileTube(BlockPos pos, BlockState state) {
		this(ThaumcraftBlockEntities.TUBE.get(), pos, state);
	}

	/** For {@link TileTubeFilter}/{@link TileTubeRestrict}, which each need their own real
	 * {@code BlockEntityType} (not this class's {@code TUBE} type) so {@code BlockEntityTicker} dispatch and
	 * {@code GameTestHelper#getBlockEntity} casts both resolve correctly. */
	protected TileTube(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileTube tile) {
		if (level.isClientSide) return;
		tile.count++;
		if (tile.count % 2 == 0) {
			tile.calculateSuction(level, pos);
			if (tile.essentiaType != null && tile.essentiaAmount <= 0) {
				tile.essentiaType = null;
			}
		}
		if (tile.count % 5 == 0 && tile.suction > 0) {
			tile.equalizeWithNeighbours(level, pos);
		}
	}

	/** Recomputes {@link #suction}/{@link #suctionType} from the strongest of the 6 neighbors' own demand.
	 * {@link TileTubeFilter}/{@link TileTubeRestrict} hook in via {@link #acceptsNeighborSuctionType},
	 * {@link #adjustSuction}, and {@link #forcedSuctionType} rather than overriding this loop itself. */
	protected void calculateSuction(Level level, BlockPos pos) {
		suction = 0;
		suctionType = null;
		for (Direction dir : Direction.values()) {
			if (!isRelayDirection(dir)) continue;
			if (!(level.getBlockEntity(pos.relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
			if (!neighbor.isConnectable(dir.getOpposite())) continue;
			Aspect neighborType = neighbor.getSuctionType(dir.getOpposite());
			if (!acceptsNeighborSuctionType(neighborType)) continue;
			int suck = adjustSuction(neighbor.getSuctionAmount(dir.getOpposite()));
			if (suck > 0 && suck > suction + 1) {
				suction = suck;
				suctionType = neighborType != null ? neighborType : forcedSuctionType();
			}
		}
	}

	protected void equalizeWithNeighbours(Level level, BlockPos pos) {
		if (essentiaAmount > 0) return;
		for (Direction dir : Direction.values()) {
			if (!isPullDirection(dir)) continue;
			if (!(level.getBlockEntity(pos.relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
			if (!neighbor.isConnectable(dir.getOpposite()) || !neighbor.canOutputTo(dir.getOpposite())) continue;

			Aspect want = suctionType;
			Aspect theirType = neighbor.getEssentiaType(dir.getOpposite());
			if (want == null) want = theirType;
			if (want == null) want = forcedSuctionType();
			if (want == null) continue;
			if (theirType != null && want != theirType) continue;
			if (!acceptsNeighborSuctionType(theirType)) continue;
			if (suction <= neighbor.getSuctionAmount(dir.getOpposite())) continue;
			if (suction < neighbor.getMinimumSuction()) continue;

			int taken = neighbor.takeEssentia(want, 1, dir.getOpposite());
			if (taken > 0 && addEssentia(want, taken, dir) > 0) {
				return;
			}
		}
	}

	/** {@link TileTubeFilter} rejects any neighbor demanding a different aspect than its own filter. Plain
	 * tubes (and Restrict) accept anything. */
	protected boolean acceptsNeighborSuctionType(Aspect neighborType) {
		return true;
	}

	/** {@link TileTubeRestrict} halves the suction value it reports/uses, throttling its effective flow
	 * rate and priority against other demand on the network. */
	protected int adjustSuction(int suck) {
		return suck;
	}

	/** {@link TileTubeFilter} forces this tube's own declared demand to its filter aspect even when no
	 * neighbor currently states a type of its own. */
	protected Aspect forcedSuctionType() {
		return null;
	}

	/** {@link TileTubeOneway} restricts which neighbor's demand gets relayed as this tube's own suction -
	 * see its own doc comment for the real semantics. Every other variant listens on all 6. */
	protected boolean isRelayDirection(Direction dir) {
		return true;
	}

	/** {@link TileTubeOneway} restricts which neighbor essentia can be pulled from - see its own doc comment.
	 * Every other variant pulls from all 6. */
	protected boolean isPullDirection(Direction dir) {
		return true;
	}

	@Override
	public boolean isConnectable(Direction face) {
		return true;
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return true;
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return true;
	}

	@Override
	public void setSuction(Aspect aspect, int amount) {
		suctionType = aspect;
		suction = amount;
	}

	@Override
	public Aspect getSuctionType(Direction face) {
		return suctionType;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		return suction;
	}

	@Override
	public Aspect getEssentiaType(Direction face) {
		return essentiaType;
	}

	@Override
	public int getEssentiaAmount(Direction face) {
		return essentiaAmount;
	}

	@Override
	public int takeEssentia(Aspect aspect, int amount, Direction face) {
		if (essentiaType == aspect && essentiaAmount > 0 && amount > 0) {
			essentiaAmount--;
			if (essentiaAmount <= 0) {
				essentiaType = null;
			}
			setChanged();
			return 1;
		}
		return 0;
	}

	@Override
	public int addEssentia(Aspect aspect, int amount, Direction face) {
		if (essentiaAmount == 0 && amount > 0) {
			essentiaType = aspect;
			essentiaAmount = 1;
			setChanged();
			return 1;
		}
		return 0;
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (essentiaType != null) {
			tag.putString("EssentiaType", essentiaType.getTag());
		}
		tag.putInt("EssentiaAmount", essentiaAmount);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		essentiaType = tag.contains("EssentiaType") ? Aspect.getAspect(tag.getString("EssentiaType")) : null;
		essentiaAmount = tag.getInt("EssentiaAmount");
	}
}

package thaumcraft.common.tiles.essentia;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.casters.IInteractWithCaster;
import thaumcraft.common.lib.utils.CuboidTargeting;
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
 * <p>Real per-face open/close toggling and the real central "cycle facing" button are both real again, via
 * {@link IInteractWithCaster} + {@link CuboidTargeting} - the modern, no-third-party-library replacement for
 * the original's CodeChicken raytracer (see that class's own doc comment for the full story of why the
 * library itself doesn't need reviving). An earlier pass here had dropped both entirely on the reasoning
 * that the click-precision system they needed was gone; it wasn't, vanilla just grew the capability under a
 * different name. {@link #onCasterRightClick} does its own fresh raytrace against 7 small real regions -
 * one nub per real currently-connected neighbor (only ever registered while that neighbor is real, matching
 * the true original's own {@code canConnectSide}-gated registration) plus one always-present central core -
 * re-derived directly from the true original's own {@code addTraceableCuboids} numbers. Clicking a face nub
 * toggles {@link #openSides} for that direction (and mirrors the new state onto the neighbor's own opposite
 * side, if it's also a real {@code TileTube} - matching the original exactly); clicking the core cycles
 * {@link #facing}, consulted only by {@link TileTubeOneway}. Requires an actual Caster in hand, matching the
 * true original's own real requirement (unlike Levitator/Redstone Relay, which never needed one).
 *
 * <p>Real, deliberate simplification kept from the earlier pass: no venting warning FX/sound when two
 * neighbors want conflicting aspects (client FX + a flavor cue, dropped like everywhere else this session).
 *
 * {@link TileTubeFilter} (aspect-locks a tube), {@link TileTubeRestrict} (halves its effective suction), and
 * {@link TileTubeOneway} (accepts suction from - and only feeds essentia toward - a single facing) all
 * extend this class, hooking into {@link #calculateSuction}/{@link #equalizeWithNeighbours} via a handful of
 * protected extension points rather than overriding those methods wholesale - a plain {@code TileTube}
 * exercises the exact same loop either way, just with every hook a no-op.
 *
 * The connecting-pipe blockstate ({@code data/thaumcraft/blockstates/tube.json}) already used vanilla's own
 * {@code multipart}/{@code when}/{@code apply} format unchanged since 1.12 - migrated with only a model-path
 * prefix fix, no format rewrite needed. {@code BlockTube} recomputes the connection booleans
 * ({@code BlockStateProperties.NORTH}/{@code SOUTH}/etc. - the exact same properties vanilla's own
 * {@code IronBarsBlock}/pane family use) from real neighbor state, purely for rendering; they're independent
 * of {@link #openSides} (a closed side still visually connects - only whether essentia actually flows
 * through it changes), matching the true original's own real distinction between "physically joined" and
 * "electrically open."
 */
public class TileTube extends BlockEntity implements IEssentiaTransport, IInteractWithCaster {

	/** The real inset nub/core geometry, re-derived verbatim from the true original's own
	 * {@code TileTube#addTraceableCuboids} numbers (0.375-0.625 inset on the two transverse axes, reaching
	 * from the block face inward on the third). */
	private static AABB nubBox(BlockPos pos, Direction dir) {
		AABB local = switch (dir) {
			case DOWN -> new AABB(0.375, 0.0, 0.375, 0.625, 0.375, 0.625);
			case UP -> new AABB(0.375, 0.625, 0.375, 0.625, 1.0, 0.625);
			case NORTH -> new AABB(0.375, 0.375, 0.0, 0.625, 0.625, 0.375);
			case SOUTH -> new AABB(0.375, 0.375, 0.625, 0.625, 0.625, 1.0);
			case WEST -> new AABB(0.0, 0.375, 0.375, 0.375, 0.625, 0.625);
			case EAST -> new AABB(0.625, 0.375, 0.375, 1.0, 0.625, 0.625);
		};
		return local.move(pos);
	}

	private static final AABB CORE_LOCAL = new AABB(0.375, 0.375, 0.375, 0.625, 0.625, 0.625);

	private int count;
	protected Aspect essentiaType;
	protected int essentiaAmount;
	protected Aspect suctionType;
	protected int suction;

	/** Per-direction electrical open/close state, real again - see this class's own doc comment. Default
	 * all-open, matching the true original's own default and this port's previously-shipped behavior. */
	private final boolean[] openSides = { true, true, true, true, true, true };

	/** The real "priority facing" the central core button cycles - only ever consulted by
	 * {@link TileTubeOneway}, but real on every variant (matching the true original's own class layout). */
	private Direction facing = Direction.NORTH;

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
	 * {@link #adjustSuction}, and {@link #forcedSuctionType} rather than overriding this loop itself.
	 *
	 * <p>Real per-hop decay, restored 2026-09-15 after being found missing by direct comparison against the
	 * true original: the threshold check here compares against a neighbor's raw reported demand, but what
	 * actually gets STORED (via {@link #adjustSuction}) is one less than that (the true original's own real
	 * {@code setSuction(st, restrict ? suck/2 : suck-1)} - this port had been storing the raw, un-decayed
	 * {@code suck} value verbatim, a real regression from the true original, not a deliberate simplification.
	 * Without this decay, 2 real tubes chained straight in a row can permanently deadlock: both adopt the
	 * exact same suction value from the same downstream demand, and {@link #equalizeWithNeighbours}'s own
	 * pull condition requires strictly-greater suction to pull - a tie never resolves either way. With the
	 * real per-hop decay restored, suction strictly decreases moving away from the real demand source, so
	 * ties between neighbors can't happen at all - exactly the true original's own real behavior. */
	protected void calculateSuction(Level level, BlockPos pos) {
		suction = 0;
		suctionType = null;
		for (Direction dir : Direction.values()) {
			if (!openSides[dir.ordinal()]) continue;
			if (!isRelayDirection(dir)) continue;
			if (!(level.getBlockEntity(pos.relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
			if (!neighbor.isConnectable(dir.getOpposite())) continue;
			Aspect neighborType = neighbor.getSuctionType(dir.getOpposite());
			if (!acceptsNeighborSuctionType(neighborType)) continue;
			int suck = neighbor.getSuctionAmount(dir.getOpposite());
			if (suck > 0 && suck > suction + 1) {
				suction = adjustSuction(suck);
				suctionType = neighborType != null ? neighborType : forcedSuctionType();
			}
		}
	}

	protected void equalizeWithNeighbours(Level level, BlockPos pos) {
		if (essentiaAmount > 0) return;
		for (Direction dir : Direction.values()) {
			if (!openSides[dir.ordinal()]) continue;
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

	/** The real value this tube stores as its own effective suction once a neighbor's raw demand has already
	 * beaten the current running threshold in {@link #calculateSuction} - NOT applied to the threshold
	 * comparison itself, matching the true original's own real {@code setSuction(st, restrict ? suck/2 :
	 * suck-1)} split exactly. Plain tubes decay by 1 per hop (see {@link #calculateSuction}'s own doc comment
	 * for why this matters); {@link TileTubeRestrict} instead halves it, throttling its effective flow rate
	 * and priority against other demand on the network (a stronger, real, deliberate penalty on top of the
	 * same real per-hop decay every tube needs). */
	protected int adjustSuction(int suck) {
		return suck - 1;
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

	public Direction getFacing() {
		return facing;
	}

	public boolean isSideOpen(Direction dir) {
		return openSides[dir.ordinal()];
	}

	/** Real public mirroring hook - lets a neighboring {@link TileTubeBuffer} (a separate class, not a
	 * {@code TileTube} subclass) push its own toggled state onto this tube's matching side, the same way
	 * {@link #onCasterRightClick} already mirrors between two plain tubes directly. */
	public void setSideOpen(Direction dir, boolean open) {
		openSides[dir.ordinal()] = open;
		setChanged();
	}

	/**
	 * The real click precision restored - see this class's own doc comment. Does its own fresh raytrace
	 * against the real current nub/core geometry (only registering a nub for a direction with a real,
	 * currently-connected {@link IEssentiaTransport} neighbor, matching the true original's own
	 * {@code canConnectSide}-gated registration exactly), and either toggles the matched face's
	 * {@link #openSides} entry (mirroring it onto a real neighboring {@code TileTube}'s own opposite side,
	 * same as the original) or, for the central core, cycles {@link #facing} to the next real connected
	 * neighbor.
	 */
	/** Distinguishes "hit the central core" from "hit nothing" in {@link #onCasterRightClick} - a face nub
	 * hit is labeled with its real {@link Direction} instead. */
	private static final Object CORE = new Object();

	@Override
	public boolean onCasterRightClick(Level world, ItemStack casterStack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
		List<CuboidTargeting.Candidate<Object>> candidates = new ArrayList<>();
		for (Direction dir : Direction.values()) {
			if (world.getBlockEntity(worldPosition.relative(dir)) instanceof IEssentiaTransport) {
				candidates.add(new CuboidTargeting.Candidate<>(dir, nubBox(worldPosition, dir)));
			}
		}
		candidates.add(new CuboidTargeting.Candidate<>(CORE, CORE_LOCAL.move(worldPosition)));

		Object hit = CuboidTargeting.pick(player, candidates);
		if (hit == null) return false;

		world.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, 0.9f + world.random.nextFloat() * 0.2f);
		if (hit instanceof Direction dir) {
			openSides[dir.ordinal()] = !openSides[dir.ordinal()];
			setChanged();
			if (world.getBlockEntity(worldPosition.relative(dir)) instanceof TileTube neighbor) {
				neighbor.setSideOpen(dir.getOpposite(), openSides[dir.ordinal()]);
			}
		} else {
			cycleFacing(world, worldPosition);
		}
		return true;
	}

	/** Cycles {@link #facing} to the next direction (wrapping) with a real {@link IEssentiaTransport}
	 * neighbor, matching the true original's own skip-forward-until-connectable loop. Falls back to a plain
	 * 1-step advance if no neighbor is currently connected at all, so the value still visibly changes rather
	 * than silently doing nothing. */
	public void cycleFacing(Level level, BlockPos pos) {
		Direction[] values = Direction.values();
		int start = facing.ordinal();
		for (int i = 1; i <= values.length; i++) {
			Direction candidate = values[(start + i) % values.length];
			if (level.getBlockEntity(pos.relative(candidate)) instanceof IEssentiaTransport) {
				facing = candidate;
				setChanged();
				return;
			}
		}
		facing = values[(start + 1) % values.length];
		setChanged();
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
		int openMask = 0;
		for (int i = 0; i < openSides.length; i++) {
			if (openSides[i]) openMask |= 1 << i;
		}
		tag.putInt("OpenSides", openMask);
		tag.putInt("Facing", facing.ordinal());
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		essentiaType = tag.contains("EssentiaType") ? Aspect.getAspect(tag.getString("EssentiaType")) : null;
		essentiaAmount = tag.getInt("EssentiaAmount");
		int openMask = tag.contains("OpenSides") ? tag.getInt("OpenSides") : 0b111111;
		for (int i = 0; i < openSides.length; i++) {
			openSides[i] = (openMask & (1 << i)) != 0;
		}
		if (tag.contains("Facing")) {
			facing = Direction.values()[tag.getInt("Facing")];
		}
	}

	/** Real block-entity sync wiring, added 2026-09-16 alongside {@code TileTubeValve}'s own real spinning-
	 * ring animation restoration - this whole real tube family had none before (neither {@link #openSides}
	 * nor {@link #facing} were ever actually visible to the client, a real, previously-unnoticed gap since
	 * every other consumer of them was server-only). Same real pattern {@code TileCrucible}/
	 * {@code TileCentrifuge}/{@code TilePatternCrafter} already established. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
	}
}

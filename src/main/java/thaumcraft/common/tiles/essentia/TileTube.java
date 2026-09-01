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
 * <li>No per-face open/close toggling or "priority output facing" - both were configured by right-clicking
 * individual faces with a wand, built on CodeChicken's raytracing library ({@code IndexedCuboid6}/
 * {@code RayTracer}) for per-face click detection, the same sub-hit-raytrace wall that blocked the Levitator
 * and Redstone Relay earlier this session. All 6 sides are always electrically open here - simpler, and the
 * only way this device can be meaningfully functional without that click-UI at all.</li>
 * <li>No venting warning FX/sound when two neighbors want conflicting aspects (client FX + a flavor cue,
 * dropped like everywhere else this session).</li>
 * </ul>
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
	private Aspect essentiaType;
	private int essentiaAmount;
	private Aspect suctionType;
	private int suction;

	public TileTube(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.TUBE.get(), pos, state);
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

	private void calculateSuction(Level level, BlockPos pos) {
		suction = 0;
		suctionType = null;
		for (Direction dir : Direction.values()) {
			if (!(level.getBlockEntity(pos.relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
			if (!neighbor.isConnectable(dir.getOpposite())) continue;
			int suck = neighbor.getSuctionAmount(dir.getOpposite());
			if (suck > 0 && suck > suction + 1) {
				suction = suck;
				suctionType = neighbor.getSuctionType(dir.getOpposite());
			}
		}
	}

	private void equalizeWithNeighbours(Level level, BlockPos pos) {
		if (essentiaAmount > 0) return;
		for (Direction dir : Direction.values()) {
			if (!(level.getBlockEntity(pos.relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
			if (!neighbor.isConnectable(dir.getOpposite()) || !neighbor.canOutputTo(dir.getOpposite())) continue;

			Aspect want = suctionType;
			Aspect theirType = neighbor.getEssentiaType(dir.getOpposite());
			if (want == null) want = theirType;
			if (want == null) continue;
			if (theirType != null && want != theirType) continue;
			if (suction <= neighbor.getSuctionAmount(dir.getOpposite())) continue;
			if (suction < neighbor.getMinimumSuction()) continue;

			int taken = neighbor.takeEssentia(want, 1, dir.getOpposite());
			if (taken > 0 && addEssentia(want, taken, dir) > 0) {
				return;
			}
		}
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

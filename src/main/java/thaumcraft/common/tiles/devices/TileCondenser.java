package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.devices.BlockCondenserLattice;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileCondenser} - converts real world flux into pure Flux essentia (pulled
 * out via any {@link IEssentiaTransport} neighbor, e.g. a Warded Jar or Tube already built this session),
 * paced by how large a connected {@link BlockCondenserLattice} structure sits above it. A bigger, unclogged
 * lattice both speeds up conversion (shorter tick interval) and lowers its essentia cost per flux point -
 * the same {@code latticeCount}-driven formula the original used.
 *
 * The lattice scan ({@code triggerCheck}/{@code performCheck}) is a depth-limited BFS up from the condenser
 * through connected lattice blocks (max search distance ~8.6 blocks, matching the original's {@code 74.0}
 * squared-distance cap); a "dirty" lattice block still counts toward connectivity but marks the whole branch
 * clogged (no bonus from it), and each conversion has a small chance to dirty a random unclogged lattice
 * block in the structure - the original's own maintenance loop ({@code ItemsTC.filter} clears a dirty block
 * back to clean on right-click, ported alongside on {@link BlockCondenserLattice}).
 *
 * Simplified vs the original: no client-side spark particle effects (FXDispatcher) - visual-only, dropped
 * like everywhere else this session.
 */
public class TileCondenser extends BlockEntity implements IEssentiaTransport {

	private static final int MAX = 100;

	private int essentia;
	private int flux;
	private int count;
	private float latticeCount = -1.0f;
	private int interval;
	private int cost;

	private final java.util.List<Long> history = new java.util.ArrayList<>();
	private final java.util.List<Long> blockList = new java.util.ArrayList<>();
	private final java.util.List<Long> uncloggedList = new java.util.ArrayList<>();

	public TileCondenser(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.CONDENSER.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileCondenser tile) {
		if (level.isClientSide) return;
		if (tile.latticeCount < 0.0f) {
			tile.triggerCheck(level, pos);
		}
		tile.count++;
		if (!state.getValue(BlockStateProperties.ENABLED) || tile.latticeCount <= 0.0f) return;

		if (tile.count % 5 == 0 && tile.essentia < MAX) {
			tile.fill(level, pos);
		}
		if (tile.interval > 0 && tile.essentia >= tile.cost && tile.flux < MAX && tile.count % tile.interval == 0
				&& AuraHelper.getFlux(level, pos) >= 1.0f) {
			AuraHelper.drainFlux(level, pos, 1.0f, false);
			tile.essentia -= tile.cost;
			tile.flux++;
			if (level.random.nextInt(50) == 0) {
				tile.makeLatticeDirty(level);
			}
			tile.setChanged();
		}
	}

	private void fill(Level level, BlockPos pos) {
		for (Direction face : Direction.Plane.HORIZONTAL) {
			if (!(level.getBlockEntity(pos.relative(face)) instanceof IEssentiaTransport neighbor)) continue;
			if (!neighbor.isConnectable(face.getOpposite()) || !neighbor.canOutputTo(face.getOpposite())) continue;
			if (neighbor.getEssentiaAmount(face.getOpposite()) <= 0) continue;
			if (neighbor.getSuctionAmount(face.getOpposite()) >= getSuctionAmount(face)) continue;
			if (getSuctionAmount(face) < neighbor.getMinimumSuction()) continue;

			Aspect type = neighbor.getEssentiaType(face.getOpposite());
			if (type == null) continue;
			if (type != Aspect.FLUX) {
				essentia += neighbor.takeEssentia(type, 1, face.getOpposite());
			} else {
				makeLatticeDirty(level);
			}
			setChanged();
			if (essentia >= MAX) return;
		}
	}

	private void makeLatticeDirty(Level level) {
		if (uncloggedList.isEmpty()) return;
		int index = level.random.nextInt(uncloggedList.size());
		if (index == 0 && uncloggedList.size() > 1) {
			index = level.random.nextInt(uncloggedList.size());
		}
		BlockPos p = BlockPos.of(uncloggedList.get(index));
		BlockState bs = level.getBlockState(p);
		if (bs.getBlock() == BlocksTC.condenserLattice.get()) {
			level.setBlock(p, BlocksTC.condenserLatticeDirty.get().defaultBlockState(), 3);
			((BlockCondenserLattice) level.getBlockState(p).getBlock()).triggerUpdate(level, p);
		}
	}

	private void triggerCheck(Level level, BlockPos pos) {
		history.clear();
		blockList.clear();
		uncloggedList.clear();
		latticeCount = 0.0f;
		interval = 0;
		performCheck(level, pos, true, false);
		history.clear();
		if (latticeCount <= 0.0f) {
			latticeCount = 0.0f;
		} else {
			if (latticeCount > 40.0f) latticeCount = 40.0f;
			interval = Math.round(600.0f - latticeCount * 15.0f);
			if (interval < 5) interval = 5;
			cost = (int) (4.0 + Math.sqrt(blockList.size()));
		}
	}

	private void performCheck(Level level, BlockPos pos, boolean skip, boolean clogged) {
		if (latticeCount < 0.0f) return;
		history.add(pos.asLong());
		boolean found = false;
		int sides = 0;
		for (Direction face : Direction.values()) {
			if (skip && face != Direction.UP) continue;
			BlockPos p2 = pos.relative(face);
			BlockState bs = level.getBlockState(p2);
			boolean lattice = bs.getBlock() == BlocksTC.condenserLattice.get();
			boolean latticeDirty = bs.getBlock() == BlocksTC.condenserLatticeDirty.get();
			if (skip && latticeDirty) clogged = true;
			if (lattice || latticeDirty) sides++;
			if (history.contains(p2.asLong())) continue;

			if (face == Direction.DOWN && bs.getBlock() == BlocksTC.condenser.get()) {
				latticeCount = -99.0f;
				return;
			}
			if (pos.getY() >= p2.getY()) continue;
			if (pos.distSqr(p2) > 74.0) continue;
			if (!lattice && !latticeDirty) continue;

			blockList.add(p2.asLong());
			if (lattice) uncloggedList.add(p2.asLong());
			found = true;
			performCheck(level, p2, false, clogged || latticeDirty);
			if (skip) break;
		}
		if (found && !clogged) {
			latticeCount += 1.0f - 0.15f * sides;
		}
	}

	@Override
	public boolean isConnectable(Direction face) {
		return face != Direction.UP;
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return face != Direction.UP && face != Direction.DOWN;
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return face == Direction.DOWN;
	}

	@Override
	public void setSuction(Aspect aspect, int amount) {
	}

	@Override
	public Aspect getSuctionType(Direction face) {
		return null;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		return (face == Direction.DOWN || essentia >= MAX) ? 0 : 128;
	}

	@Override
	public int takeEssentia(Aspect aspect, int amount, Direction face) {
		int amt = (canOutputTo(face) && (aspect == null || aspect == Aspect.FLUX)) ? Math.min(amount, flux) : 0;
		if (amt > 0) {
			flux -= amt;
			setChanged();
		}
		return amt;
	}

	@Override
	public int addEssentia(Aspect aspect, int amount, Direction face) {
		int amt = canInputFrom(face) ? Math.min(amount, MAX - essentia) : 0;
		if (amt > 0) setChanged();
		return amt;
	}

	@Override
	public Aspect getEssentiaType(Direction face) {
		return Aspect.FLUX;
	}

	@Override
	public int getEssentiaAmount(Direction face) {
		return flux;
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	public int getEssentia() {
		return essentia;
	}

	public int getFluxStored() {
		return flux;
	}

	public float getLatticeCount() {
		return latticeCount;
	}

	/** Forces a rescan on the next tick - called by {@link BlockCondenserLattice} when a lattice block loses
	 * its live path back to this condenser. */
	public void markLatticeDirty() {
		latticeCount = -1.0f;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putShort("essentia", (short) essentia);
		tag.putShort("flux", (short) flux);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		essentia = tag.getShort("essentia");
		flux = tag.getShort("flux");
	}
}

package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileTubeOneway} - a {@link TileTube} that only accepts suction from, and
 * only feeds essentia toward, one designated {@link #facing} neighbor - the true original's own in-game
 * research text describes it exactly: "Directional essentia tubes only accept suction from one direction."
 *
 * <p>The real original's own {@code calculateSuction}/{@code equalizeWithNeighbours} overrides reduce to two
 * distinct restrictions on the same one field, not a symmetric "only talk to this one neighbor" rule:
 * {@link #isRelayDirection} only relays suction that originates from the {@link #facing} neighbor (so this
 * tube's own reported demand only ever propagates backward toward whichever consumer it's pointed at), while
 * {@link #isPullDirection} <i>excludes</i> that same neighbor as a source (so essentia never flows back in
 * from the direction it's meant to flow out toward - the real one-way/backflow-prevention half of the
 * mechanic). Re-derived directly from the original's own {@code facing == loc.getOpposite()}/
 * {@code facing != loc.getOpposite()} conditions plus its own research text, not guessed.
 *
 * <p>Real, deliberate simplification: the original set {@link #facing} by right-clicking a small central
 * "core" hitbox with a wand, built on the same CodeChicken sub-block raytracing already dropped for
 * {@link TileTube}'s own per-face toggles (see its own doc comment). Unlike those 6 independent toggles,
 * this device only has the one field to configure, so it gets the same real substitute
 * {@link thaumcraft.common.blocks.devices.BlockLevitator}/{@link thaumcraft.common.blocks.devices.BlockRedstoneRelay}
 * already established: a plain whole-block right-click ({@link thaumcraft.common.blocks.essentia.BlockTubeOneway#useWithoutItem})
 * cycles {@link #facing} to the next real connected neighbor, skipping unconnected directions exactly like
 * the original's own skip-forward loop did (just without needing to know which specific sub-hitbox was
 * clicked to trigger it). No client-only directional arrow overlay ({@code TileTubeOnewayRenderer}) - same
 * "keep the mechanic, drop the flourish" precedent as every other device this port has shipped.
 */
public class TileTubeOneway extends TileTube {

	private Direction facing = Direction.NORTH;

	public TileTubeOneway(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.TUBE_ONEWAY.get(), pos, state);
	}

	public Direction getFacing() {
		return facing;
	}

	/** Cycles {@link #facing} to the next direction (wrapping) with a real {@link IEssentiaTransport}
	 * neighbor, matching the original's own skip-forward-until-connectable loop. Falls back to a plain
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
	protected boolean isRelayDirection(Direction dir) {
		return dir == facing;
	}

	@Override
	protected boolean isPullDirection(Direction dir) {
		return dir != facing;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Facing", facing.ordinal());
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains("Facing")) {
			facing = Direction.values()[tag.getInt("Facing")];
		}
	}
}

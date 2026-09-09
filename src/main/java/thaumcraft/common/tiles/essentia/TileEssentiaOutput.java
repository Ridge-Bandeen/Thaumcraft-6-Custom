package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.lib.events.EssentiaHandler;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileEssentiaOutput} - {@link TileEssentiaInput}'s exact mirror image (see
 * that class's own doc comment for the real "long-range bridge, not a physical-inventory device" mechanic
 * and why). Every 5 ticks, checks whether the real {@link IEssentiaTransport} neighbor behind it (the tube
 * network side) actually wants anything, and if so tries {@link EssentiaHandler#drainEssentia} (an
 * omnidirectional scan for the nearest real {@code IAspectSource} within range) to pull 1 unit, pushing it
 * into the network neighbor only once that succeeds.
 *
 * {@link #canInputFrom} is always {@code false} and {@link #addEssentia} is a real, deliberate no-op accept
 * - this tile holds no essentia of its own and is never a legitimate push target from the network side; it
 * only ever moves essentia by actively draining a distant source in {@link #tick}. Real, deliberately zero
 * base suction ({@link #getSuctionAmount}, matching the true original exactly) - unlike the Input tile, this
 * one never competes for tube-network demand; it only responds to what the network already wants.
 */
public class TileEssentiaOutput extends BlockEntity implements IEssentiaTransport {

	private static final int RANGE = 16;

	private int count;

	public TileEssentiaOutput(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.ESSENTIA_OUTPUT.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileEssentiaOutput tile) {
		if (level.isClientSide) return;
		if (tile.count++ % 5 != 0) return;

		Direction networkFace = state.getValue(BlockStateProperties.FACING).getOpposite();
		if (!(level.getBlockEntity(pos.relative(networkFace)) instanceof IEssentiaTransport neighbor)) return;
		Direction towardTile = networkFace.getOpposite();
		if (!neighbor.canInputFrom(towardTile)) return;
		int neighborSuction = neighbor.getSuctionAmount(towardTile);
		if (neighborSuction <= 0) return;
		Aspect aspect = neighbor.getSuctionType(towardTile);
		if (aspect == null) return;

		if (EssentiaHandler.drainEssentia(tile, aspect, RANGE)) {
			neighbor.addEssentia(aspect, 1, towardTile);
		}
	}

	@Override
	public boolean isConnectable(Direction face) {
		return face == getBlockState().getValue(BlockStateProperties.FACING).getOpposite();
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return false;
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return face == getBlockState().getValue(BlockStateProperties.FACING).getOpposite();
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
		return 0;
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	@Override
	public Aspect getEssentiaType(Direction face) {
		return null;
	}

	@Override
	public int getEssentiaAmount(Direction face) {
		return 0;
	}

	@Override
	public int takeEssentia(Aspect aspect, int amount, Direction face) {
		return 0;
	}

	@Override
	public int addEssentia(Aspect aspect, int amount, Direction face) {
		return amount;
	}
}

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
 * Replaces the old (1.12.2) {@code TileEssentiaInput} - a real long-range bridge, not a physical-inventory
 * device despite the true original's own thematic dispenser-based crafting recipe (see
 * {@code EssentiaHandler}'s own doc comment for how that was confirmed by reading the original's real
 * {@code EssentiaHandler} directly rather than assumed from the recipe's flavor). Every 5 ticks, peeks at
 * the real {@link IEssentiaTransport} neighbor behind it (the tube network side, {@code getFacing().getOpposite()}),
 * and if it has essentia to spare, tries {@link EssentiaHandler#addEssentia} (the nearest {@code IAspectSource} in
 * the 16-deep box in front of its face - a jar, altar, etc.) to actually deposit it; only
 * once that succeeds does it really drain the network neighbor. Mounted with a real always-128 suction (its
 * own {@link #getSuctionAmount}) so a plain tube's own suction calculation preferentially routes flow toward
 * it over a weaker downstream demand - it's meant to win that competition, not participate passively.
 *
 * {@link #canOutputTo} is always {@code false} and {@link #addEssentia} is a real, deliberate no-op accept
 * (returns the full requested amount, taking nothing) - this tile holds no essentia of its own and is never
 * a legitimate push target; it only ever moves essentia by actively draining its network neighbor directly
 * in {@link #tick}, matching the true original's own real shape exactly.
 */
public class TileEssentiaInput extends BlockEntity implements IEssentiaTransport {

	private static final int RANGE = 16;

	private int count;

	public TileEssentiaInput(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.ESSENTIA_INPUT.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileEssentiaInput tile) {
		if (level.isClientSide) return;
		if (tile.count++ % 5 != 0) return;

		Direction networkFace = state.getValue(BlockStateProperties.FACING).getOpposite();
		if (!(level.getBlockEntity(pos.relative(networkFace)) instanceof IEssentiaTransport neighbor)) return;
		Direction towardTile = networkFace.getOpposite();
		if (!neighbor.canOutputTo(towardTile)) return;
		if (neighbor.getEssentiaAmount(towardTile) <= 0) return;
		int mySuction = tile.getSuctionAmount(networkFace);
		if (neighbor.getSuctionAmount(towardTile) >= mySuction) return;
		if (mySuction < neighbor.getMinimumSuction()) return;

		Aspect aspect = neighbor.getEssentiaType(towardTile);
		if (aspect == null) return;
		if (EssentiaHandler.addEssentia(tile, aspect, towardTile, RANGE, false, 5)) {
			neighbor.takeEssentia(aspect, 1, towardTile);
		}
	}

	@Override
	public boolean isConnectable(Direction face) {
		return face == getBlockState().getValue(BlockStateProperties.FACING).getOpposite();
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return face == getBlockState().getValue(BlockStateProperties.FACING).getOpposite();
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return false;
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
		return 128;
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

	@Override
	public void setRemoved() {
		super.setRemoved();
		thaumcraft.common.lib.events.EssentiaHandler.forget(this);
	}
}

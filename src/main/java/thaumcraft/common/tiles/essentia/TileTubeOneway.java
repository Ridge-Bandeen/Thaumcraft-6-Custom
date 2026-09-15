package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileTubeOneway} - a {@link TileTube} that only accepts suction from, and
 * only feeds essentia toward, one designated {@link TileTube#getFacing} neighbor - the true original's own
 * in-game research text describes it exactly: "Directional essentia tubes only accept suction from one
 * direction."
 *
 * <p>The real original's own {@code calculateSuction}/{@code equalizeWithNeighbours} overrides reduce to two
 * distinct restrictions on the same one field, not a symmetric "only talk to this one neighbor" rule:
 * {@link #isRelayDirection} only relays suction that originates from the facing neighbor (so this tube's own
 * reported demand only ever propagates backward toward whichever consumer it's pointed at), while
 * {@link #isPullDirection} <i>excludes</i> that same neighbor as a source (so essentia never flows back in
 * from the direction it's meant to flow out toward - the real one-way/backflow-prevention half of the
 * mechanic). Re-derived directly from the original's own {@code facing == loc.getOpposite()}/
 * {@code facing != loc.getOpposite()} conditions plus its own research text, not guessed.
 *
 * <p>{@code facing} itself - along with the real click-to-configure-it central core button - lives on the
 * shared {@link TileTube} base now (see its own doc comment), matching the true original's own class layout:
 * every real tube variant carries the field and the real click interaction, but only this subclass's
 * {@link #isRelayDirection}/{@link #isPullDirection} overrides actually consult it. No client-only
 * directional arrow overlay ({@code TileTubeOnewayRenderer}) - same "keep the mechanic, drop the flourish"
 * precedent as every other device this port has shipped.
 */
public class TileTubeOneway extends TileTube {

	public TileTubeOneway(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.TUBE_ONEWAY.get(), pos, state);
	}

	@Override
	protected boolean isRelayDirection(Direction dir) {
		return dir == getFacing();
	}

	@Override
	protected boolean isPullDirection(Direction dir) {
		return dir != getFacing();
	}
}

package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileTubeRestrict} - a {@link TileTube} that halves its own effective
 * suction (instead of the real per-hop {@code -1} decay every plain tube applies, see {@link TileTube
 * #adjustSuction}'s own doc comment), throttling how eagerly and how often it wins the suction comparison
 * against competing demand on the network. No player interaction of its own; the throttling is a fixed
 * property of the block, matching the original exactly ({@code calculateSuction(filter, true, dir)} always
 * forced {@code restrict=true}, storing {@code suck/2} - the exact real formula this override reproduces).
 */
public class TileTubeRestrict extends TileTube {

	public TileTubeRestrict(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.TUBE_RESTRICT.get(), pos, state);
	}

	@Override
	protected int adjustSuction(int suck) {
		return suck / 2;
	}
}

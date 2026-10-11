package thaumcraft.common.blocks.world;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Silverwood leaves, as the original BlockLeavesTC: natural (not player-placed) silverwood leaves feed the aura, adding
 * 0.01 vis on each random tick while the chunk's vis is below its base. Vanilla leaves only tick while decaying, so these
 * tick whenever they are natural; decay itself is unchanged.
 */
public class BlockSilverwoodLeaves extends LeavesBlock {

	public static final MapCodec<BlockSilverwoodLeaves> CODEC = simpleCodec(BlockSilverwoodLeaves::new);

	public BlockSilverwoodLeaves(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<? extends LeavesBlock> codec() {
		return CODEC;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return !state.getValue(PERSISTENT);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(PERSISTENT) && AuraHandler.getVis(level, pos) < AuraHandler.getAuraBase(level, pos)) {
			AuraHandler.addVis(level, pos, 0.01f);
		}
		super.randomTick(state, level, pos, random);
	}
}

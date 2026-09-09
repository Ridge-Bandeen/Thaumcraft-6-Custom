package thaumcraft.common.blocks.essentia;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.essentia.TileTubeBuffer;

/**
 * Replaces the old (1.12.2) {@code BlockTube} (the buffer variant) - see {@link TileTubeBuffer}'s own doc
 * comment for the real multi-aspect storage mechanic. Extends {@link BlockTube} directly (not just its
 * shape/connection logic reused elsewhere) - no per-block interaction of its own, unlike
 * {@link BlockTubeFilter}.
 */
public class BlockTubeBuffer extends BlockTube {

	public static final MapCodec<BlockTubeBuffer> CODEC = simpleCodec(BlockTubeBuffer::new);

	public BlockTubeBuffer(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileTubeBuffer(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.TUBE_BUFFER.get()
				? (lvl, p, st, be) -> TileTubeBuffer.tick(lvl, p, st, (TileTubeBuffer) be)
				: null;
	}
}

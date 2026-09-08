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
import thaumcraft.common.tiles.essentia.TileTubeValve;

/**
 * Replaces the old (1.12.2) {@code BlockTubeValve} - see {@link TileTubeValve}'s own doc comment for the
 * redstone-gated relay mechanic and what's simplified. Otherwise a plain {@link BlockTube}: same shape, same
 * multipart connection rendering, same interaction-less placement.
 */
public class BlockTubeValve extends BlockTube {

	public static final MapCodec<BlockTubeValve> CODEC = simpleCodec(BlockTubeValve::new);

	public BlockTubeValve(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileTubeValve(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.TUBE_VALVE.get()
				? (lvl, p, st, be) -> TileTubeValve.tick(lvl, p, st, (TileTubeValve) be)
				: null;
	}
}

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
import thaumcraft.common.tiles.essentia.TileTube;
import thaumcraft.common.tiles.essentia.TileTubeOneway;

/**
 * Replaces the old (1.12.2) {@code BlockTubeOneway} - see {@link TileTubeOneway}'s own doc comment for the
 * one-directional suction/flow mechanic. The real wand-click facing configuration lives entirely on the tile
 * now (inherited {@link TileTube#onCasterRightClick}), same as every other real tube interaction - no block-
 * level override needed here at all, same shape as {@link BlockTubeRestrict}.
 */
public class BlockTubeOneway extends BlockTube {

	public static final MapCodec<BlockTubeOneway> CODEC = simpleCodec(BlockTubeOneway::new);

	public BlockTubeOneway(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileTubeOneway(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.TUBE_ONEWAY.get()
				? (lvl, p, st, be) -> TileTube.tick(lvl, p, st, (TileTube) be)
				: null;
	}
}

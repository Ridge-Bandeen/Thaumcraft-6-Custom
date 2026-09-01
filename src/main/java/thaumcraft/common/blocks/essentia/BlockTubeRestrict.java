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
import thaumcraft.common.tiles.essentia.TileTubeRestrict;

/**
 * Replaces the old (1.12.2) {@code BlockTubeRestrict} - a plain {@link BlockTube} whose tile halves its own
 * suction (see {@link TileTubeRestrict}'s own doc comment). No interaction, no shape/connection
 * differences - only {@link #newBlockEntity}/{@link #getTicker} differ from the base tube.
 */
public class BlockTubeRestrict extends BlockTube {

	public static final MapCodec<BlockTubeRestrict> CODEC = simpleCodec(BlockTubeRestrict::new);

	public BlockTubeRestrict(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileTubeRestrict(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.TUBE_RESTRICT.get()
				? (lvl, p, st, be) -> TileTube.tick(lvl, p, st, (TileTube) be)
				: null;
	}
}

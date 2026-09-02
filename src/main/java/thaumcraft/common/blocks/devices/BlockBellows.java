package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileBellows;

/**
 * Replaces the old (1.12.2) {@code BlockBellows} - see {@link TileBellows}'s own doc comment for the
 * furnace-speedup mechanic. Same "full 6-way {@code FACING}, redstone-inverted {@code ENABLED}" shape as
 * {@link BlockArcaneEar}, not just the horizontal-only facing most of this port's other devices use - the
 * original placed it the same way (any face, including up/down), and there's no reason a bellows can't sit
 * under or above the furnace it feeds.
 */
public class BlockBellows extends Block implements EntityBlock {

	public static final MapCodec<BlockBellows> CODEC = simpleCodec(BlockBellows::new);

	private static final VoxelShape SHAPE = Shapes.box(0.1, 0.1, 0.1, 0.9, 0.9, 0.9);

	public BlockBellows(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(BlockStateProperties.FACING, Direction.NORTH)
				.setValue(BlockStateProperties.ENABLED, true));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.FACING, BlockStateProperties.ENABLED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState()
				.setValue(BlockStateProperties.FACING, context.getClickedFace().getOpposite())
				.setValue(BlockStateProperties.ENABLED, true);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
		boolean shouldBeEnabled = !level.hasNeighborSignal(pos);
		if (shouldBeEnabled != state.getValue(BlockStateProperties.ENABLED)) {
			level.setBlock(pos, state.setValue(BlockStateProperties.ENABLED, shouldBeEnabled), 3);
		}
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileBellows(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.BELLOWS.get()
				? (lvl, pos, st, be) -> TileBellows.tick(lvl, pos, st, (TileBellows) be)
				: null;
	}
}

package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
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
import thaumcraft.common.tiles.devices.TileLampArcane;

/**
 * Replaces the old (1.12.2) {@code BlockLamp} (the {@code lamp_arcane} instance of it - {@code BlockLamp}
 * was shared with the not-yet-ported Growth/Fertility lamp variants, ported here as its own class instead
 * since only the Arcane one is in scope). See {@link TileLampArcane}'s own doc comment for the light-
 * scatter mechanic.
 *
 * As the original it hangs off the face it was placed against (FACING points at its support, the stand drawn
 * there) and drops if that support goes.
 * ENABLED still toggles the normal way (redstone-inverted, matching every other {@code IBlockEnabled}
 * device the original had): lit unless powered.
 */
public class BlockLampArcane extends Block implements EntityBlock {

	public static final MapCodec<BlockLampArcane> CODEC = simpleCodec(BlockLampArcane::new);

	private static final VoxelShape SHAPE = Shapes.box(0.25, 0.125, 0.25, 0.75, 0.875, 0.75);

	public BlockLampArcane(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.ENABLED, true)
				.setValue(BlockStateProperties.FACING, net.minecraft.core.Direction.DOWN));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.ENABLED, BlockStateProperties.FACING);
	}

	/** The lamp's stand goes on the face it was placed against, like the original's {@code BlockLamp} (which had a
	 * {@code FACING} property the port had dropped, so every arcane lamp stood the same way). */
	@Override
	public net.minecraft.world.level.block.state.BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
		return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
		// as the original: with nothing left to hang on, it pops off as an item
		if (level.getBlockState(pos.relative(state.getValue(BlockStateProperties.FACING))).isAir()) {
			level.destroyBlock(pos, true);
			return;
		}
		boolean shouldBeEnabled = !level.hasNeighborSignal(pos);
		if (shouldBeEnabled != state.getValue(BlockStateProperties.ENABLED)) {
			level.setBlock(pos, state.setValue(BlockStateProperties.ENABLED, shouldBeEnabled), 3);
			if (!shouldBeEnabled && level.getBlockEntity(pos) instanceof TileLampArcane tile) {
				tile.removeLights(level);
			}
		}
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileLampArcane tile) {
			tile.removeLights(level);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileLampArcane(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.LAMP_ARCANE.get()
				? (lvl, pos, st, be) -> TileLampArcane.tick(lvl, pos, st, (TileLampArcane) be)
				: null;
	}
}

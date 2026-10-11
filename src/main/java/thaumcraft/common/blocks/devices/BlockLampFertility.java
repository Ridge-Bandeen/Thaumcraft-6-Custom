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
import thaumcraft.common.tiles.devices.TileLampFertility;

/**
 * Replaces the old (1.12.2) {@code BlockLamp} (the {@code lamp_fertility} instance) - see
 * {@link BlockLampGrowth}'s own doc comment for why this is a separate, purpose-built class rather than a
 * shared generic base, and {@link TileLampFertility}'s for the breeding mechanic. Same shell as
 * {@code BlockLampGrowth}: {@code FACING} points toward wherever it draws essentia from (and doubles as the
 * support-check direction), drops itself if that support is removed, lit only while {@code ENABLED}.
 *
 * Same asset situation as the Growth Lamp - no authentic {@code lamp_fert_on}/{@code _off} texture exists
 * anywhere in the original 1.12.2 tree (confirmed the same way). Reuses Arcane Lamp's cube geometry and
 * texture as an explicitly-flagged placeholder.
 */
public class BlockLampFertility extends Block implements EntityBlock {

	public static final MapCodec<BlockLampFertility> CODEC = simpleCodec(BlockLampFertility::new);

	private static final VoxelShape SHAPE = Shapes.box(0.25, 0.125, 0.25, 0.75, 0.875, 0.75);

	public BlockLampFertility(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(BlockStateProperties.FACING, Direction.DOWN)
				.setValue(BlockStateProperties.ENABLED, false));
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
		return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace().getOpposite())
				.setValue(BlockStateProperties.ENABLED, false);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
		Direction facing = state.getValue(BlockStateProperties.FACING);
		if (level.getBlockState(pos.relative(facing)).isAir()) {
			level.destroyBlock(pos, true);
			return;
		}
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileLampFertility(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.LAMP_FERTILITY.get()
				? (lvl, pos, st, be) -> TileLampFertility.tick(lvl, pos, st, (TileLampFertility) be)
				: null;
	}

	/** As the original: the essentia inside pours into the aura as flux when this goes. */
	@Override
	protected void onRemove(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
			net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState newState, boolean movedByPiston) {
		thaumcraft.common.blocks.EssentiaSpill.onRemove(state, level, pos, newState);
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}

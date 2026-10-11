package thaumcraft.common.blocks.essentia;

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
import thaumcraft.common.tiles.essentia.TileEssentiaInput;

/**
 * Replaces the old (1.12.2) {@code BlockEssentiaTransport} (the input variant - see {@link TileEssentiaInput}'s
 * own doc comment for the real mechanic). Full 6-way {@link BlockStateProperties#FACING}, same
 * "small device stub sticking out of the mounting face" shape family as {@link BlockJar}/{@code BlockMirror}
 * - not a full cube, no support-loss check (the original didn't have one for this device either).
 */
public class BlockEssentiaInput extends Block implements EntityBlock {

	public static final MapCodec<BlockEssentiaInput> CODEC = simpleCodec(BlockEssentiaInput::new);

	private static final VoxelShape SHAPE = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.5, 0.75);

	public BlockEssentiaInput(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.FACING, Direction.UP));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileEssentiaInput(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.ESSENTIA_INPUT.get()
				? (lvl, p, st, be) -> TileEssentiaInput.tick(lvl, p, st, (TileEssentiaInput) be)
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

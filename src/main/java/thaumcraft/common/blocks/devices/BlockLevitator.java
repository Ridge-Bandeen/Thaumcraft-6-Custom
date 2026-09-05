package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileLevitator;

/**
 * Replaces the old (1.12.2) {@code BlockLevitator} - a directional vis-powered "wind tunnel" that pushes
 * entities along its facing (see {@link TileLevitator}'s own doc comment for the real push/range mechanic).
 * Same {@code FACING}-from-clicked-face placement convention as {@link BlockArcaneEar}.
 *
 * Real, deliberate simplification: the original used a CodeChickenLib multi-hitbox raytracer so only
 * clicking a small raised "key" nub on one face would cycle the range, leaving the rest of the block's real
 * interaction (none, in the original) untouched - that whole precise-sub-hitbox-click system doesn't exist
 * in modern MC and isn't worth rebuilding for this one interaction. Right-clicking anywhere on the block now
 * cycles the range instead - same real function, a plain interaction like every other device this port has
 * shipped.
 */
public class BlockLevitator extends Block implements EntityBlock {

	public static final MapCodec<BlockLevitator> CODEC = simpleCodec(BlockLevitator::new);

	private static final VoxelShape SHAPE_DOWN = Shapes.box(0.375, 0.0, 0.375, 0.625, 0.9375, 0.625);
	private static final VoxelShape SHAPE_UP = Shapes.box(0.375, 0.0625, 0.375, 0.625, 1.0, 0.625);
	private static final VoxelShape SHAPE_NORTH = Shapes.box(0.375, 0.375, 0.0, 0.625, 0.625, 0.9375);
	private static final VoxelShape SHAPE_SOUTH = Shapes.box(0.375, 0.375, 0.0625, 0.625, 0.625, 1.0);
	private static final VoxelShape SHAPE_WEST = Shapes.box(0.0, 0.375, 0.375, 0.9375, 0.625, 0.625);
	private static final VoxelShape SHAPE_EAST = Shapes.box(0.0625, 0.375, 0.375, 1.0, 0.625, 0.625);

	public BlockLevitator(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(BlockStateProperties.FACING, Direction.UP)
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
		return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(BlockStateProperties.FACING)) {
			case DOWN -> SHAPE_DOWN;
			case UP -> SHAPE_UP;
			case NORTH -> SHAPE_NORTH;
			case SOUTH -> SHAPE_SOUTH;
			case WEST -> SHAPE_WEST;
			case EAST -> SHAPE_EAST;
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (level.getBlockEntity(pos) instanceof TileLevitator tile) {
			tile.increaseRange(player);
		}
		return InteractionResult.CONSUME;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileLevitator(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.LEVITATOR.get()
				? (lvl, pos, st, be) -> TileLevitator.tick(lvl, pos, st, (TileLevitator) be)
				: null;
	}
}

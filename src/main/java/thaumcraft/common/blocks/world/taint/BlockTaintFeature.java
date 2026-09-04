package thaumcraft.common.blocks.world.taint;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;

/**
 * Replaces the old (1.12.2) {@code BlockTaintFeature} - a small mushroom-like growth that attaches to any
 * one of the six faces of a real solid neighbor (via a real {@link DirectionProperty} {@link #FACING},
 * matching modern vanilla's own attached-growth convention - {@code FACING} points from this block toward
 * its real supporting neighbor, same real semantics as the original's own facing field), and real self-
 * removes via {@link #updateShape} once that support stops being sturdy (the modern replacement for the
 * original's own {@code neighborChanged} check).
 *
 * Real per-tick behavior via {@link #randomTick}: dies without a real nearby taint seed (same shared cadence
 * every other taint block uses), otherwise keeps spreading real fibres via the now-real {@link
 * TaintHelper#spreadFibres}. Real per-facing bounding box, translated directly from the original's own six
 * literal {@code AxisAlignedBB} cases (already 0-1 normalized, so no math was needed - just a straight
 * {@link Shapes#box} per {@link Direction}).
 *
 * {@code die()} now really targets real {@link BlockFluxGoo} (real once that block itself became real), and
 * {@link #onRemove} really keeps the original's own 33% chance to spawn a real {@code EntityTaintCrawler} on
 * removal instead of just polluting the aura (real once that creature became real too), and {@link
 * #randomTick} really keeps the original's own 1% chance to convert into a real {@code taintGeyser} when
 * growing on top of a real vertical {@code taintLog} (real once that block became real too) - none of these
 * were real simplifications so much as this class simply being written before its own dependencies were.
 * This block never drops itself as an item either way, matching the original's own real {@code
 * getItemDropped} returning nothing.
 */
public class BlockTaintFeature extends Block implements ITaintBlock {

	public static final MapCodec<BlockTaintFeature> CODEC = simpleCodec(BlockTaintFeature::new);

	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	private static final VoxelShape SHAPE_DOWN = Shapes.box(0.125, 0.625, 0.125, 0.875, 1.0, 0.875);
	private static final VoxelShape SHAPE_UP = Shapes.box(0.125, 0.0, 0.125, 0.875, 0.375, 0.875);
	private static final VoxelShape SHAPE_NORTH = Shapes.box(0.125, 0.125, 0.625, 0.875, 0.875, 1.0);
	private static final VoxelShape SHAPE_SOUTH = Shapes.box(0.125, 0.125, 0.0, 0.875, 0.875, 0.375);
	private static final VoxelShape SHAPE_WEST = Shapes.box(0.625, 0.125, 0.125, 1.0, 0.875, 0.875);
	private static final VoxelShape SHAPE_EAST = Shapes.box(0.0, 0.125, 0.125, 0.375, 0.875, 0.875);

	public BlockTaintFeature(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		Direction facing = state.getValue(FACING);
		if (direction == facing.getOpposite() && !neighborState.isFaceSturdy(level, neighborPos, facing)) {
			return Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case DOWN -> SHAPE_DOWN;
			case UP -> SHAPE_UP;
			case NORTH -> SHAPE_NORTH;
			case SOUTH -> SHAPE_SOUTH;
			case WEST -> SHAPE_WEST;
			case EAST -> SHAPE_EAST;
		};
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!TaintHelper.isNearTaintSeed(level, pos) && random.nextInt(10) == 0) {
			die(level, pos, state);
			return;
		}
		TaintHelper.spreadFibres(level, pos);
		BlockState below = level.getBlockState(pos.below());
		if (below.is(BlocksTC.taintLog.get()) && below.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) == Direction.Axis.Y
				&& random.nextInt(100) == 0) {
			level.setBlockAndUpdate(pos, BlocksTC.taintGeyser.get().defaultBlockState());
		}
	}

	@Override
	public void die(Level level, BlockPos pos, BlockState state) {
		level.setBlockAndUpdate(pos, BlocksTC.fluxGoo.get().defaultBlockState());
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!level.isClientSide && level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) {
			if (level.random.nextFloat() < 0.333f) {
				thaumcraft.common.entities.monster.tainted.EntityTaintCrawler crawler =
						thaumcraft.common.registry.ThaumcraftEntities.TAINT_CRAWLER.get().create(serverLevel);
				if (crawler != null) {
					crawler.moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, (float) level.random.nextInt(360), 0.0f);
					level.addFreshEntity(crawler);
				}
			} else {
				AuraHelper.polluteAura(level, pos, 1.0f, true);
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}

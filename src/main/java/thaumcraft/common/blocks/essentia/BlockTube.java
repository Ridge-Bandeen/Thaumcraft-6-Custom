package thaumcraft.common.blocks.essentia;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.essentia.TileTube;

/**
 * Replaces the old (1.12.2) {@code BlockTube} - see {@link TileTube}'s own doc comment for the relay
 * mechanic and what's simplified. Reuses vanilla's own {@code PipeBlock.PROPERTY_BY_DIRECTION} (the same
 * {@code NORTH}/{@code SOUTH}/{@code EAST}/{@code WEST}/{@code UP}/{@code DOWN} booleans
 * {@link net.minecraft.world.level.block.IronBarsBlock}/the pane family use) - recomputed from real neighbor
 * state on placement and on every neighbor change, purely for the multipart model to pick the right stub
 * pieces; connecting doesn't gate function here since there's no per-face toggle left to gate (see
 * {@link TileTube}).
 *
 * Simplified collision shape: a single fixed inset box rather than the original's precise per-direction
 * stub hitboxes (itself built on the same CodeChicken raytracing library already dropped for the
 * interaction model) - a reasonable, if less exact, physical shape.
 */
public class BlockTube extends Block implements EntityBlock {

	public static final MapCodec<BlockTube> CODEC = simpleCodec(BlockTube::new);

	private static final VoxelShape SHAPE = Shapes.box(0.1875, 0.1875, 0.1875, 0.8125, 0.8125, 0.8125);

	public BlockTube(BlockBehaviour.Properties properties) {
		super(properties);
		BlockState any = getStateDefinition().any();
		for (Direction dir : Direction.values()) {
			any = any.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), false);
		}
		registerDefaultState(any);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PipeBlock.NORTH, PipeBlock.SOUTH, PipeBlock.EAST, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), connectsTo(level, pos, direction));
	}

	private static BlockState withConnections(BlockState state, LevelAccessor level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connectsTo(level, pos, dir));
		}
		return state;
	}

	private static boolean connectsTo(LevelAccessor level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos.relative(direction)) instanceof IEssentiaTransport transport
				&& transport.isConnectable(direction.getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileTube(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.TUBE.get()
				? (lvl, p, st, be) -> TileTube.tick(lvl, p, st, (TileTube) be)
				: null;
	}
}

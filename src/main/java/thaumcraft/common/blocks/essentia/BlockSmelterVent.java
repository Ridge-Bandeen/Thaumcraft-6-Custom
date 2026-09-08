package thaumcraft.common.blocks.essentia;

import java.util.Map;
import javax.annotation.Nullable;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;

/**
 * Replaces the old (1.12.2) {@code BlockSmelterVent}. Same real placement restriction as
 * {@link BlockSmelterAux} (only against a real {@link BlockSmelter}, on a non-facing side) - found and
 * restored alongside it while reviewing this port's own "dead/blocked" candidates (see
 * {@link thaumcraft.common.tiles.essentia.TileSmelter}'s own doc comment).
 *
 * Unlike aux, vent's own gameplay effect (see the true pre-port original's own {@code TileSmelter#smeltItem})
 * turned out to be a client-only FX/sound trigger with an apparent infinite-loop bug in the decompiled
 * source when NO vent block is present at all (an unconditioned {@code while(true)} retry with no fallback
 * exit) - fixed, not mechanically translated, same class of correction as the earlier `.split("||")`
 * research-gate bug fix this session: {@link thaumcraft.common.tiles.essentia.TileSmelter}'s own vent-or-pollute
 * logic bounds each flux unit to a single roll against whatever real vent sides exist, falling back to direct
 * aura pollution instead of retrying forever.
 */
public class BlockSmelterVent extends Block {

	public static final MapCodec<BlockSmelterVent> CODEC = simpleCodec(BlockSmelterVent::new);

	private static final Map<Direction, VoxelShape> SHAPES = Maps.newEnumMap(Direction.class);
	static {
		// Each shape is the half of the block nearest the smelter this vent points at (FACING), matching the
		// true original's own per-facing AxisAlignedBB exactly (a short pipe stub flush against the smelter).
		SHAPES.put(Direction.NORTH, Shapes.box(0.125, 0.125, 0.0, 0.875, 0.875, 0.5));
		SHAPES.put(Direction.SOUTH, Shapes.box(0.125, 0.125, 0.5, 0.875, 0.875, 1.0));
		SHAPES.put(Direction.WEST, Shapes.box(0.0, 0.125, 0.125, 0.5, 0.875, 0.875));
		SHAPES.put(Direction.EAST, Shapes.box(0.5, 0.125, 0.125, 1.0, 0.875, 0.875));
	}

	public BlockSmelterVent(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.HORIZONTAL_FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		Direction towardSmelter = clicked.getAxis().isHorizontal() ? clicked.getOpposite() : Direction.NORTH;
		return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, towardSmelter);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction towardSmelter = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		BlockState smelterState = level.getBlockState(pos.relative(towardSmelter));
		return smelterState.is(BlocksTC.smelter.get())
				&& smelterState.getValue(BlockStateProperties.HORIZONTAL_FACING) != towardSmelter.getOpposite();
	}
}

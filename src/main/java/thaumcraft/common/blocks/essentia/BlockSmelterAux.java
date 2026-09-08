package thaumcraft.common.blocks.essentia;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.api.blocks.BlocksTC;

/**
 * Replaces the old (1.12.2) {@code BlockSmelterAux}. A real, functional attachment - not decorative - found
 * and restored while reviewing this port's own "dead/blocked" candidates (see
 * {@link thaumcraft.common.tiles.essentia.TileSmelter}'s own doc comment and the port-tracking memory for the
 * fuller review): confirmed via the true pre-port original
 * source ({@code git show 31baee9:...TileSmelter.java}) that a Smelter checks every non-facing horizontal
 * side for one of these, and if found, tries to feed essentia to a {@code TileAlembic} stack above *that*
 * position too - a second (third, with two aux blocks) simultaneous essentia output point, not just cosmetic
 * building fidelity.
 *
 * Placement is restricted exactly like the original's own {@code canPlaceBlockOnSide}: only directly against
 * a real {@link BlockSmelter}, on one of its non-facing (non-"door") sides. This block's own {@code FACING}
 * always points back at the smelter it's attached to - {@link TileSmelter}'s tick loop reads it the same way
 * ({@code getValue(FACING) == side.getOpposite()}) to find which sides have one attached.
 */
public class BlockSmelterAux extends Block {

	public static final MapCodec<BlockSmelterAux> CODEC = simpleCodec(BlockSmelterAux::new);

	public BlockSmelterAux(BlockBehaviour.Properties properties) {
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

package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
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
 * Replaces the old (1.12.2) {@code BlockBrainBox} - a small decorative bracket that must be mounted flush
 * against a real {@code thaumatorium}/{@code thaumatoriumTop} block (any of its 6 faces) and pops itself off
 * if that support is ever removed, same real "must stay attached" mechanic the true original had
 * ({@code neighborChanged}/{@code canPlaceBlockOnSide}). No block entity - the original didn't have one
 * either.
 *
 * <b>Real, disclosed simplification</b>: the original's own real function - each mounted BrainBox around a
 * Thaumatorium raised {@code maxRecipes} by 2, letting the device remember more curated recipes to auto-cycle
 * through ({@code TileThaumatorium#getUpgrades}) - doesn't apply here. {@link
 * thaumcraft.common.tiles.crafting.TileThaumatorium}'s own doc comment already documents a deliberate,
 * different design for this port (a real deterministic first-match against whatever catalyst is inserted,
 * not a player-curated recipe list) - there's nothing left for a BrainBox to extend. It ships anyway as the
 * true original's own real, authentic decorative device (and the real result of the {@code MnemonicMatrix}
 * recipe, see {@code data/thaumcraft/recipe/brain_box.json}), not invented content.
 *
 * Also drops the original's own extra placement rule excluding the Thaumatorium's own declared facing side
 * (a real device orientation concept neither {@code BlockThaumatorium} nor {@code thaumatoriumTop} has in
 * this port - both are plain, faceless blocks here, see their own doc comments) - real, forced by that
 * earlier simplification, not a new one: any of the 6 faces works.
 */
public class BlockBrainBox extends Block {

	public static final MapCodec<BlockBrainBox> CODEC = simpleCodec(BlockBrainBox::new);

	private static final VoxelShape SHAPE = Shapes.box(0.1875, 0.1875, 0.1875, 0.8125, 0.8125, 0.8125);

	public BlockBrainBox(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.FACING, Direction.DOWN));
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
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	private static boolean isValidSupport(LevelReader level, BlockPos supportPos) {
		Block block = level.getBlockState(supportPos).getBlock();
		return block == BlocksTC.thaumatorium.get() || block == BlocksTC.thaumatoriumTop.get();
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getClickedFace().getOpposite();
		if (!isValidSupport(context.getLevel(), context.getClickedPos().relative(facing))) return null;
		return defaultBlockState().setValue(BlockStateProperties.FACING, facing);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction facing = state.getValue(BlockStateProperties.FACING);
		return isValidSupport(level, pos.relative(facing));
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
		if (!canSurvive(state, level, pos)) {
			level.destroyBlock(pos, true);
		}
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
	}
}

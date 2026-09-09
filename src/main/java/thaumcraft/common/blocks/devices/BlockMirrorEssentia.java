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
import thaumcraft.common.tiles.devices.TileMirrorEssentia;

/**
 * Replaces the old (1.12.2) {@code BlockMirror} (the essentia-transport variant - see {@link TileMirrorEssentia}'s
 * own doc comment for the real mechanic; {@link BlockMirror} is this device's item-transport sibling and this
 * class mirrors its structure exactly, same real shape/facing/support-check). No {@code entityInside}
 * override here - unlike {@link BlockMirror}, this tile never reacts to a physical item entity touching it; it's
 * queried purely via {@link thaumcraft.api.aspects.IAspectSource} calls from other tiles (the Infusion Matrix,
 * chiefly).
 */
public class BlockMirrorEssentia extends Block implements EntityBlock {

	public static final MapCodec<BlockMirrorEssentia> CODEC = simpleCodec(BlockMirrorEssentia::new);

	public BlockMirrorEssentia(BlockBehaviour.Properties properties) {
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
		return switch (state.getValue(BlockStateProperties.FACING)) {
			case DOWN -> Shapes.box(0, 0.875, 0, 1, 1, 1);
			case UP -> Shapes.box(0, 0, 0, 1, 0.125, 1);
			case NORTH -> Shapes.box(0, 0, 0.875, 1, 1, 1);
			case SOUTH -> Shapes.box(0, 0, 0, 1, 1, 0.125);
			case WEST -> Shapes.box(0.875, 0, 0, 1, 1, 1);
			case EAST -> Shapes.box(0, 0, 0, 0.125, 1, 1);
		};
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
		Direction facing = state.getValue(BlockStateProperties.FACING);
		BlockPos support = pos.relative(facing.getOpposite());
		if (!level.getBlockState(support).isFaceSturdy(level, support, facing)) {
			level.destroyBlock(pos, true);
		}
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileMirrorEssentia(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.MIRROR_ESSENTIA.get()
				? (lvl, p, st, be) -> TileMirrorEssentia.tick(lvl, p, st, (TileMirrorEssentia) be)
				: null;
	}
}

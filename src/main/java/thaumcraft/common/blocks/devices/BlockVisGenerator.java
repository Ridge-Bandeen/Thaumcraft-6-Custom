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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileVisGenerator;

/**
 * Replaces the old (1.12.2) {@code BlockVisGenerator} - a device that drains vis from the local aura and
 * pushes it out as real energy to whatever's adjacent to its output face (see {@link TileVisGenerator}'s own
 * doc comment for why {@code IEnergyStorage} ports over almost mechanically here, unlike most devices this
 * session).
 *
 * On placement, scans all 6 neighbors for one that can receive energy and orients {@code FACING} towards it
 * - same behavior as the original. Falls back to {@link Direction#UP} if none found (the original fell
 * through to a generic facing-from-click default via the unported {@code BlockTCDevice}; a fixed fallback
 * here is an intentional simplification, not a functional loss - a generator with nothing to push into just
 * idles either way, and the facing can't be changed after placement in either version).
 *
 * {@code ENABLED} is a plain redstone-inverted toggle, same convention as every other {@code IBlockEnabled}
 * device this session (e.g. {@link BlockLampArcane}) - active unless powered.
 */
public class BlockVisGenerator extends Block implements EntityBlock {

	public static final MapCodec<BlockVisGenerator> CODEC = simpleCodec(BlockVisGenerator::new);

	private static final VoxelShape SHAPE = Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 1.0, 0.8125);

	public BlockVisGenerator(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(BlockStateProperties.FACING, Direction.UP)
				.setValue(BlockStateProperties.ENABLED, true));
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
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction facing = Direction.UP;
		for (Direction face : Direction.values()) {
			IEnergyStorage neighbor = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(face), face.getOpposite());
			if (neighbor != null && neighbor.canReceive()) {
				facing = face;
				break;
			}
		}
		return defaultBlockState().setValue(BlockStateProperties.FACING, facing)
				.setValue(BlockStateProperties.ENABLED, !level.hasNeighborSignal(pos));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
		boolean shouldBeEnabled = !level.hasNeighborSignal(pos);
		if (shouldBeEnabled != state.getValue(BlockStateProperties.ENABLED)) {
			level.setBlock(pos, state.setValue(BlockStateProperties.ENABLED, shouldBeEnabled), 3);
		}
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileVisGenerator(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.VIS_GENERATOR.get()
				? (lvl, pos, st, be) -> TileVisGenerator.tick(lvl, pos, st, (TileVisGenerator) be)
				: null;
	}
}

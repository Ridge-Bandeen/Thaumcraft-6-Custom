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
import thaumcraft.common.tiles.devices.TileLampGrowth;

/**
 * Replaces the old (1.12.2) {@code BlockLamp} (the {@code lamp_growth} instance - shared with Arcane/Fertility
 * variants in the original via a generic tile-class constructor param; this port already gave Arcane its own
 * standalone class, and Growth follows the same "purpose-built, not a shared generic base" pattern). See
 * {@link TileLampGrowth}'s own doc comment for the growth mechanic.
 *
 * {@code FACING} points toward wherever this lamp draws essentia from (set from the clicked face at
 * placement, opposite convention from a torch - matches the original exactly), and the lamp drops itself if
 * that support is removed. Lit (light level 15) only while {@code ENABLED} (has charge and isn't
 * redstone-powered).
 *
 * No authentic texture exists for this block anywhere in the original 1.12.2 asset tree (confirmed - not
 * just an unmigrated blockstate format like Arcane Lamp's was, the actual {@code lamp_grow_on}/{@code _off}
 * PNGs were never present at all). Reuses Arcane Lamp's cube geometry and texture as an explicitly-flagged
 * placeholder until real art exists, rather than blocking real gameplay logic on missing visuals - same
 * "ship the function, flag the visual gap" precedent as the Thaumometer's own missing model early this
 * session.
 */
public class BlockLampGrowth extends Block implements EntityBlock {

	public static final MapCodec<BlockLampGrowth> CODEC = simpleCodec(BlockLampGrowth::new);

	private static final VoxelShape SHAPE = Shapes.box(0.25, 0.125, 0.25, 0.75, 0.875, 0.75);

	public BlockLampGrowth(BlockBehaviour.Properties properties) {
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
		return new TileLampGrowth(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.LAMP_GROWTH.get()
				? (lvl, pos, st, be) -> TileLampGrowth.tick(lvl, pos, st, (TileLampGrowth) be)
				: null;
	}
}

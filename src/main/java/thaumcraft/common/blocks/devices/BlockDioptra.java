package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import thaumcraft.common.tiles.devices.TileDioptra;

/**
 * Replaces the old (1.12.2) {@code BlockDioptra} - an aura radar block. See {@link TileDioptra}'s own doc
 * comment for the scan logic.
 *
 * Unlike most of the toggleable devices this session, {@code ENABLED} here isn't a redstone-driven state
 * (the original overrode {@code updateState} to a no-op specifically to opt out of that) - it's a manual
 * right-click toggle between "showing vis" (enabled) and "showing flux" (disabled), same as the original's
 * {@code onBlockActivated}.
 *
 * Comparator output reads the scan grid's center cell (index 84 of the 13x13 grid, i.e. this block's own
 * chunk) scaled from its 0-64 byte range to a 0-15 comparator signal, exactly as the original computed it.
 *
 * The collision/selection shape is a simplification: the original's model is a hollow picture-frame (open
 * top over most of its footprint, two solid end walls) built for the now-unported radar mesh to render
 * inside of; approximated here as a single box close to the model's overall footprint and height rather
 * than reproducing the frame shape exactly - a shape simplification, not a functional one.
 */
public class BlockDioptra extends Block implements EntityBlock {

	public static final MapCodec<BlockDioptra> CODEC = simpleCodec(BlockDioptra::new);

	private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.875, 1.0);

	public BlockDioptra(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.ENABLED, true));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.ENABLED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide) {
			level.setBlock(pos, state.cycle(BlockStateProperties.ENABLED), 3);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof TileDioptra tile) {
			float r = tile.gridAmount[84] / 64.0f;
			return Mth.floor(r * 14.0f) + (r > 0.0f ? 1 : 0);
		}
		return 0;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileDioptra(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.DIOPTRA.get()
				? (lvl, pos, st, be) -> TileDioptra.tick(lvl, pos, st, (TileDioptra) be)
				: null;
	}
}

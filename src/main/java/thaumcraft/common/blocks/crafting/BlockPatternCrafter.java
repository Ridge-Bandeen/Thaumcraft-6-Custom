package thaumcraft.common.blocks.crafting;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import thaumcraft.common.tiles.crafting.TilePatternCrafter;

/**
 * Replaces the old (1.12.2) {@code BlockPatternCrafter} - see {@link TilePatternCrafter}'s own doc comment
 * for the vis-powered auto-crafting mechanic; clicking its dial ({@link #dialAt}) changes the pattern.
 * {@link BlockStateProperties#HORIZONTAL_FACING}
 * matches the original's own real per-facing model rotation exactly (purely cosmetic now - the tile's real
 * item-handler lookups are fixed to straight up/down, not relative to facing).
 */
public class BlockPatternCrafter extends Block implements EntityBlock {

	public static final MapCodec<BlockPatternCrafter> CODEC = simpleCodec(BlockPatternCrafter::new);

	private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.375, 1.0);

	public BlockPatternCrafter(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH)
				.setValue(BlockStateProperties.ENABLED, true));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.ENABLED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// as the original: the way the placer faces (not the opposite, as most devices)
		return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection());
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

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!(level.getBlockEntity(pos) instanceof TilePatternCrafter tile)) return InteractionResult.PASS;
		// as the original: only the pattern dial on its side turns it
		if (!dialAt(state, pos).clip(player.getEyePosition(), player.getEyePosition().add(player.getViewVector(1.0f).scale(player.blockInteractionRange()))).isPresent()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide) return InteractionResult.SUCCESS;
		tile.cycle();
		level.playSound(null, pos, thaumcraft.common.registry.ThaumcraftSounds.KEY.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
		return InteractionResult.SUCCESS;
	}

	/** The original's getCuboidByFacing: the little dial on the side of the crafter. */
	static net.minecraft.world.phys.AABB dialAt(BlockState state, BlockPos pos) {
		net.minecraft.world.phys.AABB box = switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
			case EAST -> new net.minecraft.world.phys.AABB(0.125, 0.125, 0.375, 0.25, 0.375, 0.625);
			case NORTH -> new net.minecraft.world.phys.AABB(0.375, 0.125, 0.75, 0.625, 0.375, 0.875);
			case SOUTH -> new net.minecraft.world.phys.AABB(0.375, 0.125, 0.125, 0.625, 0.375, 0.25);
			default -> new net.minecraft.world.phys.AABB(0.75, 0.125, 0.375, 0.875, 0.375, 0.625);
		};
		return box.move(pos);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TilePatternCrafter(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.PATTERN_CRAFTER.get()
				? (lvl, p, st, be) -> TilePatternCrafter.tick(lvl, p, st, (TilePatternCrafter) be)
				: null;
	}
}

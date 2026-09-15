package thaumcraft.common.blocks.devices;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.lib.utils.CuboidTargeting;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileLevitator;

/**
 * Replaces the old (1.12.2) {@code BlockLevitator} - a directional vis-powered "wind tunnel" that pushes
 * entities along its facing (see {@link TileLevitator}'s own doc comment for the real push/range mechanic).
 * Same {@code FACING}-from-clicked-face placement convention as {@link BlockArcaneEar}.
 *
 * <p>Real, deliberate re-derivation, not a simplification: the original's own real solid shape (a near-full
 * block, inset by 0.125 only on whichever face it points through - the "wind tunnel opening") and its real
 * small clickable button nub (on the opposite, "control panel" side) are two genuinely different shapes in
 * the true original, kept separate here too - {@link #getShape} is the real solid one (re-derived directly
 * from the original's own {@code getBoundingBox}, one inset face per real facing), while the button click is
 * its own small hitbox, tested independently via {@link CuboidTargeting} inside {@link #useWithoutItem} - the
 * modern, no-third-party-library replacement for the original's CodeChicken raytracer (see that class's own
 * doc comment). An earlier pass here had collapsed both into one made-up narrow-footprint shape that matched
 * neither the real collision box nor the real button position - this restores both for real.
 */
public class BlockLevitator extends Block implements EntityBlock {

	public static final MapCodec<BlockLevitator> CODEC = simpleCodec(BlockLevitator::new);

	private static final float INSET = 0.125f;
	private static final VoxelShape SHAPE_DOWN = Shapes.box(0, 0, 0, 1, 1 - INSET, 1);
	private static final VoxelShape SHAPE_UP = Shapes.box(0, INSET, 0, 1, 1, 1);
	private static final VoxelShape SHAPE_NORTH = Shapes.box(0, 0, 0, 1, 1, 1 - INSET);
	private static final VoxelShape SHAPE_SOUTH = Shapes.box(0, 0, INSET, 1, 1, 1);
	private static final VoxelShape SHAPE_WEST = Shapes.box(0, 0, 0, 1 - INSET, 1, 1);
	private static final VoxelShape SHAPE_EAST = Shapes.box(INSET, 0, 0, 1, 1, 1);

	/** The real small clickable button nub, one per real facing - re-derived verbatim from the true
	 * original's own {@code TileLevitator#getCuboidByFacing} numbers, on the opposite ("control panel") side
	 * from the wind-tunnel opening. */
	private static AABB buttonBox(BlockPos pos, Direction facing) {
		AABB local = switch (facing) {
			case DOWN -> new AABB(0.375, 0.875, 0.375, 0.625, 0.9375, 0.625);
			case EAST -> new AABB(0.0625, 0.375, 0.375, 0.125, 0.625, 0.625);
			case WEST -> new AABB(0.875, 0.375, 0.375, 0.9375, 0.625, 0.625);
			case SOUTH -> new AABB(0.375, 0.375, 0.0625, 0.625, 0.625, 0.125);
			case NORTH -> new AABB(0.375, 0.375, 0.875, 0.625, 0.625, 0.9375);
			default -> new AABB(0.375, 0.0625, 0.375, 0.625, 0.125, 0.625); // UP
		};
		return local.move(pos);
	}

	public BlockLevitator(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(BlockStateProperties.FACING, Direction.UP)
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
		return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(BlockStateProperties.FACING)) {
			case DOWN -> SHAPE_DOWN;
			case UP -> SHAPE_UP;
			case NORTH -> SHAPE_NORTH;
			case SOUTH -> SHAPE_SOUTH;
			case WEST -> SHAPE_WEST;
			case EAST -> SHAPE_EAST;
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		Boolean hitButton = CuboidTargeting.pick(player, List.of(
				new CuboidTargeting.Candidate<>(Boolean.TRUE, buttonBox(pos, state.getValue(BlockStateProperties.FACING)))));
		if (hitButton == null) return InteractionResult.PASS;
		if (level.getBlockEntity(pos) instanceof TileLevitator tile) {
			tile.increaseRange(player);
		}
		return InteractionResult.CONSUME;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileLevitator(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.LEVITATOR.get()
				? (lvl, pos, st, be) -> TileLevitator.tick(lvl, pos, st, (TileLevitator) be)
				: null;
	}
}

package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import thaumcraft.common.tiles.devices.TileArcaneEar;

/**
 * Replaces the old (1.12.2) {@code BlockArcaneEar} and its trivial {@code BlockArcaneEarToggle} subclass -
 * folded into a single class with an {@code isToggle} flag instead, since the only difference was ever which
 * registry name/textures it used (see {@link TileArcaneEar}'s own doc comment for the actual listening
 * mechanic). Attaches to a face like a lever/button - {@code FACING} is set directly from the clicked face
 * at placement (already exactly the semantic this device needs, no translation required), and drops itself
 * if that support is ever removed.
 */
public class BlockArcaneEar extends Block implements EntityBlock {

	public static final MapCodec<BlockArcaneEar> CODEC = simpleCodec(props -> new BlockArcaneEar(props, false));
	public static final MapCodec<BlockArcaneEar> TOGGLE_CODEC = simpleCodec(props -> new BlockArcaneEar(props, true));

	private static final VoxelShape SHAPE = Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.4375, 0.8125);

	private final boolean toggle;

	public BlockArcaneEar(BlockBehaviour.Properties properties, boolean toggle) {
		super(properties);
		this.toggle = toggle;
		registerDefaultState(getStateDefinition().any()
				.setValue(BlockStateProperties.FACING, Direction.UP)
				.setValue(BlockStateProperties.ENABLED, false));
	}

	public boolean isToggle() {
		return toggle;
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return toggle ? TOGGLE_CODEC : CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.FACING, BlockStateProperties.ENABLED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace())
				.setValue(BlockStateProperties.ENABLED, false);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		Direction facing = state.getValue(BlockStateProperties.FACING);
		if (direction == facing.getOpposite() && !canAttachTo(level, pos, facing)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	private boolean canAttachTo(LevelAccessor level, BlockPos pos, Direction facing) {
		BlockPos supportPos = pos.relative(facing.getOpposite());
		return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (level.getBlockEntity(pos) instanceof TileArcaneEar tile) {
			tile.changePitchAndPlay(level, pos, state);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getValue(BlockStateProperties.ENABLED) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return getSignal(state, level, pos, direction);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileArcaneEar(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.ARCANE_EAR.get()
				? (lvl, pos, st, be) -> TileArcaneEar.tick(lvl, pos, st, (TileArcaneEar) be)
				: null;
	}
}

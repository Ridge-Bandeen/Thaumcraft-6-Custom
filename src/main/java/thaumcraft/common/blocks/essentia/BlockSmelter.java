package thaumcraft.common.blocks.essentia;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.essentia.TileSmelter;

/**
 * Replaces the old (1.12.2) {@code BlockSmelter} - the Alchemical Furnace in its three tiers; see {@link TileSmelter}.
 */
public class BlockSmelter extends Block implements EntityBlock {

	public static final MapCodec<BlockSmelter> CODEC = simpleCodec(BlockSmelter::new);

	private static final VoxelShape SHAPE = Shapes.block();

	public BlockSmelter(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
				.setValue(BlockStateProperties.LIT, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileSmelter(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.SMELTER.get()
				? (lvl, pos, st, be) -> TileSmelter.tick(lvl, pos, st, (TileSmelter) be)
				: null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return openMenu(level, pos, player);
	}

	@Override
	protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		return openMenu(level, pos, player) == InteractionResult.SUCCESS
				? net.minecraft.world.ItemInteractionResult.SUCCESS
				: net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	/** The furnace screen (fuel/cook/vis gauges) opens on right-click, as in the original - the port had used
	 * right-click item routing instead, which lost the gauges. */
	private InteractionResult openMenu(Level level, BlockPos pos, Player player) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (player.isShiftKeyDown()) return InteractionResult.SUCCESS; // as the original: not while sneaking
		if (!(level.getBlockEntity(pos) instanceof TileSmelter tile)) return InteractionResult.PASS;
		player.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(id, inv, p) -> new thaumcraft.common.container.SmelterMenu(id, inv, tile),
				net.minecraft.network.chat.Component.translatable(getDescriptionId())));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileSmelter tile) {
			if (tile.aspects.visSize() > 0) {
				AuraHelper.polluteAura(level, pos, tile.aspects.visSize(), true);
			}
			net.minecraft.world.Containers.dropContents(level, pos, tile);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(BlockStateProperties.LIT) ? 13 : 0;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof TileSmelter tile ? AbstractContainerMenu.getRedstoneSignalFromContainer(tile) : 0;
	}

	/** Event 1: a vent took a unit of flux - the original's hiss and four puffs of grey steam out of it. */
	@Override
	protected boolean triggerEvent(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
			net.minecraft.core.BlockPos pos, int id, int param) {
		if (id == 1) {
			if (level.isClientSide) {
				thaumcraft.client.fx.ClientFxHandlers.smelterVent(level, pos, net.minecraft.core.Direction.from3DDataValue(param).getOpposite());
			}
			return true;
		}
		return super.triggerEvent(state, level, pos, id, param);
	}
}

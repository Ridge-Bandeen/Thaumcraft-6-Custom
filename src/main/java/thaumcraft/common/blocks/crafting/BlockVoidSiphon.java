package thaumcraft.common.blocks.crafting;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import thaumcraft.common.tiles.crafting.TileVoidSiphon;

/**
 * Replaces the old (1.12.2) {@code BlockVoidSiphon} - see {@link TileVoidSiphon}'s own doc comment for the
 * rift-draining mechanic and what's simplified. Not a full cube (matches the original's slim
 * base/stem/orb shape closely enough for collision/selection), no facing needed.
 */
public class BlockVoidSiphon extends Block implements EntityBlock {

	public static final MapCodec<BlockVoidSiphon> CODEC = simpleCodec(BlockVoidSiphon::new);

	private static final VoxelShape SHAPE = Shapes.or(
			Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.125, 0.8125),
			Shapes.box(0.25, 0.125, 0.25, 0.75, 0.6875, 0.75),
			Shapes.box(0.3125, 0.75, 0.3125, 0.625, 1.0, 0.625))
			.optimize();

	public BlockVoidSiphon(BlockBehaviour.Properties properties) {
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
		return new TileVoidSiphon(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.VOID_SIPHON.get()
				? (lvl, pos, st, be) -> TileVoidSiphon.tick(lvl, pos, st, (TileVoidSiphon) be)
				: null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (!(level.getBlockEntity(pos) instanceof TileVoidSiphon tile)) return InteractionResult.PASS;

		// Opens the output screen, like the original (the port's stand-in emptied the slot into your inventory).
		player.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(id, inv, p) -> new thaumcraft.common.container.VoidSiphonMenu(id, inv, tile),
				net.minecraft.network.chat.Component.translatable(getDescriptionId())), buf -> buf.writeBlockPos(pos));
		return InteractionResult.SUCCESS;
	}

	/** As the original (BlockTCTile#breakBlock): whatever it holds drops when the block goes. */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof thaumcraft.common.tiles.crafting.TileVoidSiphon tile) {
			var items = tile.getItemHandler();
			for (int i = 0; i < items.getSlots(); i++) {
				net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStackInSlot(i).copy());
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}

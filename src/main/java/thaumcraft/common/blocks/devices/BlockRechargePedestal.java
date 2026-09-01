package thaumcraft.common.blocks.devices;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileRechargePedestal;

/**
 * Replaces the old (1.12.2) {@code BlockRechargePedestal}. See {@link TileRechargePedestal}'s doc comment
 * for why this doesn't extend a shared {@code BlockTCDevice} base (fully superseded by picking the right
 * vanilla base class directly - this block needs neither facing nor a redstone-enabled state, unlike some
 * other devices, so it's plain {@link Block}). No GUI - right-click directly places/removes the held item,
 * same as the original.
 */
public class BlockRechargePedestal extends Block implements EntityBlock {

	public static final MapCodec<BlockRechargePedestal> CODEC = simpleCodec(BlockRechargePedestal::new);

	// Approximates the block model's stepped-pyramid shape (base/middle/top tiers) closely enough for
	// collision/selection - not a pixel-perfect match to the model's small gem accents.
	private static final VoxelShape SHAPE = Shapes.or(
			Shapes.box(0, 0, 0, 1, 0.25, 1),
			Shapes.box(0.125, 0.25, 0.125, 0.875, 0.5, 0.875),
			Shapes.box(0.25, 0.5, 0.25, 0.75, 1, 0.75));

	public BlockRechargePedestal(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileRechargePedestal(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.RECHARGE_PEDESTAL.get()
				? (lvl, pos, st, be) -> TileRechargePedestal.tick(lvl, pos, st, (TileRechargePedestal) be)
				: null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return interact(state, level, pos, player, ItemStack.EMPTY);
	}

	@Override
	protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		InteractionResult result = interact(state, level, pos, player, stack);
		return result == InteractionResult.SUCCESS ? net.minecraft.world.ItemInteractionResult.SUCCESS
				: net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileRechargePedestal tile) {
			ItemStack held = tile.getItem();
			if (!held.isEmpty()) {
				net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, ItemStack held) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (!(level.getBlockEntity(pos) instanceof TileRechargePedestal tile)) return InteractionResult.PASS;

		if (tile.getItem().isEmpty() && tile.accepts(held)) {
			ItemStack single = held.copyWithCount(1);
			tile.setItem(single);
			held.shrink(1);
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f,
					((level.random.nextFloat() - level.random.nextFloat()) * 0.7f + 1.0f) * 1.6f);
			return InteractionResult.SUCCESS;
		}
		if (!tile.getItem().isEmpty()) {
			ItemStack returned = tile.getItem();
			tile.setItem(ItemStack.EMPTY);
			if (!player.getInventory().add(returned)) {
				player.drop(returned, false);
			}
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f,
					((level.random.nextFloat() - level.random.nextFloat()) * 0.7f + 1.0f) * 1.5f);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}

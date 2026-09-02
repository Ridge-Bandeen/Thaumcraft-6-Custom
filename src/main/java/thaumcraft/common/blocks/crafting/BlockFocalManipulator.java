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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.crafting.TileFocalManipulator;

/**
 * Replaces the old (1.12.2) {@code BlockFocalManipulator}. See {@link TileFocalManipulator}'s own doc
 * comment for the device's real purpose and what this pass covers. No custom {@code BlockTCDevice} base,
 * same "pick the right vanilla base directly" precedent {@link thaumcraft.common.blocks.devices.BlockRechargePedestal}
 * already established - no facing/redstone state needed here either.
 *
 * No GUI yet (see {@code TileFocalManipulator}'s doc comment for the rest of the arc this feeds into) -
 * right-click for now just deposits/withdraws the held focus item, same convenience
 * {@code BlockRechargePedestal} already offers, so the block is genuinely useful the moment it's placed
 * even before the node-graph screen exists. The original's own non-directional, non-full-cube shape (no
 * custom voxel data of its own beyond opacity flags) is approximated with a flat pedestal box.
 */
public class BlockFocalManipulator extends Block implements EntityBlock {

	public static final MapCodec<BlockFocalManipulator> CODEC = simpleCodec(BlockFocalManipulator::new);

	private static final VoxelShape SHAPE = Shapes.box(0.0625, 0, 0.0625, 0.9375, 0.75, 0.9375);

	public BlockFocalManipulator(BlockBehaviour.Properties properties) {
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
		return new TileFocalManipulator(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.FOCAL_MANIPULATOR.get()
				? (lvl, pos, st, be) -> TileFocalManipulator.tick(lvl, pos, st, (TileFocalManipulator) be)
				: null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return interact(level, pos, player, ItemStack.EMPTY);
	}

	@Override
	protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		InteractionResult result = interact(level, pos, player, stack);
		return result == InteractionResult.SUCCESS ? net.minecraft.world.ItemInteractionResult.SUCCESS
				: net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileFocalManipulator tile) {
			ItemStack held = tile.getFocus();
			if (!held.isEmpty()) {
				net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	private InteractionResult interact(Level level, BlockPos pos, Player player, ItemStack held) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (!(level.getBlockEntity(pos) instanceof TileFocalManipulator tile)) return InteractionResult.PASS;

		if (tile.getFocus().isEmpty() && !held.isEmpty() && tile.getItemHandler().isItemValid(0, held)) {
			ItemStack single = held.copyWithCount(1);
			tile.setFocus(single);
			held.shrink(1);
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f, 1.6f);
			return InteractionResult.SUCCESS;
		}
		if (!tile.getFocus().isEmpty()) {
			ItemStack returned = tile.getFocus();
			tile.setFocus(ItemStack.EMPTY);
			if (!player.getInventory().add(returned)) {
				player.drop(returned, false);
			}
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f, 1.5f);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}

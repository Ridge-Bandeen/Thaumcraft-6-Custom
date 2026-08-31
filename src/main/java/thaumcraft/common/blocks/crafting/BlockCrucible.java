package thaumcraft.common.blocks.crafting;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.tiles.crafting.TileCrucible;

/**
 * Replaces the old (1.12.2) {@code BlockCrucible}. See {@link TileCrucible}'s doc for what's simplified
 * this pass. Base class is vanilla {@code Block} directly ({@code BlockTCTile}, the original's shared base,
 * isn't ported).
 */
public class BlockCrucible extends Block implements EntityBlock {

	public static final MapCodec<BlockCrucible> CODEC = simpleCodec(BlockCrucible::new);

	private static final VoxelShape SHAPE = Shapes.or(
			Shapes.box(0, 0, 0, 1, 0.3125, 1),
			Shapes.box(0, 0, 0, 0.125, 1, 1),
			Shapes.box(0.875, 0, 0, 1, 1, 1),
			Shapes.box(0, 0, 0, 1, 1, 0.125),
			Shapes.box(0, 0, 0.875, 1, 1, 1)
	);

	public BlockCrucible(BlockBehaviour.Properties properties) {
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
		return new TileCrucible(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == thaumcraft.common.registry.ThaumcraftBlockEntities.CRUCIBLE.get()
				? (lvl, pos, st, be) -> TileCrucible.tick(lvl, pos, st, (TileCrucible) be)
				: null;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
		if (!level.isClientSide && entity instanceof ItemEntity itemEntity
				&& level.getBlockEntity(pos) instanceof TileCrucible tile && tile.isActive()) {
			tile.tryConsume(itemEntity);
		}
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
		if (!(level.getBlockEntity(pos) instanceof TileCrucible tile)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if (stack.is(Items.WATER_BUCKET) && !tile.hasWater) {
			tile.hasWater = true;
			tile.setChanged();
			if (!player.getAbilities().instabuild) {
				player.setItemInHand(hand, new ItemStack(Items.BUCKET));
			}
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.5f, 1.0f);
			return ItemInteractionResult.SUCCESS;
		}

		if (stack.is(Items.BUCKET) && tile.hasWater && tile.aspects.size() == 0) {
			tile.hasWater = false;
			tile.setChanged();
			if (!player.getAbilities().instabuild) {
				player.setItemInHand(hand, new ItemStack(Items.WATER_BUCKET));
			}
			level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.5f, 1.0f);
			return ItemInteractionResult.SUCCESS;
		}

		if (!stack.isEmpty() && hitResult.getDirection() == Direction.UP && tile.isActive()) {
			ItemStack single = stack.copyWithCount(1);
			ItemStack leftover = tile.attemptSmelt(single, level);
			if (leftover.isEmpty()) {
				stack.shrink(1);
				return ItemInteractionResult.SUCCESS;
			}
		}

		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide && player.isCrouching() && level.getBlockEntity(pos) instanceof TileCrucible tile) {
			tile.spillRemnants(level);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileCrucible tile) {
			tile.spillRemnants(level);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof TileCrucible tile ? tile.getComparatorSignal() : 0;
	}
}

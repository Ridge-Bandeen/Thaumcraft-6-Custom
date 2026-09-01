package thaumcraft.common.blocks.devices;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Replaces the old (1.12.2) {@code BlockHungryChest} - a chest that never links into a double chest and
 * auto-consumes dropped items walking over it, otherwise a completely normal chest. Extends vanilla's own
 * {@link ChestBlock} directly rather than reimplementing a chest from scratch (reusing its real GUI,
 * lid-open animation, comparator output, and contents-drop-on-break for free) - registered under its own
 * {@link BlockEntityType} ({@code thaumcraft.common.tiles.devices.TileHungryChest}, a thin visibility shim
 * over vanilla's own {@link ChestBlockEntity} adding no real behavior - see that class's own doc comment)
 * rather than vanilla's {@code BlockEntityType.CHEST}, both so the two never visually/logically merge and
 * to keep this project's "every block/tile pair gets its own registry entry" convention. Note that
 * {@link ChestBlock#newBlockEntity} itself is hardcoded to build a plain {@code ChestBlockEntity(pos,
 * state)} - it never actually consults its own injected {@code blockEntityType} supplier at construction
 * time (that field is only read by {@code getTicker}/{@code combine}/{@code blockEntityType()}) - found by
 * actually running the GameTest and getting "Invalid block entity minecraft:chest" despite the supplier
 * being wired correctly, not by inspection. {@link #newBlockEntity} is overridden here for that reason.
 *
 * The original suppressed merging by overriding {@code TileEntityChest#checkForAdjacentChests} to always
 * report "already checked" without actually scanning - the direct modern equivalent is overriding the two
 * places {@link ChestBlock} itself decides to merge ({@link #getStateForPlacement}/{@link #updateShape}) to
 * never produce anything but {@link ChestType#SINGLE}, rather than relying on being a distinct registered
 * Block instance (which alone would still let two Hungry Chests merge with each other).
 *
 * Simplified vs the original: no lid-open animation - the block model is a plain static populated model
 * (the original's own resting-pose asset, {@code models/block/chest.json}) rendered normally instead of
 * through vanilla's chest-specific {@code BlockEntityRenderer}, same "drop the animation, keep the
 * function" precedent as the Bellows/most other devices this session. Item insertion on collision is a
 * simple manual merge-then-empty-slot loop, not a faithful port of {@code ThaumcraftInvHelper.insertStackAt}
 * (itself part of the now-superseded {@code InventoryUtils} grab-bag).
 */
public class BlockHungryChest extends ChestBlock {

	public static final MapCodec<BlockHungryChest> CODEC = simpleCodec(
			props -> new BlockHungryChest(props, () -> thaumcraft.common.registry.ThaumcraftBlockEntities.HUNGRY_CHEST.get()));

	public BlockHungryChest(BlockBehaviour.Properties properties, Supplier<BlockEntityType<? extends ChestBlockEntity>> blockEntityType) {
		super(properties, blockEntityType);
	}

	@Override
	public MapCodec<? extends ChestBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, facing).setValue(TYPE, ChestType.SINGLE)
				.setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == net.minecraft.world.level.material.Fluids.WATER);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new thaumcraft.common.tiles.devices.TileHungryChest(pos, state);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		// Deliberately does not call super - never re-evaluates into a double chest after placement either.
		return state;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (!level.isClientSide && entity instanceof ItemEntity itemEntity && itemEntity.isAlive()
				&& level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			ItemStack leftover = insertIntoChest(chest, itemEntity.getItem().copy());
			boolean atePart = leftover.getCount() != itemEntity.getItem().getCount();
			if (leftover.isEmpty()) {
				itemEntity.discard();
			} else {
				itemEntity.setItem(leftover);
			}
			if (atePart) {
				level.playSound(null, pos, SoundEvents.GENERIC_EAT, net.minecraft.sounds.SoundSource.BLOCKS, 0.25f,
						(level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2f + 1.0f);
				chest.setChanged();
			}
		}
	}

	private static ItemStack insertIntoChest(ChestBlockEntity chest, ItemStack stack) {
		for (int i = 0; i < chest.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack slot = chest.getItem(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
				int room = slot.getMaxStackSize() - slot.getCount();
				if (room > 0) {
					int moved = Math.min(room, stack.getCount());
					slot.grow(moved);
					stack.shrink(moved);
				}
			}
		}
		for (int i = 0; i < chest.getContainerSize() && !stack.isEmpty(); i++) {
			if (chest.getItem(i).isEmpty()) {
				int moved = Math.min(stack.getMaxStackSize(), stack.getCount());
				chest.setItem(i, stack.copyWithCount(moved));
				stack.shrink(moved);
			}
		}
		return stack;
	}
}

package thaumcraft.common.blocks.crafting;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.crafting.TileThaumatorium;

/**
 * Replaces the old (1.12.2) {@code BlockThaumatorium} - see {@link TileThaumatorium}'s own doc comment for
 * the auto-crafting mechanic and what's simplified (single standalone block, no GUI, no Brain-in-a-Jar, no
 * required-crucible-below structure or cosmetic "top" block). Full cube, no facing needed. Right-click
 * inserts/removes the catalyst item directly, same no-menu convention as
 * {@link thaumcraft.common.blocks.devices.BlockPotionSprayer}/{@link BlockVoidSiphon}.
 *
 * Texture note: the original's real look came from a hand-modeled {@code .obj} mesh with its own UV atlas
 * (see {@code thaumatorium.png}/{@code .mtl}/{@code .obj} still sitting in assets, unused). Reconstructing
 * that mesh as vanilla block-model elements is real, separate work (unlike {@code BlockInfernalFurnace}/
 * {@code BlockVoidSiphon}, nobody has done that reconstruction for this block yet) - not worth blocking this
 * chunk on, so this reuses {@link BlockCrucible}'s own per-face textures (thematically the closest existing
 * device: another heat-fired essentia vessel) as a plain cube for now.
 */
public class BlockThaumatorium extends Block implements EntityBlock {

	public static final MapCodec<BlockThaumatorium> CODEC = simpleCodec(BlockThaumatorium::new);

	public BlockThaumatorium(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileThaumatorium(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.THAUMATORIUM.get()
				? (lvl, pos, st, be) -> TileThaumatorium.tick(lvl, pos, st, (TileThaumatorium) be)
				: null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return interact(level, pos, player, ItemStack.EMPTY);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		InteractionResult result = interact(level, pos, player, stack);
		return result == InteractionResult.SUCCESS ? ItemInteractionResult.SUCCESS
				: ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	private InteractionResult interact(Level level, BlockPos pos, Player player, ItemStack held) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (!(level.getBlockEntity(pos) instanceof TileThaumatorium tile)) return InteractionResult.PASS;

		if (tile.getItem().isEmpty() && tile.accepts(held)) {
			ItemStack single = held.copyWithCount(1);
			tile.setItem(single);
			held.shrink(1);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.4f, 1.0f);
			return InteractionResult.SUCCESS;
		}
		if (!tile.getItem().isEmpty()) {
			ItemStack returned = tile.getItem();
			tile.setItem(ItemStack.EMPTY);
			if (!player.getInventory().add(returned)) {
				player.drop(returned, false);
			}
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.4f, 1.0f);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileThaumatorium tile) {
			ItemStack held = tile.getItem();
			if (!held.isEmpty()) {
				Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}

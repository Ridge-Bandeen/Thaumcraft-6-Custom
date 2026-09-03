package thaumcraft.common.blocks.crafting;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
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
import thaumcraft.common.tiles.crafting.TileResearchTable;

/**
 * Replaces the old (1.12.2) {@code BlockResearchTable}. See {@link TileResearchTable}'s own doc comment for
 * the device's scope this pass. No custom {@code BlockTCDevice} base, same "pick the right vanilla base
 * directly" precedent every device this session has used.
 *
 * Real, documented cut: the original was a real 4-way {@code IBlockFacingHorizontal} block (faces the
 * placing player); this ships non-directional for now - purely cosmetic (nothing in the real minigame logic
 * reads the table's facing), same class of simplification already made for other devices' rotation-only
 * cosmetics. Right-click opens the real {@link thaumcraft.common.container.ResearchTableMenu} now (same
 * {@code player.openMenu(tile)} pattern every menu-backed device this session uses) - still no screen (see
 * the tile's own arc notes for the rest of this arc).
 */
public class BlockResearchTable extends Block implements EntityBlock {

	public static final MapCodec<BlockResearchTable> CODEC = simpleCodec(BlockResearchTable::new);

	private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 0.875, 1);

	public BlockResearchTable(BlockBehaviour.Properties properties) {
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
		return new TileResearchTable(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof TileResearchTable tile) {
			player.openMenu(tile);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileResearchTable tile) {
			for (int slot = 0; slot < tile.getItemHandler().getSlots(); slot++) {
				ItemStack held = tile.getItemHandler().getStackInSlot(slot);
				if (!held.isEmpty()) {
					net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
				}
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}

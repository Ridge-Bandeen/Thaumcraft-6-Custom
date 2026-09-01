package thaumcraft.common.blocks.essentia;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.essentia.TileTube;
import thaumcraft.common.tiles.essentia.TileTubeFilter;

/**
 * Replaces the old (1.12.2) {@code BlockTubeFilter} - see {@link TileTubeFilter}'s own doc comment for the
 * aspect-lock mechanic. Right-click with a filled essentia-container item (Phial, Crystal Essence) to set
 * the filter to that item's aspect; sneak+empty-hand clears it - the exact same interaction shape as the
 * Warded Jar Label mechanic, reused here for a tube instead.
 */
public class BlockTubeFilter extends BlockTube {

	public static final MapCodec<BlockTubeFilter> CODEC = simpleCodec(BlockTubeFilter::new);

	public BlockTubeFilter(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
		if (!(level.getBlockEntity(pos) instanceof TileTubeFilter tile)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if (player.isShiftKeyDown() && stack.isEmpty()) {
			tile.setAspectFilter(null);
			return ItemInteractionResult.SUCCESS;
		}
		if (stack.getItem() instanceof IEssentiaContainerItem container) {
			AspectList held = container.getAspects(stack);
			if (held != null && held.size() > 0) {
				tile.setAspectFilter(held.getAspectsSortedByAmount()[0]);
				return ItemInteractionResult.SUCCESS;
			}
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileTubeFilter(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.TUBE_FILTER.get()
				? (lvl, p, st, be) -> TileTube.tick(lvl, p, st, (TileTube) be)
				: null;
	}
}

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
import thaumcraft.common.tiles.crafting.TileGolemBuilder;

/**
 * Replaces the old (1.12.2) {@code BlockGolemBuilder}. See {@link TileGolemBuilder}'s own doc comment for
 * the device's real crafting logic.
 *
 * Real, documented cut: the original wasn't a real block at all - an invisible {@code BlockPistonBase}
 * shell wrapping a 3x2x3 multi-block of decorative placeholder blocks (bars/anvil/cauldron/table, each
 * reverting to its real vanilla equivalent on break via {@code BlockGolemBuilder.destroy}), built on
 * CodeChicken-lib rendering this port has never depended on for any device. This ships as one real, solid,
 * directly-interactable block instead - same "pick the right vanilla base directly, drop the multi-block
 * illusion" simplification every device this session has made for decorative/structural complexity, while
 * keeping the actual crafting mechanic (essentia-driven, real component cost) fully real. Right-click opens
 * the real {@link thaumcraft.common.container.GolemBuilderMenu}.
 */
public class BlockGolemBuilder extends Block implements EntityBlock {

	public static final MapCodec<BlockGolemBuilder> CODEC = simpleCodec(BlockGolemBuilder::new);

	private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 1, 1);

	public BlockGolemBuilder(BlockBehaviour.Properties properties) {
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

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileGolemBuilder(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.GOLEM_BUILDER.get()
				? (lvl, pos, st, be) -> TileGolemBuilder.tick(lvl, pos, st, (TileGolemBuilder) be)
				: null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof TileGolemBuilder tile) {
			player.openMenu(tile);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileGolemBuilder tile) {
			ItemStack held = tile.getItemHandler().getStackInSlot(0);
			if (!held.isEmpty()) {
				net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}

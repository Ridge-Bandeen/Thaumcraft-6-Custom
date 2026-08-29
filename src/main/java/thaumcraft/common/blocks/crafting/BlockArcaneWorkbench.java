package thaumcraft.common.blocks.crafting;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;

/**
 * Replaces the old (1.12.2) {@code BlockArcaneWorkbench} (which extended a shared {@code BlockTCDevice}
 * base - not ported yet, so this extends vanilla {@code Block} directly for now). Right-clicking opens the
 * tile entity's menu directly instead of going through Forge's old numeric-id {@code player.openGui(...)}
 * system, which doesn't exist anymore.
 */
public class BlockArcaneWorkbench extends Block implements EntityBlock {

	public static final MapCodec<BlockArcaneWorkbench> CODEC = simpleCodec(BlockArcaneWorkbench::new);

	private static final VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.box(0, 0, 0, 1, 0.875, 1);

	public BlockArcaneWorkbench(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileArcaneWorkbench(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof TileArcaneWorkbench workbench) {
			player.openMenu(workbench);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			if (level.getBlockEntity(pos) instanceof TileArcaneWorkbench workbench) {
				net.minecraft.world.Containers.dropContents(level, pos, workbench);
			}
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}

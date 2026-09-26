package thaumcraft.common.blocks.crafting;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.api.blocks.BlocksTC;

/**
 * The upper half of the Thaumatorium ({@code BlockThaumatorium(top = true)} in the original): invisible - the lower
 * block's two-block-tall mesh draws it - but solid, opens the lower block's GUI when clicked, and takes the whole
 * construct back to Metal Alchemical when broken. Restored 2026-09-25 (it had been a plain cube).
 */
public class BlockThaumatoriumTop extends Block {

	public static final MapCodec<BlockThaumatoriumTop> CODEC = simpleCodec(BlockThaumatoriumTop::new);

	public BlockThaumatoriumTop(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		BlockPos below = pos.below();
		BlockState body = level.getBlockState(below);
		return body.is(BlocksTC.thaumatorium.get()) ? body.useWithoutItem(level, player, hitResult.withPosition(below)) : InteractionResult.PASS;
	}

	@Override
	protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		BlockState body = level.getBlockState(pos.below());
		return body.is(BlocksTC.thaumatorium.get())
				? body.useItemOn(stack, level, player, hand, hitResult.withPosition(pos.below()))
				: net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockState(pos.below()).is(BlocksTC.thaumatorium.get())) {
			level.setBlockAndUpdate(pos.below(), BlocksTC.metalAlchemical.get().defaultBlockState());
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}

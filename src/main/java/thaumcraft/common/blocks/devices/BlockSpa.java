package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.fluids.FluidUtil;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileSpa;

/**
 * Replaces the old (1.12.2) {@code BlockSpa} - see {@link TileSpa}'s own doc comment for the fluid-tank/
 * auto-placer mechanic and what's simplified. Right-clicking with a real fluid container fills the tank (via
 * NeoForge's own {@code FluidUtil}, same real pattern {@link BlockWaterJug} already established); any other
 * right-click opens the real menu (same real {@code player.openMenu(tile)} pattern
 * {@link thaumcraft.common.blocks.crafting.BlockGolemBuilder} already established).
 */
public class BlockSpa extends Block implements EntityBlock {

	public static final MapCodec<BlockSpa> CODEC = simpleCodec(BlockSpa::new);

	public BlockSpa(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
		if (!(level.getBlockEntity(pos) instanceof TileSpa tile)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if (FluidUtil.interactWithFluidHandler(player, hand, tile.tank)) {
			return ItemInteractionResult.SUCCESS;
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (level.getBlockEntity(pos) instanceof TileSpa tile) {
			player.openMenu(tile);
		}
		return InteractionResult.CONSUME;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileSpa(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.SPA.get()
				? (lvl, pos, st, be) -> TileSpa.tick(lvl, pos, st, (TileSpa) be)
				: null;
	}
}

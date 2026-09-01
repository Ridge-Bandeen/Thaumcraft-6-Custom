package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
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
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileWaterJug;

/**
 * Replaces the old (1.12.2) {@code BlockWaterJug} ("Everfull Urn") - see {@link TileWaterJug}'s own doc
 * comment for what's simplified. Bucket interaction goes through NeoForge's own {@code FluidUtil} (handles
 * the bucket empty/fill swap automatically, same as the original's own usage of it); glass bottle -> water
 * bottle is a direct special case (vanilla glass bottles aren't {@code IFluidHandler} items, same reason the
 * original needed its own special case here too).
 */
public class BlockWaterJug extends Block implements EntityBlock {

	public static final MapCodec<BlockWaterJug> CODEC = simpleCodec(BlockWaterJug::new);

	private static final VoxelShape SHAPE = Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.625, 0.8125);

	public BlockWaterJug(BlockBehaviour.Properties properties) {
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
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
		if (!(level.getBlockEntity(pos) instanceof TileWaterJug tile)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

		if (FluidUtil.interactWithFluidHandler(player, hand, tile.tank)) {
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.33f, 1.0f);
			return ItemInteractionResult.SUCCESS;
		}

		if (stack.is(Items.GLASS_BOTTLE) && tile.tank.getFluidAmount() >= 333) {
			tile.tank.drain(333, IFluidHandler.FluidAction.EXECUTE);
			ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			if (stack.isEmpty()) {
				player.setItemInHand(hand, waterBottle);
			} else if (!player.getInventory().add(waterBottle)) {
				player.drop(waterBottle, false);
			}
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.33f, 1.0f);
			return ItemInteractionResult.SUCCESS;
		}

		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileWaterJug(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.WATER_JUG.get()
				? (lvl, pos, st, be) -> TileWaterJug.tick(lvl, pos, st, (TileWaterJug) be)
				: null;
	}
}

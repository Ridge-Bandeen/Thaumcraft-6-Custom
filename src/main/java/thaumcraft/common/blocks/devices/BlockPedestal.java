package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.crafting.IInfusionStabiliserExt;
import thaumcraft.common.tiles.crafting.TilePedestal;

/**
 * Replaces the old (1.12.2) {@code BlockPedestal}. Right-click with an item to place it (one at a time),
 * right-click empty-handed (or with a different item) to take it back out. See {@link TilePedestal}'s own
 * doc comment for what's simplified this pass - no floating-item render chief among them.
 *
 * Still implements {@link IInfusionStabiliserExt} - pedestals were always part of the infusion altar's own
 * symmetry-stability scan (see {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix#getSurroundings}),
 * not a separate stabilizer block, so this piece survives even though the other stabilizer blocks
 * ({@code BlockPillar} tiers, {@code TileStabilizer}, decorative candles/skulls-as-stabilizers) mostly don't
 * exist yet. Only the plain "vanilla skull block" case of that scan is also kept - see the altar's own scan
 * code.
 */
public class BlockPedestal extends Block implements EntityBlock, IInfusionStabiliserExt {

	public static final MapCodec<BlockPedestal> CODEC = simpleCodec(BlockPedestal::new);

	private static final VoxelShape SHAPE = Shapes.or(
			Shapes.box(0, 0, 0, 1, 0.25, 1),
			Shapes.box(0.125, 0.75, 0.125, 0.875, 1, 0.875),
			Shapes.box(0.25, 0.25, 0.25, 0.75, 0.75, 0.75)
	);

	public BlockPedestal(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(BlockInlay.CHARGE, 0));
	}

	/** As the original: a pedestal is part of the Redstone Inlay network and carries its charge (see {@link BlockInlay}). */
	@Override
	protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockInlay.CHARGE);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!level.isClientSide && !oldState.is(state.getBlock())) {
			BlockInlay.chargeChanged(level, pos);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
		if (!level.isClientSide) {
			BlockInlay.updateSurroundingInlay(level, pos, state);
		}
	}

	/** The original's offering cue: a charged pedestal shows red runes. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
		if (state.getValue(BlockInlay.CHARGE) > 0) {
			thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes2(level, pos.getX(), pos.getY() - 0.375, pos.getZ(), 1.0f, 0.0f, 0.0f, 10, 0.0f);
		}
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
		return new TilePedestal(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return interact(level, pos, player, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
	}

	@Override
	protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		InteractionResult result = interact(level, pos, player, stack, hand);
		return result == InteractionResult.SUCCESS ? net.minecraft.world.ItemInteractionResult.SUCCESS
				: net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	private InteractionResult interact(Level level, BlockPos pos, Player player, ItemStack stack, InteractionHand hand) {
		if (!(level.getBlockEntity(pos) instanceof TilePedestal pedestal)) return InteractionResult.PASS;
		if (level.isClientSide) return InteractionResult.SUCCESS;

		if (pedestal.getItem().isEmpty()) {
			if (stack.isEmpty()) return InteractionResult.PASS;
			ItemStack single = stack.copyWithCount(1);
			pedestal.setItem(single);
			stack.shrink(1);
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f, ((level.random.nextFloat() - level.random.nextFloat()) * 0.7f + 1.0f) * 1.6f);
			return InteractionResult.SUCCESS;
		}

		if (!player.getInventory().add(pedestal.getItem())) {
			player.drop(pedestal.getItem(), false);
		}
		pedestal.setItem(ItemStack.EMPTY);
		level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2f, ((level.random.nextFloat() - level.random.nextFloat()) * 0.7f + 1.0f) * 1.5f);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TilePedestal pedestal) {
			pedestal.dropContents(level);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
		if (!level.isClientSide && !state.is(newState.getBlock())) {
			BlockInlay.chargeRemoved(level, pos, state, this);
		}
	}

	@Override
	public boolean canStabaliseInfusion(Level world, BlockPos pos) {
		return true;
	}

	@Override
	public float getStabilizationAmount(Level world, BlockPos pos) {
		return 0.0f;
	}

	@Override
	public boolean hasSymmetryPenalty(Level world, BlockPos pos1, BlockPos pos2) {
		return (world.getBlockEntity(pos1) instanceof TilePedestal p1) && (world.getBlockEntity(pos2) instanceof TilePedestal p2)
				&& p1.getItem().isEmpty() != p2.getItem().isEmpty();
	}

	@Override
	public float getSymmetryPenalty(Level world, BlockPos pos) {
		return 0.1f;
	}
}

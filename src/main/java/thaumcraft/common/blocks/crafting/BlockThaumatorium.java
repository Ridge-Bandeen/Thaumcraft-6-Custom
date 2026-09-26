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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import thaumcraft.api.blocks.BlocksTC;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.crafting.TileThaumatorium;

/**
 * Replaces the old (1.12.2) {@code BlockThaumatorium} - see {@link TileThaumatorium}'s own doc comment for
 * the auto-crafting mechanic. Right-click opens its curation GUI ({@link thaumcraft.common.container.ThaumatoriumMenu}).
 *
 * <b>Restored 2026-09-25:</b> the original's real hand-modelled mesh (two blocks tall: this block plus the invisible
 * {@link BlockThaumatoriumTop} above it - the placeholder crucible-textured cubes it was standing in with looked like
 * two more crucibles in a playtest), its horizontal facing (turned to face the player by the Salis Mundus ritual), and
 * its self-protection: without a crucible directly below, both halves revert to Metal Alchemical.
 *
 * <b>Real acquisition path restored</b> (see {@code ThaumcraftDustTriggers#thaumatoriumBlueprint}): this had
 * no real recipe of any kind for most of this session (not even an invented substitute - a genuine, confirmed
 * gap, not a deliberate cut). The real path is the original's own real ritual: a real {@code crucible} placed
 * below, a real {@code metalAlchemical} block placed on top of that, right-click with Salis Mundus - the
 * {@code metalAlchemical} becomes this block. {@code metalAlchemical} itself is really craftable now too
 * (see its own doc comment) once {@code tubeValve} was ported to supply its one missing ingredient.
 *
 */
public class BlockThaumatorium extends Block implements EntityBlock {

	public static final MapCodec<BlockThaumatorium> CODEC = simpleCodec(BlockThaumatorium::new);

	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	public BlockThaumatorium(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(FACING, net.minecraft.core.Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	/** The original's own guard: no crucible underneath means the whole construct falls back to its raw metal blocks. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		if (!level.isClientSide && !level.getBlockState(pos.below()).is(BlocksTC.crucible.get())) {
			level.setBlockAndUpdate(pos, BlocksTC.metalAlchemical.get().defaultBlockState());
			if (level.getBlockState(pos.above()).is(BlocksTC.thaumatoriumTop.get())) {
				level.setBlockAndUpdate(pos.above(), BlocksTC.metalAlchemical.get().defaultBlockState());
			}
		}
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
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

		// Opens the curation GUI, like the original (its old stand-in inserted/removed the catalyst on right-click).
		player.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(id, inv, p) -> new thaumcraft.common.container.ThaumatoriumMenu(id, inv, tile),
				net.minecraft.network.chat.Component.translatable("block.thaumcraft.thaumatorium")), buf -> buf.writeBlockPos(pos));
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileThaumatorium tile) {
			ItemStack held = tile.getItem();
			if (!held.isEmpty()) {
				Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
			}
		}
		if (!state.is(newState.getBlock()) && level.getBlockState(pos.above()).is(BlocksTC.thaumatoriumTop.get())) {
			level.setBlockAndUpdate(pos.above(), BlocksTC.metalAlchemical.get().defaultBlockState());
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}

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
 * As the original: made by a Salis Mundus ritual (ThaumcraftDustTriggers#golemPressBlueprint) out of a piston with
 * iron bars above and a cauldron, anvil and stone table beside it. This block takes the piston's place, faces the way
 * the ritual found the structure (so its model, TileGolemBuilderRenderer, lies over the other three), and is
 * invisible - the model draws the whole press, the others becoming invisible {@link BlockPlaceholder}s. Breaking any
 * part takes the press apart ({@link #dismantle}): the placeholders turn back into their real blocks and this one drops
 * a piston (or, broken through one of the others, turns back into the piston). Right-click opens
 * {@link thaumcraft.common.container.GolemBuilderMenu}.
 */
public class BlockGolemBuilder extends Block implements EntityBlock {

	public static final MapCodec<BlockGolemBuilder> CODEC = simpleCodec(BlockGolemBuilder::new);

	private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 1, 1);

	public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
			net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

	/** The original's guard: a dismantle breaks placeholders, which would start another dismantle. */
	private static boolean ignore;

	public BlockGolemBuilder(BlockBehaviour.Properties properties) {
		super(properties.noOcclusion());
		registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** Placed directly (it has an item), it faces the player, as every original device did unless sneaking. */
	@Override
	public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
		net.minecraft.core.Direction look = context.getHorizontalDirection();
		return defaultBlockState().setValue(FACING, context.getPlayer() != null && context.getPlayer().isShiftKeyDown() ? look : look.getOpposite());
	}

	@Override
	protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
		return net.minecraft.world.level.block.RenderShape.INVISIBLE;
	}

	/** The original's BlockGolemBuilder.destroy: every placeholder within a block (up to one above) turns back into its
	 * real block, and the press itself into a piston facing up - unless {@code from} is the press, which is being
	 * broken and drops its piston itself. */
	public static void dismantle(Level level, BlockPos press, BlockPos from) {
		if (ignore || level.isClientSide) return;
		ignore = true;
		try {
			if (level.getBlockEntity(press) instanceof thaumcraft.common.tiles.crafting.TileGolemBuilder tile && !tile.origins().isEmpty()) {
				// put back exactly what it was built from
				var originals = java.util.Map.copyOf(tile.origins().all());
				for (var e : originals.entrySet()) {
					BlockPos p = e.getKey();
					if (p.equals(press)) continue;
					if (p.equals(from)) {
						thaumcraft.common.lib.crafting.MultiblockOrigins.dropAs(level, p, e.getValue());
						continue;
					}
					if (level.getBlockState(p).getBlock() instanceof thaumcraft.common.blocks.misc.BlockPlaceholder) {
						level.setBlock(p, e.getValue(), Block.UPDATE_ALL);
					}
				}
				if (!press.equals(from) && originals.get(press) != null) level.setBlock(press, originals.get(press), Block.UPDATE_ALL);
				return;
			}
			for (BlockPos p : BlockPos.betweenClosed(press.offset(-1, 0, -1), press.offset(1, 1, 1))) {
				if (p.equals(from)) continue;
				BlockState state = level.getBlockState(p);
				BlockState real = null;
				if (state.is(thaumcraft.api.blocks.BlocksTC.placeholderBars.get())) real = net.minecraft.world.level.block.Blocks.IRON_BARS.defaultBlockState();
				else if (state.is(thaumcraft.api.blocks.BlocksTC.placeholderAnvil.get())) real = net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState();
				else if (state.is(thaumcraft.api.blocks.BlocksTC.placeholderCauldron.get())) real = net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState();
				else if (state.is(thaumcraft.api.blocks.BlocksTC.placeholderTable.get())) real = thaumcraft.api.blocks.BlocksTC.tableStone.get().defaultBlockState();
				if (real != null) level.setBlock(p.immutable(), real, Block.UPDATE_ALL);
			}
			if (!press.equals(from)) {
				level.setBlock(press, net.minecraft.world.level.block.Blocks.PISTON.defaultBlockState()
						.setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, net.minecraft.core.Direction.UP), Block.UPDATE_ALL);
			}
		} finally {
			ignore = false;
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
		thaumcraft.common.blocks.EssentiaSpill.onRemove(state, level, pos, newState);
		if (!state.is(newState.getBlock())) {
			if (level.getBlockEntity(pos) instanceof TileGolemBuilder tile) {
				ItemStack held = tile.getItemHandler().getStackInSlot(0);
				if (!held.isEmpty()) {
					net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
				}
			}
			dismantle(level, pos, pos);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}

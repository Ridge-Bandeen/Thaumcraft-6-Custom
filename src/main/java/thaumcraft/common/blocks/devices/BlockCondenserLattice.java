package thaumcraft.common.blocks.devices;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code BlockCondenserLattice} - the connective structure a {@link
 * thaumcraft.common.tiles.devices.TileCondenser} scans upward through (see its own doc comment). Folded the
 * original's clean/dirty pair into a single class with a {@code dirty} flag, same "one class, two codecs"
 * shape as {@link BlockArcaneEar}, rather than a trivial subclass.
 *
 * Reuses vanilla's own {@code PipeBlock.PROPERTY_BY_DIRECTION} for the 6 connection booleans - same choice
 * as {@link thaumcraft.common.tiles.essentia.TileTube} - recomputed from real neighbor state on every shape
 * update purely for rendering the multipart model.
 *
 * A lattice block drops itself if it's ever left with no live BFS path back to a condenser (checked on
 * placement, on a lattice/condenser neighbor changing, and on harvest, exactly like the original's own
 * {@code triggerUpdate} calls). Right-clicking a dirty lattice block with an {@link ItemsTC#filter} clears it
 * back to clean, with a 50% chance of also ejecting a Flux crystal essence (an {@code ItemGenericEssentiaContainer}
 * stack tagged with 1 Flux, the modern equivalent of the original's damage-value-keyed
 * {@code ConfigItems.FLUX_CRYSTAL}).
 */
public class BlockCondenserLattice extends Block {

	public static final MapCodec<BlockCondenserLattice> CODEC = simpleCodec(props -> new BlockCondenserLattice(props, false));
	public static final MapCodec<BlockCondenserLattice> DIRTY_CODEC = simpleCodec(props -> new BlockCondenserLattice(props, true));

	private final boolean dirty;

	public BlockCondenserLattice(BlockBehaviour.Properties properties, boolean dirty) {
		super(properties);
		this.dirty = dirty;
		BlockState any = getStateDefinition().any();
		for (Direction dir : Direction.values()) {
			any = any.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), false);
		}
		registerDefaultState(any);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return dirty ? DIRTY_CODEC : CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PipeBlock.NORTH, PipeBlock.SOUTH, PipeBlock.EAST, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), connectsTo(level, pos, direction));
	}

	private boolean connectsTo(LevelAccessor level, BlockPos pos, Direction dir) {
		BlockState neighbor = level.getBlockState(pos.relative(dir));
		return neighbor.getBlock() instanceof BlockCondenserLattice
				|| (dir == Direction.DOWN && neighbor.getBlock() == BlocksTC.condenser.get());
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		triggerUpdate(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
		if (neighborBlock == BlocksTC.condenserLattice.get() || neighborBlock == BlocksTC.condenserLatticeDirty.get() || neighborBlock == BlocksTC.condenser.get()) {
			triggerUpdate(level, pos);
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		BlockState result = super.playerWillDestroy(level, pos, state, player);
		if (!level.isClientSide) triggerUpdate(level, pos);
		return result;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
		if (dirty && stack.is(ItemsTC.filter.get())) {
			stack.shrink(1);
			if (level.random.nextBoolean()) {
				ItemStack crystal = new ItemStack(ItemsTC.crystalEssence.get(), 1);
				((ItemGenericEssentiaContainer) crystal.getItem()).setAspects(crystal, new AspectList().add(Aspect.FLUX, 1));
				Block.popResource(level, pos, crystal);
			}
			level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.BLOCKS,
					0.2f, ((level.random.nextFloat() - level.random.nextFloat()) * 0.7f + 1.0f) * 1.6f);
			level.setBlock(pos, BlocksTC.condenserLattice.get().defaultBlockState(), 3);
			if (level.getBlockState(pos).getBlock() instanceof BlockCondenserLattice lattice) {
				lattice.triggerUpdate(level, pos);
			}
			return ItemInteractionResult.CONSUME;
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	/** Depth-limited BFS (same ~8.6 block cap as {@code TileCondenser}'s own scan) back down to a real
	 * condenser; drops itself as an item and turns to air if no live path exists any more. */
	public void triggerUpdate(Level level, BlockPos pos) {
		List<Long> history = new ArrayList<>();
		BlockPos found = processUpdate(level, pos, history);
		if (found == null || found.distSqr(pos) > 74.0) {
			// Always drop the clean lattice item, even for the dirty variant (which has no BlockItem of its
			// own, same as the original) - a small, deliberate improvement over silently dropping nothing.
			popResource(level, pos, new ItemStack(BlocksTC.condenserLattice.get()));
			level.removeBlock(pos, false);
		}
	}

	private BlockPos processUpdate(Level level, BlockPos pos, List<Long> history) {
		history.add(pos.asLong());
		for (Direction face : Direction.values()) {
			BlockPos p2 = pos.relative(face);
			if (history.contains(p2.asLong())) continue;
			Block b = level.getBlockState(p2).getBlock();
			if (b instanceof BlockCondenserLattice) {
				BlockPos found = processUpdate(level, p2, history);
				if (found != null) return found;
			}
			if (face == Direction.DOWN && b == BlocksTC.condenser.get()) {
				if (level.getBlockEntity(p2) instanceof thaumcraft.common.tiles.devices.TileCondenser condenser) {
					condenser.markLatticeDirty();
				}
				return p2;
			}
		}
		return null;
	}
}

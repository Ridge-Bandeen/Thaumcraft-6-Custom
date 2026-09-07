package thaumcraft.common.blocks.misc;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * Replaces the old (1.12.2) {@code BlockFluidPure} ("purifying fluid") - real, from-scratch finite-fluid
 * block, same real deliberate choice {@link thaumcraft.common.blocks.world.taint.BlockFluxGoo}'s own doc
 * comment already explains for this whole port: a plain {@link Block}, not a real modern
 * {@link net.minecraft.world.level.material.Fluid}/{@code FluidType} (that API is built entirely around the
 * infinite-source-block model; the original's own {@code BlockFluidClassic} base was quantized/finite, and
 * the original never exposed a bucket for it either). {@link #LEVEL}/flow/{@link #tryTransfer} are the exact
 * same real conserved-quanta flow engine {@code BlockFluxGoo} already established, reused verbatim here
 * (this block just doesn't decay or spawn anything - it only spreads until spent).
 *
 * Real effect on touch ({@link #entityInside}): a real, full-level (source-equivalent) block grants a real
 * player without an active {@link ThaumcraftMobEffects#WARP_WARD} a real temporary Warp Ward (the same
 * already-real effect {@code WarpEvents#checkWarpEvent} already gates its own warp-misfortune roll on -
 * built and wired an earlier session, not new here), duration scaled down by the player's own real permanent
 * warp (more permanent warp = a shorter ward, matching the original's own {@code 200000/sqrt(warp)} formula
 * exactly), then consumes itself - a real, single-use dip, matching the original's own
 * {@code world.setBlockToAir}.
 */
public class BlockPurifyingFluid extends Block {

	public static final MapCodec<BlockPurifyingFluid> CODEC = simpleCodec(BlockPurifyingFluid::new);

	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 7);
	public static final int MAX_LEVEL = 7;

	private static final int TICK_DELAY = 10;

	public BlockPurifyingFluid(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LEVEL, MAX_LEVEL));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return state.getValue(LEVEL) < 4;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(state.getBlock())) {
			level.scheduleTick(pos, this, TICK_DELAY);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighborPos, boolean movedByPiston) {
		level.scheduleTick(pos, this, TICK_DELAY);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (level.isClientSide || state.getValue(LEVEL) < MAX_LEVEL || !(entity instanceof Player player)) return;
		if (player.hasEffect(ThaumcraftMobEffects.WARP_WARD)) return;

		int warp = ThaumcraftCapabilities.getWarp(player).get(IPlayerWarp.EnumWarpType.PERMANENT);
		int div = Math.max(1, (int) Math.sqrt(warp));
		int duration = Math.min(32000, 200000 / div);
		player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.WARP_WARD, duration, 0, true, true));
		level.removeBlock(pos, false);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		flow(state, level, pos, random);
	}

	private void flow(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int lvl = state.getValue(LEVEL);
		if (lvl <= 0) return;
		BlockPos below = pos.below();
		if (tryTransfer(level, pos, state, lvl, below, level.getBlockState(below), MAX_LEVEL)) return;

		List<Direction> dirs = Direction.Plane.HORIZONTAL.shuffledCopy(random);
		for (Direction dir : dirs) {
			BlockPos side = pos.relative(dir);
			if (tryTransfer(level, pos, state, lvl, side, level.getBlockState(side), Math.max(1, lvl / 2))) return;
		}
	}

	/** Real, conserved quanta transfer - see {@code BlockFluxGoo#tryTransfer}'s own doc comment, identical
	 * shape reused here. */
	private boolean tryTransfer(ServerLevel level, BlockPos pos, BlockState state, int lvl, BlockPos targetPos, BlockState targetState, int capTargetTo) {
		int targetLevel;
		if (targetState.is(this)) {
			targetLevel = targetState.getValue(LEVEL);
		} else if (targetState.isAir() || targetState.canBeReplaced()) {
			targetLevel = 0;
		} else {
			return false;
		}
		int room = Math.min(MAX_LEVEL, capTargetTo) - targetLevel;
		int amount = Math.min(lvl, room);
		if (amount <= 0) return false;

		int newSelfLevel = lvl - amount;
		if (newSelfLevel <= 0) {
			level.removeBlock(pos, false);
		} else {
			level.setBlockAndUpdate(pos, state.setValue(LEVEL, newSelfLevel));
			level.scheduleTick(pos, this, TICK_DELAY);
		}
		level.setBlockAndUpdate(targetPos, defaultBlockState().setValue(LEVEL, targetLevel + amount));
		level.scheduleTick(targetPos, this, TICK_DELAY);
		return true;
	}
}

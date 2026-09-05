package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Replaces the old (1.12.2) {@code BlockRedstoneRelay} - a configurable-threshold, configurable-strength
 * redstone repeater ("outputs OUT (1-15) once the incoming signal reaches at least IN (1-15)", both real,
 * player-adjustable, vs. a plain repeater's fixed "any signal, full 15 out"). The original hand-rolled the
 * entire tick-delayed conditional-power-propagation mechanic that vanilla's own repeater already implements
 * - ported here by extending the real vanilla {@link DiodeBlock} base class instead (the same one {@code
 * RepeaterBlock} itself extends) and overriding only the 2 real points of difference ({@link #shouldTurnOn}
 * and {@link #getOutputSignal}); {@link DiodeBlock#getInputSignal} already does the exact same real "check a
 * neighbor redstone wire's own real power directly" fallback the original's own {@code calculateInputStrength}
 * needed, for free.
 *
 * Real, deliberate simplification: {@code IN}/{@code OUT} are real {@link IntegerProperty} blockstate values
 * (1-15 each) rather than a separate tile entity - the original only needed a tile because 1.12.2's
 * blockstate system couldn't cleanly carry two more 15-value properties; modern blockstates already handle
 * combinatorics like this for free (a vanilla repeater's own state already combines 4 properties). No tile
 * entity at all is needed as a result. The original's 2-separate-clickable-sub-hitbox interaction (one dial
 * for IN, one for OUT, via a CodeChickenLib raytracer) is replaced with a plain interaction matching this
 * session's established simplification for that whole raytracer system: right-click cycles OUT, shift-
 * right-click cycles IN.
 */
public class BlockRedstoneRelay extends DiodeBlock {

	public static final MapCodec<BlockRedstoneRelay> CODEC = simpleCodec(BlockRedstoneRelay::new);

	public static final IntegerProperty IN = IntegerProperty.create("in", 1, 15);
	public static final IntegerProperty OUT = IntegerProperty.create("out", 1, 15);

	public BlockRedstoneRelay(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(FACING, Direction.NORTH)
				.setValue(POWERED, false)
				.setValue(IN, 1)
				.setValue(OUT, 15));
	}

	@Override
	protected MapCodec<? extends DiodeBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		// Neither DiodeBlock nor HorizontalDirectionalBlock actually adds FACING/POWERED to the state
		// definition themselves (confirmed via javap - both leave it entirely to concrete subclasses, same
		// as every real vanilla DiodeBlock subclass, e.g. RepeaterBlock, does), so this adds all 4 real
		// properties itself rather than assuming a super call handles any of them.
		builder.add(FACING, POWERED, IN, OUT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// Matches the original's own real inversion: sneak-placing faces it the SAME way you're looking,
		// a normal placement faces it the OPPOSITE way (i.e. towards you) - a deliberate original design
		// choice, not a bug, kept as-is.
		Player placer = context.getPlayer();
		Direction facing = placer != null && placer.isShiftKeyDown() ? context.getHorizontalDirection() : context.getHorizontalDirection().getOpposite();
		return defaultBlockState().setValue(FACING, facing).setValue(POWERED, false).setValue(IN, 1).setValue(OUT, 15);
	}

	@Override
	protected int getDelay(BlockState state) {
		return 2;
	}

	@Override
	protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
		return getInputSignal(level, pos, state) >= state.getValue(IN);
	}

	@Override
	protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
		return state.getValue(OUT);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (player.isShiftKeyDown()) {
			int in = state.getValue(IN) % 15 + 1;
			level.setBlock(pos, state.setValue(IN, in), 2);
		} else {
			int out = state.getValue(OUT) % 15 + 1;
			level.setBlock(pos, state.setValue(OUT, out), 2);
		}
		checkTickOnNeighbor(level, pos, level.getBlockState(pos));
		return InteractionResult.CONSUME;
	}
}

package thaumcraft.common.blocks.devices;

import java.util.List;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.common.lib.utils.CuboidTargeting;

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
 * entity at all is needed as a result.
 *
 * <p>The original's 2 separate clickable dials (one for IN, one for OUT) are real again too, via
 * {@link CuboidTargeting} - the modern, no-third-party-library replacement for the CodeChicken raytracer
 * this was first ported with (see that class's own doc comment for the full story). {@link #dialBox} places
 * the 2 real dial hitboxes symmetrically to either side of the block's own real facing, rotating correctly
 * with it - a real, deliberate re-derivation rather than the true original's own literal rotated-cuboid
 * numbers (not worth reverse-engineering those pixel-for-pixel; two genuinely separate, correctly-rotating
 * click regions reproduce the real mechanic faithfully). Right-clicking the OUT-side dial cycles OUT;
 * right-clicking the IN-side dial cycles IN - no shift-click needed any more, matching the original's own
 * real "two independent dials" UX instead of the previous placeholder's "one dial with a modifier key."
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

	/** Real per-dial small hitbox, rotating with the block's own real facing - see this class's own doc
	 * comment. Both dials sit low on the block, offset to either side of center and slightly toward the
	 * input face (behind the direction the relay points), never overlapping. */
	private static AABB dialBox(BlockPos pos, Direction facing, boolean out) {
		Direction side = out ? facing.getClockWise() : facing.getCounterClockWise();
		double cx = 0.5 + side.getStepX() * 0.25 - facing.getStepX() * 0.125;
		double cz = 0.5 + side.getStepZ() * 0.25 - facing.getStepZ() * 0.125;
		return new AABB(cx - 0.125, 0.0625, cz - 0.125, cx + 0.125, 0.25, cz + 0.125).move(pos);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		Direction facing = state.getValue(FACING);
		Boolean dial = CuboidTargeting.pick(player, List.of(
				new CuboidTargeting.Candidate<>(Boolean.TRUE, dialBox(pos, facing, true)),
				new CuboidTargeting.Candidate<>(Boolean.FALSE, dialBox(pos, facing, false))));
		if (dial == null) return InteractionResult.PASS;

		if (dial) {
			int out = state.getValue(OUT) % 15 + 1;
			level.setBlock(pos, state.setValue(OUT, out), 2);
		} else {
			int in = state.getValue(IN) % 15 + 1;
			level.setBlock(pos, state.setValue(IN, in), 2);
		}
		checkTickOnNeighbor(level, pos, level.getBlockState(pos));
		return InteractionResult.CONSUME;
	}
}

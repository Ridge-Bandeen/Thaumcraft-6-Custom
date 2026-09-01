package thaumcraft.common.blocks.devices;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.server.level.ServerLevel;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code BlockVisBattery} - a redstone-triggered vis store with no tile entity
 * at all, unlike almost every other device this session. All state lives directly in a single blockstate
 * property, {@code CHARGE} (0-10): unpowered, it slowly siphons vis out of the local aura up to its 10-charge
 * cap (only while the aura is comfortably above its base level, and drains itself back into the aura if the
 * aura ever drops below 75% of base); powered by redstone, it discharges one charge per {@code updateTick}
 * back into the aura instead - a manual "release stored vis on demand" battery, not an energy conduit.
 *
 * The original's {@code updateTick} (a scheduled-tick callback, not a random one) drives the whole cycle,
 * re-arming itself via {@code scheduleUpdate} every time it runs; {@code setTickRandomly(true)} was only
 * there to give the cycle its very first kick after being placed with nothing yet scheduled. Reproduced here
 * with vanilla's modern equivalents: {@link BlockBehaviour.Properties#randomTicks()} + {@link #randomTick}
 * for that first kick, {@link #tick} + {@code level.scheduleTick(pos, this, delay)} for the self-perpetuating
 * chain, and {@link #neighborChanged} scheduling an immediate tick on a redstone signal exactly as before.
 *
 * Light level and comparator output are both driven directly off {@code CHARGE} via
 * {@link BlockBehaviour.Properties#lightLevel} and {@link #getAnalogOutputSignal} - the original computed
 * the same two things from its meta value by hand.
 */
public class BlockVisBattery extends Block {

	public static final MapCodec<BlockVisBattery> CODEC = simpleCodec(BlockVisBattery::new);

	public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 10);

	public BlockVisBattery(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(CHARGE, 0));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CHARGE);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		tickVisBattery(level, pos, state, random);
	}

	/**
	 * The actual scheduled-tick logic, split out to a public static method so GameTests can call it
	 * directly instead of depending on the real random-tick-then-scheduled-tick chain to fire within a
	 * test's short window - same "test the real entry point directly" precedent as
	 * {@code TileArcaneEar.recordNoteEvent}.
	 */
	public static void tickVisBattery(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		int charge = state.getValue(CHARGE);
		if (level.hasNeighborSignal(pos)) {
			if (charge > 0) {
				AuraHandler.addVis(level, pos, 1.0f);
				level.setBlock(pos, state.setValue(CHARGE, charge - 1), 3);
				level.scheduleTick(pos, state.getBlock(), 5);
			}
		} else {
			float aura = AuraHelper.getVis(level, pos);
			int base = AuraHelper.getAuraBase(level, pos);
			if (charge < 10 && aura > base * 0.9 && aura > 1.0f) {
				AuraHandler.drainVis(level, pos, 1.0f, false);
				level.setBlock(pos, state.setValue(CHARGE, charge + 1), 3);
				level.scheduleTick(pos, state.getBlock(), 100 + random.nextInt(100));
			} else if (charge > 0 && aura < base * 0.75) {
				AuraHandler.addVis(level, pos, 1.0f);
				level.setBlock(pos, state.setValue(CHARGE, charge - 1), 3);
				level.scheduleTick(pos, state.getBlock(), 20 + random.nextInt(20));
			}
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
		if (level.hasNeighborSignal(pos)) {
			level.scheduleTick(pos, this, 1);
		}
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return state.getValue(CHARGE);
	}
}

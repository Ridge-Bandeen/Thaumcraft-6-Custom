package thaumcraft.common.blocks.world.taint;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Replaces the old (1.12.2) {@code BlockTaintLog} - a plain axis-oriented log, real corrupted wood. Extends
 * vanilla's own {@link RotatedPillarBlock} directly (same real base class {@code logSilverwood}/{@code
 * logGreatwood} already use in {@code BlocksTC}) rather than reimplementing axis placement by hand - modern
 * vanilla's own {@code getStateForPlacement} already computes the real axis from the clicked face, exactly
 * matching the original's own override.
 *
 * Real per-tick behavior via {@link #randomTick}, faithfully distinct from every other taint block in this
 * arc: dies unconditionally (no 10%-chance gate) once it's outside a real tracked seed's range, otherwise
 * keeps spreading real fibres via {@link TaintHelper#spreadFibres}.
 *
 * Real, documented cuts vs the original: {@code die()}'s own {@code fluxGoo} target isn't ported yet (same
 * deferred finite-fluid outlier as the rest of this arc), so it falls back to real air. No custom "gore"
 * sound type or fire-spread-speed registration - this port hasn't wired up either the original's own custom
 * {@code SoundEvent} catalog or NeoForge's {@code FlammableBlockRegistry} yet for ANY block (including the
 * already-ported {@code logSilverwood}/{@code logGreatwood}), not a cut specific to this one.
 */
public class BlockTaintLog extends RotatedPillarBlock implements ITaintBlock {

	public static final MapCodec<BlockTaintLog> CODEC = simpleCodec(BlockTaintLog::new);

	public BlockTaintLog(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<? extends RotatedPillarBlock> codec() {
		return CODEC;
	}

	@Override
	public void die(Level level, BlockPos pos, BlockState state) {
		level.removeBlock(pos, false);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!TaintHelper.isNearTaintSeed(level, pos)) {
			die(level, pos, state);
		} else {
			TaintHelper.spreadFibres(level, pos);
		}
	}
}

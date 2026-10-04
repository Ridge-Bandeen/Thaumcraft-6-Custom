package thaumcraft.api;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.blocks.world.taint.BlockTaint;

/**
 * What tainted ground turns back into when the taint dies away. By default (as the original) tainted rock becomes
 * porous stone, tainted soil plain dirt, and crust and geysers flux goo. An addon can supply its own answer - the
 * local soil or rock of the world it adds, say - with {@link #setDecayResult}; the last one set wins. What taint eats
 * is the {@code #thaumcraft:taint/crust_surface}, {@code soil_surface} and {@code rock_surface} block tags.
 */
public final class TaintHooks {

	@FunctionalInterface
	public interface DecayResult {
		/** The block {@code variant} taint at {@code pos} decays into, or null for Thaumcraft's default. */
		@Nullable
		BlockState decay(Level level, BlockPos pos, BlockTaint.Variant variant);
	}

	private static volatile DecayResult decayResult = (level, pos, variant) -> null;

	private TaintHooks() {
	}

	public static void setDecayResult(DecayResult result) {
		decayResult = result;
	}

	@Nullable
	public static BlockState decay(Level level, BlockPos pos, BlockTaint.Variant variant) {
		return decayResult.decay(level, pos, variant);
	}
}

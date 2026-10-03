package thaumcraft.common.lib.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The original's {@code CropUtils}: what counts as a fully grown crop (for the Harvest seal and the Growth Lamp, which
 * leaves grown crops alone). Thaumcraft's own registrations: melons and pumpkins are always "grown", nether wart at
 * age 3, and sugar cane and cactus once they stand on their own kind (the "stacked" crops). Other mods could add
 * standard, clickable and stacked crops and lamp-blacklisted blocks through IMC; nothing here registers any.
 */
public final class CropUtils {

	private CropUtils() {
	}

	private static boolean isStandardCrop(BlockState state) {
		return state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || (state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) == 3);
	}

	private static boolean isStackedCrop(BlockState state) {
		return state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS);
	}

	/** The original's isGrownCrop: a growable that can't grow any more (not a stem), a crop at its last age, a standard
	 * crop, or a stacked crop on top of its own kind. */
	public static boolean isGrownCrop(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) return false;
		Block block = state.getBlock();
		boolean listed = isStandardCrop(state) || isStackedCrop(state);
		// 1.12's grass "could always grow"; today's spreaders only can with room above, so they're left out as before
		if (block instanceof BonemealableBlock growable && growable.getType() != BonemealableBlock.Type.NEIGHBOR_SPREADER
				&& !growable.isValidBonemealTarget(level, pos, state) && !(block instanceof StemBlock)) {
			return true;
		}
		if (block instanceof CropBlock crop && crop.isMaxAge(state) && !listed) return true;
		return isStandardCrop(state) || (isStackedCrop(state) && level.getBlockState(pos.below()).is(block));
	}

	/** The original's doesLampGrow: everything but blocks other mods blacklisted (none here). */
	public static boolean doesLampGrow(Level level, BlockPos pos) {
		return true;
	}
}

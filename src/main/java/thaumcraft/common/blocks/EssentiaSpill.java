package thaumcraft.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.aura.AuraHelper;

/**
 * The original BlockTCTile#breakBlock: when a block whose tile carries essentia goes away (broken, blown up or replaced),
 * the essentia it held (as reported upward) pours into the aura as flux. Jars were the one exception - they keep their
 * contents in the dropped jar - so BlockJar doesn't call this. Call it from {@code onRemove} before the block entity goes.
 */
public final class EssentiaSpill {

	private EssentiaSpill() {
	}

	public static void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState) {
		if (level.isClientSide || state.is(newState.getBlock())) return;
		if (level.getBlockEntity(pos) instanceof IEssentiaTransport transport) {
			int essentia = transport.getEssentiaAmount(Direction.UP);
			if (essentia > 0) AuraHelper.polluteAura(level, pos, essentia, true);
		}
	}
}

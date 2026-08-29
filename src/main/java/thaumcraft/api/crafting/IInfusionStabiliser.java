package thaumcraft.api.crafting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;


/**
 *
 * @author Azanor
 *
 * Blocks that implement this interface act as infusion crafting stabilisers like candles and skulls
 *
 * @Deprecated
 * This interface will eventually be combined with IInfusionStabiliserExt
 * Currently they are separate to preserve compatibility with addon mods.
 *
 */
@Deprecated
public interface IInfusionStabiliser {

	/**
	 * returns true if the block can stabilise things
	 */
	public boolean canStabaliseInfusion(Level world, BlockPos pos);

}

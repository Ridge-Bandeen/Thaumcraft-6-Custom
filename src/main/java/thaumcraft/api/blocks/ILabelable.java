package thaumcraft.api.blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;


/**
 *
 * @author Azanor
 *
 * Tile entities or blocks that extend this interface can have jar labels applied to them
 *
 */
public interface ILabelable {

	/**
	 * This method is used by the block or tileentity to do whatever needs doing.
	 * @return if true then label will be subtracted from player inventory
	 */
	public boolean applyLabel(Player player, BlockPos pos, Direction side, ItemStack labelstack);

}

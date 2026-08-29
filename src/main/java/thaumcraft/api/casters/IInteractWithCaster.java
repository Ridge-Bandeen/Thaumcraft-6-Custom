package thaumcraft.api.casters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;


/**
 *
 * @author azanor
 *
 * Add this to a tile entity that you wish casters to interact with in some way.
 *
 */

public interface IInteractWithCaster {

	public boolean onCasterRightClick(Level world, ItemStack casterStack, Player player, BlockPos pos, Direction side, InteractionHand hand);

}

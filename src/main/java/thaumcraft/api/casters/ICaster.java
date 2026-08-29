package thaumcraft.api.casters;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;


public interface ICaster {

	public abstract float getConsumptionModifier(ItemStack is, Player player, boolean crafting);

	public abstract boolean consumeVis(ItemStack is, Player player, float amount, boolean crafting, boolean simulate);

	public abstract Item getFocus(ItemStack stack);

	public abstract ItemStack getFocusStack(ItemStack stack);

	public abstract void setFocus(ItemStack stack, ItemStack focus);

	public ItemStack getPickedBlock(ItemStack stack);

}

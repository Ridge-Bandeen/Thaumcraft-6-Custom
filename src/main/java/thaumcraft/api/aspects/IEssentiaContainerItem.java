package thaumcraft.api.aspects;
import net.minecraft.world.item.ItemStack;


/**
 *
 * @author azanor
 *
 * Used by wispy essences and essentia phials to hold their aspects.
 * Useful for similar item containers that store their aspect information in nbt form so TC
 * automatically picks up the aspects they contain.
 *
 */
public interface IEssentiaContainerItem {
	public AspectList getAspects(ItemStack itemstack);
	public void setAspects(ItemStack itemstack, AspectList aspects);

	/**
	 * Return true if the contained aspect should not be used to calculate the actual item aspects. For example: jar labels.
	 */
	public boolean ignoreContainedAspects();
}

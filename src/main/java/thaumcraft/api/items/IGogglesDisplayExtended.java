package thaumcraft.api.items;
import net.minecraft.world.phys.Vec3;


/**
 *
 * An interface that you can attach to a block or tilenetity to display additional text when viewed with an IGoggles item.
 * Used by things like Infusion Matrix to display additional information.
 *
 */
public interface IGogglesDisplayExtended {

	/**
	 * What text to display onscreen. You can return multiple lines (discretion is advised).
	 * @return
	 */
	public String[] getIGogglesText();

	/**
	 * Returns the positional offset that text will be displayed at in relation to the object.
	 * @return
	 */
	default public Vec3 getIGogglesTextOffset() {
		return new Vec3(0,0,0);
	}

}

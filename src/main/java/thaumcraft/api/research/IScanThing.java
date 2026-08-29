package thaumcraft.api.research;
import net.minecraft.world.entity.player.Player;


public interface IScanThing {

	/**
	 * The passed in obj can either be an Entity, a BlockPos, an Itemstack, or a null if nothing was actually clicked on.
	 * You could then probably use the players lookvec to do whatever you want.
	 * @param player
	 * @param obj
	 * @return the research key that will be unlocked if the object is scanned.
	 * This need not be an actual defined research item - any text string will do, though note that
	 * some characters like '@' have special meanings within the whole research system as a whole.
	 * I generally use "!" as a prefix for these research key's.
	 * You can then use this research key (fake or otherwise) as a parent for research or for whatever.
	 */
	public boolean checkThing(Player player, Object obj);

	/**
	 * @param object
	 * @param player
	 * @return the research key linked to this 'thing'.
	 */
	public String getResearchKey(Player player, Object object);


	/**
	 * @param object
	 * @param player
	 * @return the research key linked to this 'thing'
	 */
	public default void onSuccess(Player player, Object object) {

	}
}

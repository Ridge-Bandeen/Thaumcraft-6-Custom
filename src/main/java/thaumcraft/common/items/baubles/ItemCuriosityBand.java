package thaumcraft.common.items.baubles;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Replaces the old (1.12.2) {@code ItemCuriosityBand}. Plain item - the original's own class was purely a
 * head-render hook (a 3D band model drawn on the player's bauble-render layer), which has no real analog
 * worth chasing for a normal inventory item; its one real gameplay effect (splitting XP orb pickups and
 * occasionally converting the difference into free research progress) lived entirely in a separate event
 * handler in the original and is ported the same way here - see
 * {@link thaumcraft.common.lib.events.WornGearEvents#xpPickup}.
 */
public class ItemCuriosityBand extends Item {

	public ItemCuriosityBand(Properties properties) {
		super(properties.rarity(Rarity.RARE));
	}
}

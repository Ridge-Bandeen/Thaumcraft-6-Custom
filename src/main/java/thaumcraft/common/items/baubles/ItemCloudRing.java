package thaumcraft.common.items.baubles;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Replaces the old (1.12.2) {@code ItemCloudRing}: carried (the port's stand-in for its Baubles ring slot), it gives a
 * double jump ({@link thaumcraft.client.lib.events.CloudRingJump}) and softens falls
 * ({@link thaumcraft.common.lib.events.WornGearEvents#fallDamage}), as the original.
 */
public class ItemCloudRing extends Item {

	public ItemCloudRing(Properties properties) {
		super(properties.rarity(Rarity.RARE));
	}
}

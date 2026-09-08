package thaumcraft.common.items.tools;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Replaces the old (1.12.2) {@code ItemSanityChecker} - a plain held item now (matching the original exactly,
 * which had no real behavior of its own beyond its rarity). The real mechanic - a live readout of the
 * holder's real Warp breakdown - lives in {@link thaumcraft.client.gui.SanityCheckerOverlay}, the same
 * "the item is just a marker, the client overlay does the real work" split
 * {@link thaumcraft.common.items.casters.ItemFocusPouch}/{@link thaumcraft.api.casters.ICaster}'s own real
 * focus-radial HUD already established.
 */
public class ItemSanityChecker extends Item {

	public ItemSanityChecker(Properties properties) {
		super(properties.rarity(Rarity.UNCOMMON));
	}
}

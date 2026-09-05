package thaumcraft.common.items.baubles;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.items.IVisDiscountGear;

/**
 * Replaces the old (1.12.2) {@code ItemBaubles} damage value 3 ("ring_apprentice") - the one real subtype
 * out of that item's 7 metadata variants (the other 6 were always-blank trinkets, now registered directly
 * as plain items - see {@code ItemsTC#amuletMundane} and siblings). A 5% vis-cost discount, same as the
 * original's {@code getVisDiscount}. Per the user's "just move bauble items to normal item slots" call, this
 * is picked up by scanning the player's hotbar/offhand/armor - see {@link thaumcraft.common.items.casters.ItemCaster
 * #getConsumptionModifier}.
 */
public class ItemRingApprentice extends Item implements IVisDiscountGear {

	public ItemRingApprentice(Properties properties) {
		super(properties);
	}

	@Override
	public int getVisDiscount(ItemStack stack, Player player) {
		return 5;
	}
}

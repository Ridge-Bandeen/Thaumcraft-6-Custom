package thaumcraft.common.items.baubles;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Replaces the old (1.12.2) {@code ItemCharmUndying}. Plain item - all its real behavior (canceling a fatal
 * hit, consuming one, healing to 1 HP, granting Regeneration/Absorption) lives in
 * {@link thaumcraft.common.lib.events.WornGearEvents#onDeath}, matching where the original itself put it
 * (its own {@code onWornTick} was empty too). See {@link thaumcraft.api.items.WornGearHelper}'s own doc
 * comment for why that scans the hotbar/offhand instead of a dedicated bauble slot.
 */
public class ItemCharmUndying extends Item {

	public ItemCharmUndying(Properties properties) {
		super(properties.rarity(Rarity.RARE));
	}
}

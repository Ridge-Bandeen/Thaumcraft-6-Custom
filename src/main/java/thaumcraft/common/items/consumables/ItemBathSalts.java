package thaumcraft.common.items.consumables;

import net.minecraft.world.item.Item;

/**
 * Replaces the old (1.12.2) {@code ItemBathSalts} - the {@link thaumcraft.common.tiles.devices.TileSpa}
 * crafting ingredient, real Bath Salts. Real, documented cut: the original's own shortened item-entity
 * lifespan (200 ticks instead of the default 6000, tied to a real-mode-only-in-1.12.2
 * {@code getEntityLifespan} hook that also fed a "tossed salts sitting in open water directly convert it"
 * shortcut over in the old, stale {@code EntityEvents#itemExpire}) isn't ported - the Spa device itself
 * (consuming a Bath Salts from its own real inventory slot) is the real, intended way to make purifying
 * fluid, matching how the original's own Thaumonomicon entry actually presents this item.
 */
public class ItemBathSalts extends Item {

	public ItemBathSalts(Properties properties) {
		super(properties);
	}
}

package thaumcraft.api.items;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Real, deliberate stand-in for the original's own Baubles-slot {@code onWornTick} - this port has no real
 * bauble-slot equipment system at all yet (a genuine future task), so an item implementing this is instead
 * ticked whenever {@link WornGearHelper#getWornItems} finds it - the player's own real hotbar or offhand,
 * not a dedicated slot. See {@code thaumcraft.common.lib.events.WornGearEvents} for the real tick hook.
 */
public interface IWornGear {

	/** Called once per real server tick for each stack this item owns that {@link WornGearHelper} finds
	 * "worn" - matches the original's own real {@code IBauble#onWornTick} cadence and intent, just driven by
	 * a real plain inventory scan instead of a real dedicated bauble slot. */
	void wornTick(ItemStack stack, Player player);
}

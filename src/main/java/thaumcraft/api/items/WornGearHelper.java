package thaumcraft.api.items;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Shared inventory scan standing in for the original's real Baubles-slot iteration - per the user's explicit
 * "just move bauble items to normal item slots... we can unfuck it later" call, every consumer of this class
 * (worn-gear ticking, the undying-charm death check, the caster vis-discount scan) looks at the player's real
 * hotbar (main-inventory slots 0-8) plus their real offhand slot instead of a dedicated bauble slot.
 */
public final class WornGearHelper {

	private WornGearHelper() {}

	/** All non-empty stacks in the player's hotbar and offhand, in that order. */
	public static List<ItemStack> getWornItems(Player player) {
		List<ItemStack> worn = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty()) worn.add(stack);
		}
		ItemStack offhand = player.getOffhandItem();
		if (!offhand.isEmpty()) worn.add(offhand);
		return worn;
	}
}

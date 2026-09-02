package thaumcraft.api.research;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Replaces the old (1.12.2) {@code ScanItem}. Matches on the item's identity only (same item, ignoring
 * durability/enchantments/etc.) via {@link ItemStack#is(net.minecraft.world.item.Item)} - the modern,
 * simpler equivalent of the original's {@code ThaumcraftInvHelper.areItemStacksEqualForCrafting}, which
 * isn't ported.
 */
public class ScanItem implements IScanThing {

	private final String research;
	private final ItemStack stack;

	public ScanItem(String research, ItemStack stack) {
		this.research = research;
		this.stack = stack;
	}

	@Override
	public boolean checkThing(Player player, Object obj) {
		if (obj == null) return false;

		ItemStack is = null;
		if (obj instanceof ItemStack itemStack) is = itemStack;
		if (obj instanceof ItemEntity itemEntity && !itemEntity.getItem().isEmpty()) is = itemEntity.getItem();

		return is != null && !is.isEmpty() && is.is(stack.getItem());
	}

	@Override
	public String getResearchKey(Player player, Object object) {
		return research;
	}
}

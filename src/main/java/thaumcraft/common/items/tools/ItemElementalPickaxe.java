package thaumcraft.common.items.tools;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;

/**
 * Replaces the old (1.12.2) {@code ItemElementalPickaxe}, the Pickaxe of the Core: as the original, a swing at anything
 * sets it alight for 2 seconds (players only with PvP on); it comes pre-infused with Refining and Sounding.
 */
public class ItemElementalPickaxe extends PickaxeItem {

	public ItemElementalPickaxe(Tier tier, Properties properties) {
		super(tier, properties);
	}

	@Override
	public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
		if (!player.level().isClientSide && (!(entity instanceof Player) || player.level().getServer().isPvpAllowed())) {
			entity.igniteForSeconds(2.0f);
		}
		return super.onLeftClickEntity(stack, player, entity);
	}
}

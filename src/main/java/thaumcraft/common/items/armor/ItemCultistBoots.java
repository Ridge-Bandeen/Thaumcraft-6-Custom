package thaumcraft.common.items.armor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftArmorMaterials;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.api.items.IWarpingGear;

/**
 * Replaces the old (1.12.2) {@code ItemCultistBoots} - the Crimson Boots, a rare cultist drop. Real
 * {@link IWarpingGear} (+1 warp) and {@link IVisDiscountGear} (1% discount) implementer, same "both
 * interfaces on one boots slot" shape {@link thaumcraft.common.items.armor.ItemRobeArmor} already
 * established for {@code IVisDiscountGear} alone. Uses {@link ThaumcraftArmorMaterials#CULTIST_BOOTS} -
 * see that field's own doc comment for why plain vanilla iron material couldn't be reused directly despite
 * matching stats exactly.
 */
public class ItemCultistBoots extends ArmorItem implements IWarpingGear, IVisDiscountGear {

	public ItemCultistBoots(Properties properties) {
		super(ThaumcraftArmorMaterials.CULTIST_BOOTS, Type.BOOTS, properties);
	}

	@Override
	public int getWarp(ItemStack itemstack, Player player) {
		return 1;
	}

	@Override
	public int getVisDiscount(ItemStack stack, Player player) {
		return 1;
	}
}

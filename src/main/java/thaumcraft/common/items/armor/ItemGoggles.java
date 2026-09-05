package thaumcraft.common.items.armor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftArmorMaterials;
import thaumcraft.api.items.IGoggles;
import thaumcraft.api.items.IVisDiscountGear;

/**
 * Replaces the old (1.12.2) {@code ItemGoggles}. A plain real helmet (see {@link ThaumcraftArmorMaterials#GOGGLES}),
 * real 5% {@link IVisDiscountGear} discount, real {@link IGoggles} (always shows in-game popups while worn).
 *
 * Real, deliberate cut: the original's dual "wear as a helmet OR as a Baubles head accessory" mode (via
 * {@code IBauble}/{@code IRenderBauble}) is dropped - this port has no real bauble-slot system at all (see
 * {@link thaumcraft.api.items.WornGearHelper}'s own doc comment), so Goggles is a plain helmet only, same
 * simplification already applied to every other bauble-family item this session.
 */
public class ItemGoggles extends ArmorItem implements IVisDiscountGear, IGoggles {

	public ItemGoggles(Properties properties) {
		super(ThaumcraftArmorMaterials.GOGGLES, Type.HELMET, properties);
	}

	@Override
	public int getVisDiscount(ItemStack stack, Player player) {
		return 5;
	}

	@Override
	public boolean showIngamePopups(ItemStack itemstack, LivingEntity player) {
		return true;
	}
}

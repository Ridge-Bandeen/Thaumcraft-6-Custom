package thaumcraft.common.items.armor;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IWarpingGear;

/**
 * Replaces the old (1.12.2) {@code ItemVoidArmor} - the base Void armor set (helm/chest/legs/boots, crafted
 * at a plain crafting table - see {@code data/thaumcraft/recipe/void_helm.json} and siblings), one shared
 * class for all 4 pieces matching the original's own design. Real, ported-as-is effects: self-repairs 1
 * durability every real second while worn or held (the original ran this same check from both {@code
 * onUpdate} and {@code onArmorTick} redundantly - one real {@link #inventoryTick} hook covers both here),
 * and adds 1 real {@link IWarpingGear} warp per piece while worn or held.
 */
public class ItemVoidArmor extends ArmorItem implements IWarpingGear {

	public ItemVoidArmor(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		super.inventoryTick(stack, level, entity, slotId, isSelected);
		if (!level.isClientSide && stack.isDamaged() && entity.tickCount % 20 == 0) {
			stack.setDamageValue(stack.getDamageValue() - 1);
		}
	}

	@Override
	public int getWarp(ItemStack itemstack, Player player) {
		return 1;
	}
}

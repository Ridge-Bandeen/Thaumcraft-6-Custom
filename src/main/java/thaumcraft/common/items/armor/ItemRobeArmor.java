package thaumcraft.common.items.armor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftArmorMaterials;
import thaumcraft.api.items.IVisDiscountGear;

/**
 * Replaces the old (1.12.2) {@code ItemRobeArmor} - the "Robes" cloth armor set (chest/legs/boots, one
 * shared class for all three like the original). Lower armor value than iron, but a real
 * {@link IVisDiscountGear} implementer - the first one this port has, and {@link
 * thaumcraft.common.items.casters.ItemCaster#getConsumptionModifier} now checks worn armor for it for real
 * (that interface was already ported early this session but had zero implementers or consumers until now).
 * Uses {@link ThaumcraftArmorMaterials#ROBES} - an exact stat match for the original's own
 * {@code ARMORMAT_SPECIAL} (this set and Boots of the Traveller shared that same material in the original
 * too; kept as a separate registry entry here only because the texture Layer is now baked into the
 * material - see that field's own doc comment).
 *
 * Simplified vs the original: no dye/cauldron-wash support (the original's {@code hasColor}/{@code setColor}/
 * {@code removeColor} plus an {@code onItemUseFirst} cauldron-water-consuming override, the same interaction
 * shape as vanilla leather armor washing). The base textures are the plain grayscale ones meant to be tinted
 * - undyed, they render as a flat gray robe rather than vanilla leather armor's default tan. A real,
 * documented cosmetic loss, not a functional one - worth adding back via
 * {@code DataComponents.DYED_COLOR} (the modern replacement for the original's NBT {@code display.color}
 * tag) if it matters later.
 */
public class ItemRobeArmor extends ArmorItem implements IVisDiscountGear {

	public ItemRobeArmor(ArmorItem.Type type, Properties properties) {
		super(ThaumcraftArmorMaterials.ROBES, type, properties);
	}

	@Override
	public int getVisDiscount(ItemStack stack, Player player) {
		return getType() == ArmorItem.Type.BOOTS ? 2 : 3;
	}
}

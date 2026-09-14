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
 * <p>Real dye support, restored (this class's own doc comment previously listed it as a cut, and even named
 * the modern replacement this now uses). Rather than porting the original's own {@code hasColor}/
 * {@code setColor}/{@code removeColor} overrides and its own hand-rolled dye-averaging crafting recipe
 * ({@code RecipesRobeArmorDyes}, a byte-for-byte clone of vanilla's own 1.12.2 {@code RecipesArmorDyes}
 * gated on this class), all of it is now real, data-driven vanilla behaviour: these items sit in the real
 * {@code #minecraft:dyeable} item tag, which is exactly what vanilla's own {@code ArmorDyeRecipe} and its
 * own cauldron-wash {@code CauldronInteraction} both key off - so combining a robe with dyes in a crafting
 * grid and washing it clean in a cauldron are both real again, with zero mod-side recipe code. The color
 * itself lives in vanilla's own {@link net.minecraft.core.component.DataComponents#DYED_COLOR} component
 * (the modern replacement for the original's NBT {@code display.color} tag), tinting both the real worn
 * armor layer ({@link ThaumcraftArmorMaterials#ROBES}'s own real dyeable {@code Layer}) and the real item
 * icon (registered in {@code ThaumcraftClient}) - the base textures are deliberately grayscale for exactly
 * this. Undyed, they fall back to {@link #DEFAULT_ROBE_COLOR}, the original's own real default.
 */
public class ItemRobeArmor extends ArmorItem implements IVisDiscountGear {

	/** The original's own real default robe color ({@code 6961280}), returned by its own {@code getColor}
	 * whenever no {@code display.color} tag was set - the classic Thaumcraft purple. Shared with
	 * {@link ItemVoidRobeArmor}, which used the exact same real default. */
	public static final int DEFAULT_ROBE_COLOR = 0x6A3880;

	public ItemRobeArmor(ArmorItem.Type type, Properties properties) {
		super(ThaumcraftArmorMaterials.ROBES, type, properties);
	}

	@Override
	public int getVisDiscount(ItemStack stack, Player player) {
		return getType() == ArmorItem.Type.BOOTS ? 2 : 3;
	}
}

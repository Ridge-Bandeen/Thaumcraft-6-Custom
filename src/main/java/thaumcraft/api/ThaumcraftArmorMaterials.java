package thaumcraft.api;

import java.util.EnumMap;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.items.ItemsTC;

/**
 * Since 1.20.5, {@code ArmorMaterial} is a proper datapack-aware registry entry (like
 * {@link ThaumcraftAttributes}'s {@code Attribute}s) rather than a plain enum constant created via
 * {@code EnumHelper.addArmorMaterial(...)} - the original 1.12.2 approach still visible, unported, in
 * {@code ThaumcraftMaterials.ARMORMAT_THAUMIUM}. Reproduces that entry's exact stats: durability
 * multiplier 25, protection {@code [2,5,6,2]} (boots/legs/chest/helm), enchantability 25, iron equip
 * sound, toughness 1.0, no knockback resistance.
 *
 * 1.21.1 still uses the older {@code ArmorMaterial.Layer} texture system (a plain
 * {@code textures/models/armor/<name>_layer_1/2.png} pair) rather than the newer 1.21.2+
 * {@code EquipmentAsset} registry, which matches the original's existing
 * {@code textures/entity/armor/thaumium_1.png}/{@code _2.png} files directly - no extra asset-registry
 * plumbing needed.
 */
public final class ThaumcraftArmorMaterials {

	public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, "thaumcraft");

	public static final Holder<ArmorMaterial> THAUMIUM = ARMOR_MATERIALS.register("thaumium",
			() -> new ArmorMaterial(
					Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
						m.put(ArmorItem.Type.BOOTS, 2);
						m.put(ArmorItem.Type.LEGGINGS, 5);
						m.put(ArmorItem.Type.CHESTPLATE, 6);
						m.put(ArmorItem.Type.HELMET, 2);
						m.put(ArmorItem.Type.BODY, 6);
					}),
					25,
					SoundEvents.ARMOR_EQUIP_IRON,
					() -> Ingredient.of(ItemsTC.ingotThaumium),
					List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath("thaumcraft", "thaumium"))),
					1.0F,
					0.0F));

	/** Replaces the original's {@code ThaumcraftMaterials.ARMORMAT_SPECIAL} ({@code EnumHelper.addArmorMaterial(
	 * "SPECIAL", "SPECIAL", 25, [1,2,3,1], 25, ITEM_ARMOR_EQUIP_LEATHER, 1.0F)}) - only ever used by
	 * {@link thaumcraft.common.items.armor.ItemBootsTraveller}, so named for that rather than kept generic. */
	public static final Holder<ArmorMaterial> TRAVELLER = ARMOR_MATERIALS.register("traveller",
			() -> new ArmorMaterial(
					Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
						m.put(ArmorItem.Type.BOOTS, 1);
						m.put(ArmorItem.Type.LEGGINGS, 2);
						m.put(ArmorItem.Type.CHESTPLATE, 3);
						m.put(ArmorItem.Type.HELMET, 1);
						m.put(ArmorItem.Type.BODY, 3);
					}),
					25,
					SoundEvents.ARMOR_EQUIP_LEATHER,
					() -> Ingredient.of(Items.LEATHER),
					List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath("thaumcraft", "traveller"))),
					1.0F,
					0.0F));

	private ThaumcraftArmorMaterials() {
	}
}

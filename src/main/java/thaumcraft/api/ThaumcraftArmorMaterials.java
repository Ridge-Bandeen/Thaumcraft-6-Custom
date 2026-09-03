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

	/** Same stats as {@link #TRAVELLER} (the original shared one {@code ARMORMAT_SPECIAL} material between
	 * Boots of the Traveller and the Robes set) - kept as a separate registry entry purely because a modern
	 * {@code ArmorMaterial}'s texture {@code Layer} is baked into the material itself, unlike the original's
	 * per-item {@code getArmorTexture} override, and Robes needs its own distinct texture
	 * ({@code robes_layer_1/2.png}, not {@code traveller_layer_2.png}). See
	 * {@link thaumcraft.common.items.armor.ItemRobeArmor}'s own doc comment. */
	public static final Holder<ArmorMaterial> ROBES = ARMOR_MATERIALS.register("robes",
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
					() -> Ingredient.of(ItemsTC.fabric),
					List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath("thaumcraft", "robes"))),
					1.0F,
					0.0F));

	/** {@code ThaumcraftMaterials.ARMORMAT_CULTIST_PLATE} from the original (durability multiplier 18 -
	 * applied per item at each {@code ItemsTC.crimsonPlate*} registration site via {@code
	 * ArmorItem.Type.X.getDurability(18)}, same convention {@link #THAUMIUM}'s own doc comment explains;
	 * a modern {@code ArmorMaterial} record has no durability field of its own), protection {@code
	 * [2,5,6,2]}, enchantability 13, iron equip sound, no toughness. Used by {@link
	 * thaumcraft.common.entities.monster.cult.EntityCultistKnight}'s helm/chest/legs drops. */
	public static final Holder<ArmorMaterial> CULTIST_PLATE = ARMOR_MATERIALS.register("cultist_plate",
			() -> new ArmorMaterial(
					Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
						m.put(ArmorItem.Type.BOOTS, 2);
						m.put(ArmorItem.Type.LEGGINGS, 5);
						m.put(ArmorItem.Type.CHESTPLATE, 6);
						m.put(ArmorItem.Type.HELMET, 2);
						m.put(ArmorItem.Type.BODY, 6);
					}),
					13,
					SoundEvents.ARMOR_EQUIP_IRON,
					() -> Ingredient.of(Items.IRON_INGOT),
					List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath("thaumcraft", "cultist_plate"))),
					0.0F,
					0.0F));

	/** The original's own {@code ItemCultistBoots} reused plain vanilla {@code ItemArmor.ArmorMaterial.IRON}
	 * wholesale (same durability/protection/enchantability/sound/toughness as real iron boots) while only
	 * overriding its own {@code getArmorTexture} for a custom look. A modern {@code ArmorMaterial} bakes the
	 * texture into the material itself, so matching both "iron-identical stats" and "custom texture" needs
	 * its own registry entry mirroring vanilla iron's real, well-known boots stats (durability multiplier
	 * 15 - applied at the {@code ItemsTC.crimsonBoots} registration site, same as {@link #CULTIST_PLATE} -
	 * protection 2, enchantability 9, iron equip sound, no toughness) rather than reusing {@code
	 * Items.IRON_BOOTS}' material directly. */
	public static final Holder<ArmorMaterial> CULTIST_BOOTS = ARMOR_MATERIALS.register("cultist_boots",
			() -> new ArmorMaterial(
					Util.make(new EnumMap<>(ArmorItem.Type.class), m -> m.put(ArmorItem.Type.BOOTS, 2)),
					9,
					SoundEvents.ARMOR_EQUIP_IRON,
					() -> Ingredient.of(Items.IRON_INGOT),
					List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath("thaumcraft", "cultist_boots"))),
					0.0F,
					0.0F));

	private ThaumcraftArmorMaterials() {
	}
}

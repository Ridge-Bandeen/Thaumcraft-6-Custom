package thaumcraft.api.crafting;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry home for Thaumcraft's custom {@link RecipeType}s/{@link RecipeSerializer}s. Recipes themselves
 * are loaded as JSON under {@code data/thaumcraft/recipe/} once a datapack references these serializers
 * via their {@code "type"} field (e.g. {@code "thaumcraft:arcane_shaped"}).
 */
public final class ThaumcraftRecipeTypes {

	private static final String MODID = "thaumcraft";

	public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, MODID);
	public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

	/** Shared by both arcane recipe shapes - see {@link ArcaneRecipe}. */
	public static final Supplier<RecipeType<ArcaneRecipe>> ARCANE_TYPE = registerType("arcane");

	public static final Supplier<RecipeSerializer<ArcaneShapedRecipe>> ARCANE_SHAPED_SERIALIZER =
			RECIPE_SERIALIZERS.register("arcane_shaped", () -> new ArcaneShapedRecipe.Serializer());

	public static final Supplier<RecipeSerializer<ArcaneShapelessRecipe>> ARCANE_SHAPELESS_SERIALIZER =
			RECIPE_SERIALIZERS.register("arcane_shapeless", () -> new ArcaneShapelessRecipe.Serializer());

	public static final Supplier<RecipeType<CrucibleRecipe>> CRUCIBLE_TYPE = registerType("crucible");
	public static final Supplier<RecipeSerializer<CrucibleRecipe>> CRUCIBLE_SERIALIZER =
			RECIPE_SERIALIZERS.register("crucible", () -> new CrucibleRecipe.Serializer());

	public static final Supplier<RecipeType<InfusionRecipe>> INFUSION_TYPE = registerType("infusion");
	public static final Supplier<RecipeSerializer<InfusionRecipe>> INFUSION_SERIALIZER =
			RECIPE_SERIALIZERS.register("infusion", () -> new InfusionRecipe.Serializer());

	/** {@link thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe} is a real subclass of
	 * {@link InfusionRecipe}, sharing {@link #INFUSION_TYPE} with a second serializer - same "one shared
	 * type, one serializer per concrete shape" pattern {@link #ARCANE_TYPE} already uses for its shaped/
	 * shapeless pair. {@link net.minecraft.world.item.crafting.RecipeManager#getRecipeFor} looks up by type,
	 * not serializer, so {@code TileInfusionMatrix}'s existing lookup needs no changes to also find these. */
	public static final Supplier<RecipeSerializer<thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe>> INFUSION_ENCHANTMENT_SERIALIZER =
			RECIPE_SERIALIZERS.register("infusion_enchantment", () -> new thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe.Serializer());

	/** A third shape sharing {@link #INFUSION_TYPE} - see {@link thaumcraft.common.lib.crafting.InfusionCrystalRecipe}'s
	 * own doc comment for why this exists instead of trying to express "central item must be a crystal_essence
	 * of this one specific Aspect" as a plain JSON ingredient. */
	public static final Supplier<RecipeSerializer<thaumcraft.common.lib.crafting.InfusionCrystalRecipe>> INFUSION_CRYSTAL_SERIALIZER =
			RECIPE_SERIALIZERS.register("infusion_crystal", () -> new thaumcraft.common.lib.crafting.InfusionCrystalRecipe.Serializer());

	private static <T extends net.minecraft.world.item.crafting.Recipe<?>> Supplier<RecipeType<T>> registerType(String name) {
		return RECIPE_TYPES.register(name, () -> new RecipeType<T>() {
			@Override
			public String toString() {
				return ResourceLocation.fromNamespaceAndPath(MODID, name).toString();
			}
		});
	}

	private ThaumcraftRecipeTypes() {
	}
}

package thaumcraft.common.lib.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;

/**
 * {@code thaumcraft:infernal_smelting}: what the Infernal Furnace makes of an item, checked before the vanilla furnace
 * recipes. Same JSON as a vanilla {@code minecraft:smelting} recipe ({@code ingredient}, {@code result},
 * {@code experience}; {@code cookingtime} is accepted but unused - the furnace keeps its own pace). Thaumcraft ships
 * none: by default the furnace smelts exactly what a furnace does, as the original. For addons and packs.
 */
public class InfernalSmeltingRecipe extends AbstractCookingRecipe {

	public InfernalSmeltingRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
		super(ThaumcraftRecipeTypes.INFERNAL_SMELTING_TYPE.get(), group, category, ingredient, result, experience, cookingTime);
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.INFERNAL_SMELTING_SERIALIZER.get();
	}
}

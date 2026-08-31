package thaumcraft.api.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;

/**
 * Plain JUnit coverage for {@link ArcaneShapelessRecipe#matches} - the shapeless counterpart to
 * {@link ArcaneShapedRecipeTest}. The crystal-ring semantics are the exact same private-method logic as
 * the shaped recipe (copy-pasted, per the class's own doc comment), so this doesn't re-exercise every edge
 * case there - just enough to confirm it's wired up, plus what's actually different here: unordered
 * (bipartite) ingredient matching instead of a fixed shape.
 */
class ArcaneShapelessRecipeTest {

	private static ArcaneShapelessRecipe stickAndCobbleRecipe(AspectList crystals) {
		NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STICK), Ingredient.of(Items.COBBLESTONE));
		return new ArcaneShapelessRecipe("thaumcraft", "FIRSTSTEPS", 10, crystals, ingredients, new ItemStack(Items.TORCH));
	}

	@Test
	void ingredientsMatchRegardlessOfGridOrder() {
		ArcaneShapelessRecipe recipe = stickAndCobbleRecipe(null);

		CraftingInput inOrder = CraftingInput.of(2, 1, List.of(new ItemStack(Items.STICK), new ItemStack(Items.COBBLESTONE)));
		assertTrue(recipe.matches(new ArcaneCraftingInput(inOrder, List.of()), null));

		CraftingInput reversed = CraftingInput.of(2, 1, List.of(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.STICK)));
		assertTrue(recipe.matches(new ArcaneCraftingInput(reversed, List.of()), null), "shapeless matching should not care about slot order");
	}

	@Test
	void wrongIngredientCountNeverMatches() {
		ArcaneShapelessRecipe recipe = stickAndCobbleRecipe(null);

		CraftingInput tooFew = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK)));
		assertFalse(recipe.matches(new ArcaneCraftingInput(tooFew, List.of()), null), "missing an ingredient should never match");

		CraftingInput tooMany = CraftingInput.of(3, 1, List.of(new ItemStack(Items.STICK), new ItemStack(Items.COBBLESTONE), new ItemStack(Items.DIRT)));
		assertFalse(recipe.matches(new ArcaneCraftingInput(tooMany, List.of()), null), "an extra unrelated ingredient should never match");
	}

	@Test
	void duplicateIngredientRequirementNeedsThatManyStacks() {
		NonNullList<Ingredient> twoSticks = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STICK), Ingredient.of(Items.STICK));
		ArcaneShapelessRecipe recipe = new ArcaneShapelessRecipe("thaumcraft", "FIRSTSTEPS", 10, null, twoSticks, new ItemStack(Items.TORCH));

		CraftingInput oneStickOneDirt = CraftingInput.of(2, 1, List.of(new ItemStack(Items.STICK), new ItemStack(Items.DIRT)));
		assertFalse(recipe.matches(new ArcaneCraftingInput(oneStickOneDirt, List.of()), null), "one stick can't satisfy a requirement for two");

		CraftingInput twoStickStacks = CraftingInput.of(2, 1, List.of(new ItemStack(Items.STICK), new ItemStack(Items.STICK)));
		assertTrue(recipe.matches(new ArcaneCraftingInput(twoStickStacks, List.of()), null));
	}

	@Test
	void crystalRequirementIsCheckedTheSameWayAsTheShapedRecipe() {
		AspectList required = new AspectList().add(Aspect.FIRE, 2);
		ArcaneShapelessRecipe recipe = stickAndCobbleRecipe(required);
		CraftingInput grid = CraftingInput.of(2, 1, List.of(new ItemStack(Items.STICK), new ItemStack(Items.COBBLESTONE)));

		assertFalse(recipe.matches(new ArcaneCraftingInput(grid, List.of()), null), "an empty ring should not satisfy a real crystal requirement");

		ItemStack crystal = new ItemStack(ItemsTC.crystalEssence.get(), 2);
		((ItemGenericEssentiaContainer) crystal.getItem()).setAspects(crystal, new AspectList().add(Aspect.FIRE, 1));
		assertTrue(recipe.matches(new ArcaneCraftingInput(grid, List.of(crystal)), null));
	}
}

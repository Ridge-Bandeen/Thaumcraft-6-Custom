package thaumcraft.api.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;

/**
 * Plain JUnit coverage for {@link ArcaneShapedRecipe#matches}, the arcane workbench's core recipe-matching
 * logic. Runnable as plain JUnit (not GameTest) because {@code matches}'s {@code Level} parameter is
 * completely unused in the body - it only touches the pattern and the crystal ring, neither of which needs
 * a live world. Registry-backed objects (Ingredient/ItemStack against real registered items) work fine
 * here because the {@code neoForge.unitTest} DSL still boots the real game environment, just without
 * spinning up a per-test dedicated server the way GameTest does.
 */
class ArcaneShapedRecipeTest {

	private static ItemStack crystalOf(Aspect aspect, int amount) {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get(), amount);
		((ItemGenericEssentiaContainer) stack.getItem()).setAspects(stack, new AspectList().add(aspect, 1));
		return stack;
	}

	private static ArcaneShapedRecipe stickPlusOneCobbleRecipe(AspectList crystals) {
		ShapedRecipePattern pattern = ShapedRecipePattern.of(Map.of('S', Ingredient.of(Items.STICK)), "S");
		return new ArcaneShapedRecipe("thaumcraft", "FIRSTSTEPS", 10, crystals, pattern, new ItemStack(Items.COBBLESTONE));
	}

	@Test
	void shapeMustMatchRegardlessOfCrystalRing() {
		ArcaneShapedRecipe recipe = stickPlusOneCobbleRecipe(null);

		CraftingInput matchingGrid = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK)));
		assertTrue(recipe.matches(new ArcaneCraftingInput(matchingGrid, List.of()), null));

		CraftingInput wrongGrid = CraftingInput.of(1, 1, List.of(new ItemStack(Items.DIRT)));
		assertFalse(recipe.matches(new ArcaneCraftingInput(wrongGrid, List.of()), null));
	}

	@Test
	void noCrystalRequirementMatchesRegardlessOfRingContents() {
		ArcaneShapedRecipe recipe = stickPlusOneCobbleRecipe(new AspectList());
		CraftingInput grid = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK)));

		assertTrue(recipe.matches(new ArcaneCraftingInput(grid, List.of()), null), "an empty crystal requirement should match an empty ring");
		assertTrue(recipe.matches(new ArcaneCraftingInput(grid, List.of(crystalOf(Aspect.WATER, 5))), null),
				"an empty crystal requirement should match regardless of what's actually in the ring");
	}

	@Test
	void crystalRequirementNeedsAMatchingSlotWithEnoughAmount() {
		AspectList required = new AspectList().add(Aspect.FIRE, 3);
		ArcaneShapedRecipe recipe = stickPlusOneCobbleRecipe(required);
		CraftingInput grid = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK)));

		assertFalse(recipe.matches(new ArcaneCraftingInput(grid, List.of()), null), "an empty ring should not satisfy a real crystal requirement");

		assertFalse(recipe.matches(new ArcaneCraftingInput(grid, List.of(crystalOf(Aspect.FIRE, 2))), null),
				"a matching aspect but too small a stack should not satisfy the requirement");

		assertTrue(recipe.matches(new ArcaneCraftingInput(grid, List.of(crystalOf(Aspect.FIRE, 3))), null), "exactly enough should satisfy it");
		assertTrue(recipe.matches(new ArcaneCraftingInput(grid, List.of(crystalOf(Aspect.FIRE, 10))), null), "more than enough should also satisfy it");
	}

	@Test
	void wrongAspectInTheRingDoesNotSatisfyTheRequirement() {
		AspectList required = new AspectList().add(Aspect.FIRE, 1);
		ArcaneShapedRecipe recipe = stickPlusOneCobbleRecipe(required);
		CraftingInput grid = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK)));

		assertFalse(recipe.matches(new ArcaneCraftingInput(grid, List.of(crystalOf(Aspect.WATER, 10))), null),
				"a large stack of the wrong aspect should never satisfy a different aspect's requirement");
	}

	@Test
	void eachRequiredAspectNeedsItsOwnSlotNotASharedStack() {
		AspectList required = new AspectList().add(Aspect.FIRE, 1).add(Aspect.WATER, 1);
		ArcaneShapedRecipe recipe = stickPlusOneCobbleRecipe(required);
		CraftingInput grid = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK)));

		// Only FIRE present - WATER's requirement is unmet even though FIRE's is.
		assertFalse(recipe.matches(new ArcaneCraftingInput(grid, List.of(crystalOf(Aspect.FIRE, 5))), null));

		// Both present in separate slots - should match.
		assertTrue(recipe.matches(new ArcaneCraftingInput(grid, List.of(crystalOf(Aspect.FIRE, 5), crystalOf(Aspect.WATER, 5))), null));
	}

	@Test
	void multiAspectStackDoesNotCountForAnySingleRequirement() {
		// matchesCrystals requires stackAspects.size() == 1 - a crystal carrying more than one aspect
		// (not something craft_essence normally has, but the check exists for a reason) shouldn't satisfy
		// a single-aspect requirement even if the amount of that one aspect would otherwise be enough.
		AspectList required = new AspectList().add(Aspect.FIRE, 1);
		ArcaneShapedRecipe recipe = stickPlusOneCobbleRecipe(required);
		CraftingInput grid = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK)));

		ItemStack mixedCrystal = new ItemStack(ItemsTC.crystalEssence.get(), 5);
		((ItemGenericEssentiaContainer) mixedCrystal.getItem()).setAspects(mixedCrystal, new AspectList().add(Aspect.FIRE, 5).add(Aspect.WATER, 5));

		assertFalse(recipe.matches(new ArcaneCraftingInput(grid, List.of(mixedCrystal)), null));
	}
}

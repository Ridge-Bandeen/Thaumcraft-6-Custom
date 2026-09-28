package thaumcraft.common.lib.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * What a research stage's {@code "recipes"} entry points at. The shipped research data still names the original's
 * own recipe catalog keys - arcane/crucible/infusion catalog names ({@code thaumcraft:Goggles},
 * {@code thaumcraft:BathSalts}), crafting-recipe groups ({@code thaumcraft:baubles_stuff}, one bookmark paging
 * through several recipes) and the handful of "fake" display-only recipes - none of which are this port's recipe
 * ids. The original resolved a key through {@code CommonInternals.getCatalogRecipe}, then
 * {@code getCatalogRecipeFake}, then the vanilla registry, then {@code ConfigRecipes.recipeGroups}; this is that
 * same lookup against a table built from the original's {@code ConfigRecipes} registrations.
 *
 * <p>Keys are matched case-insensitively: modern resource locations can't hold the original's CamelCase keys, so the
 * research loader lower-cases them. A key with no table entry falls back to a recipe of the same id.
 */
public final class ResearchRecipeCatalog {

	private ResearchRecipeCatalog() {
	}

	/** Original catalog key -> this port's recipe ids, in the original's page order. */
	private static final Map<String, List<String>> LEGACY = LegacyRecipeKeys.TABLE;

	/** Every recipe a research {@code recipes} key stands for, in page order; empty when none are loaded. */
	public static List<RecipeHolder<?>> resolve(RecipeManager recipes, ResourceLocation key) {
		List<RecipeHolder<?>> out = new ArrayList<>();
		List<String> ids = LEGACY.get(key.toString().toLowerCase(java.util.Locale.ROOT));
		if (ids == null) {
			recipes.byKey(key).ifPresentOrElse(out::add, () -> {
				ResourceLocation lower = ResourceLocation.tryParse(key.toString().toLowerCase());
				if (lower != null) recipes.byKey(lower).ifPresent(out::add);
			});
			return out;
		}
		for (String id : ids) {
			ResourceLocation rl = ResourceLocation.tryParse(id);
			if (rl != null) recipes.byKey(rl).ifPresent(out::add);
		}
		return out;
	}
}

package thaumcraft.api.crafting;
import net.minecraft.world.item.crafting.Recipe;
import thaumcraft.api.aspects.AspectList;

/**
 * Marker shared by {@link ArcaneShapedRecipe} and {@link ArcaneShapelessRecipe}, the same way vanilla's
 * {@code CraftingRecipe} is shared by {@code ShapedRecipe}/{@code ShapelessRecipe} - lets the arcane
 * workbench look both up through a single {@code RecipeType} (see {@link ThaumcraftRecipeTypes#ARCANE_TYPE}).
 */
public interface ArcaneRecipe extends Recipe<ArcaneCraftingInput> {

	int getVis();

	AspectList getCrystals();

	/** Not extending {@link IThaumcraftRecipe} here - its getGroup() abstract declaration conflicts with
	 *  Recipe's default getGroup() at the interface level. Implementations still implement both directly
	 *  (their own concrete getGroup() resolves the conflict at the class level, same as CrucibleRecipe/
	 *  InfusionRecipe already do). */
	String getResearch();
}

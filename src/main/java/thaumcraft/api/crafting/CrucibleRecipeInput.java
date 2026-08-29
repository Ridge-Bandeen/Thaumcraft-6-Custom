package thaumcraft.api.crafting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import thaumcraft.api.aspects.AspectList;

/**
 * @param catalyst the item dropped into the crucible to attempt a craft
 * @param aspects the aspects currently dissolved in the crucible's cauldron
 */
public record CrucibleRecipeInput(ItemStack catalyst, AspectList aspects) implements RecipeInput {
	@Override
	public ItemStack getItem(int index) {
		if (index != 0) throw new IllegalArgumentException("No item for index " + index);
		return catalyst;
	}

	@Override
	public int size() {
		return 1;
	}
}

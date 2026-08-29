package thaumcraft.api.crafting;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * @param central the item placed in the centre of the infusion matrix
 * @param components the items placed around it
 */
public record InfusionRecipeInput(ItemStack central, List<ItemStack> components) implements RecipeInput {
	@Override
	public ItemStack getItem(int index) {
		return index == 0 ? central : components.get(index - 1);
	}

	@Override
	public int size() {
		return 1 + components.size();
	}
}

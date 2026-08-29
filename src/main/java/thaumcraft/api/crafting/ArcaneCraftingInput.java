package thaumcraft.api.crafting;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * The recipe input for the arcane workbench. In 1.12.2 this was just an {@code InventoryCrafting} with a size
 * of 15+ (the normal 3x3 grid plus 6 "crystal ring" slots around it), detected via
 * {@code inv.getSizeInventory() >= 15} and an {@code instanceof IArcaneWorkbench} check. The modern recipe
 * system (1.20.5+) replaced the single-grid-shape model with a {@link RecipeInput} interface mods can
 * implement for exactly this kind of non-vanilla-shaped crafting station, so the crystal ring is now a
 * first-class part of the input instead of extra trailing slots.
 *
 * @param grid the normal 3x3 (or larger, if a future workbench variant raises the limit) crafting grid
 * @param crystals the contents of the crystal ring slots, used to pay the recipe's aspect cost
 */
public record ArcaneCraftingInput(CraftingInput grid, List<ItemStack> crystals) implements RecipeInput {
	@Override
	public ItemStack getItem(int index) {
		return grid.getItem(index);
	}

	@Override
	public int size() {
		return grid.size();
	}
}

package thaumcraft.common.lib.crafting;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code RecipeTripleMeatTreat} - a real custom {@link CustomRecipe} (same real
 * vanilla mechanism {@code RepairItemRecipe}/{@code ShieldDecorationRecipe} use), not expressible as a plain
 * JSON shapeless recipe: 3 <b>distinct</b> chunk types (no duplicates, any of the 6 real
 * {@link ItemsTC#chunkBeef}/etc.) plus exactly 1 sugar, anywhere in the grid, in any real quantity of empty
 * slots - matching the true original's own real {@code matches} logic exactly.
 */
public class RecipeTripleMeatTreat extends CustomRecipe {

	public RecipeTripleMeatTreat(CraftingBookCategory category) {
		super(category);
	}

	private static boolean isChunk(Item item) {
		return item == ItemsTC.chunkBeef.get() || item == ItemsTC.chunkChicken.get() || item == ItemsTC.chunkPork.get()
				|| item == ItemsTC.chunkFish.get() || item == ItemsTC.chunkRabbit.get() || item == ItemsTC.chunkMutton.get();
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		boolean sugar = false;
		Set<Item> meats = new HashSet<>();
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) continue;
			if (stack.is(Items.SUGAR)) {
				if (sugar) return false;
				sugar = true;
			} else if (isChunk(stack.getItem())) {
				if (!meats.add(stack.getItem()) || meats.size() > 3) return false;
			} else {
				return false;
			}
		}
		return sugar && meats.size() == 3;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		return new ItemStack(ItemsTC.tripleMeatTreat.get());
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 4;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.TRIPLE_MEAT_TREAT_SERIALIZER.get();
	}
}

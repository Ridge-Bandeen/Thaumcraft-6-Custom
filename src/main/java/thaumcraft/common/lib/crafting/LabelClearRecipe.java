package thaumcraft.common.lib.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.ThaumcraftDataComponents;

/**
 * Replaces the old (1.12.2) {@code JarLabelNull} recipe - washing the dye back off a Label, the other real
 * half of the original's label recipe family alongside {@link LabelDyeRecipe}. The original matched this by
 * requiring damage value 1 specifically (its dyed metadata variant); this port has one Label item with the
 * dyed/blank distinction carried by the {@code ASPECTS} component's presence, so the real {@code CustomRecipe}
 * check is that presence rather than an item identity a plain JSON ingredient could already express - a plain
 * {@code {"item":"thaumcraft:label"}} shapeless recipe would also (harmlessly, but needlessly) match an
 * already-blank label and offer a no-op "craft a label from a label" recipe-book entry.
 */
public class LabelClearRecipe extends CustomRecipe {

	public LabelClearRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		boolean sawDyedLabel = false;
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) continue;
			if (!stack.is(ItemsTC.label.get()) || !stack.has(ThaumcraftDataComponents.ASPECTS.get())) return false;
			if (sawDyedLabel) return false;
			sawDyedLabel = true;
		}
		return sawDyedLabel;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		return new ItemStack(ItemsTC.label.get());
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 1;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.LABEL_CLEAR_SERIALIZER.get();
	}
}

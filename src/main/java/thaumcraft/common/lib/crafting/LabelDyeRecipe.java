package thaumcraft.common.lib.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.ThaumcraftDataComponents;

/**
 * Replaces the old (1.12.2) {@code label_<aspect>} recipe family - the true original registered one real
 * shapeless recipe per {@code Aspect}, each an {@code IngredientNBTTC} matching a filled Phial by its exact
 * NBT contents. {@link thaumcraft.common.items.consumables.ItemLabel}'s own doc comment already flagged this
 * as the one deliberately deferred piece of its recipe support.
 *
 * <p>Same real technique {@link InfusionPotionRecipe}/{@link InfusionCrystalRecipe} already established for
 * "the item's own real data, not just its identity, decides the match" - rather than build a whole new
 * {@code ICustomIngredient}/{@code IngredientType} plumbing layer (a phial is one item regardless of aspect;
 * nothing at the JSON level can tell them apart), this is a real {@link CustomRecipe} (the same real vanilla
 * mechanism {@link RecipeTripleMeatTreat} already used, itself modeled on vanilla's own
 * {@code ArmorDyeRecipe}/{@code BannerDuplicateRecipe} - a dynamic-output recipe whose result depends on the
 * actual input, not a fixed JSON stack).
 *
 * <p>Real, deliberate simplification versus registering 40-odd near-identical recipes: one {@code matches}
 * accepts <i>any</i> single filled Phial (exactly one aspect, {@link thaumcraft.common.items.consumables.ItemPhial#BASE}
 * essentia) alongside exactly one blank Label, and {@link #assemble} derives the output aspect from whichever
 * one was actually used - functionally identical to the original's per-aspect recipe family (every one of
 * them differed only in which aspect they matched and produced), with no behavioral loss: every real aspect
 * still dyes a label, and only that aspect.
 *
 * <p>Real behavior kept from the original: the Phial itself is never spent, essentia content included. The
 * original's own {@code CraftingEvents} achieved that with a real trick - {@code var3.grow(1)} on the phial
 * still sitting in the crafting grid right before vanilla's own post-craft consumption removed 1, a net
 * no-op. The modern, direct equivalent is {@link #getRemainingItems} - the exact real mechanism vanilla's own
 * bucket/glass-bottle recipes already use for "this ingredient comes back unconsumed" - returning a copy of
 * the same phial stack rather than {@link ItemStack#EMPTY}.
 */
public class LabelDyeRecipe extends CustomRecipe {

	public LabelDyeRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		boolean sawBlankLabel = false;
		Aspect phialAspect = null;
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) continue;
			if (stack.is(ItemsTC.label.get())) {
				if (sawBlankLabel || stack.has(ThaumcraftDataComponents.ASPECTS.get())) return false;
				sawBlankLabel = true;
			} else if (stack.is(ItemsTC.phial.get())) {
				if (phialAspect != null) return false;
				AspectList aspects = stack.get(ThaumcraftDataComponents.ASPECTS.get());
				if (aspects == null || aspects.size() != 1) return false;
				phialAspect = aspects.getAspects()[0];
			} else {
				return false;
			}
		}
		return sawBlankLabel && phialAspect != null;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		for (int i = 0; i < input.size(); i++) {
			AspectList aspects = input.getItem(i).get(ThaumcraftDataComponents.ASPECTS.get());
			if (aspects != null && aspects.size() == 1) {
				ItemStack result = new ItemStack(ItemsTC.label.get());
				// The original's own real amount for a dyed label - a flag, not an essentia store, matching
				// its own `new AspectList().add(aspect, 1)`.
				result.set(ThaumcraftDataComponents.ASPECTS.get(), new AspectList().add(aspects.getAspects()[0], 1));
				return result;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.is(ItemsTC.phial.get()) && stack.has(ThaumcraftDataComponents.ASPECTS.get())) {
				remaining.set(i, stack.copyWithCount(1));
			}
		}
		return remaining;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.LABEL_DYE_SERIALIZER.get();
	}
}

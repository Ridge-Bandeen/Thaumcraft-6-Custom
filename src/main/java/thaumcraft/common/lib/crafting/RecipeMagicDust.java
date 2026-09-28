package thaumcraft.common.lib.crafting;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.api.items.ItemsTC;

/**
 * The original's {@code RecipeMagicDust} (Salis Mundus): one bowl, one flint, one redstone and three essence crystals
 * of three different aspects, anywhere in the grid. A plain shapeless recipe can't say "different", which is why the
 * original needed this class too.
 */
public class RecipeMagicDust extends CustomRecipe {

	public RecipeMagicDust(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		boolean bowl = false;
		boolean flint = false;
		boolean redstone = false;
		Set<Aspect> crystals = new HashSet<>();
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) continue;
			if (stack.is(Items.BOWL)) {
				if (bowl) return false;
				bowl = true;
			} else if (stack.is(Items.FLINT)) {
				if (flint) return false;
				flint = true;
			} else if (stack.is(Items.REDSTONE)) {
				if (redstone) return false;
				redstone = true;
			} else if (stack.is(ItemsTC.crystalEssence.get()) && stack.getItem() instanceof IEssentiaContainerItem container) {
				AspectList aspects = container.getAspects(stack);
				if (aspects == null || aspects.size() == 0) return false;
				if (crystals.size() >= 3 || !crystals.add(aspects.getAspects()[0])) return false;
			} else {
				return false;
			}
		}
		return bowl && redstone && flint && crystals.size() == 3;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		return new ItemStack(ItemsTC.salisMundus.get());
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 6;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.MAGIC_DUST_SERIALIZER.get();
	}
}

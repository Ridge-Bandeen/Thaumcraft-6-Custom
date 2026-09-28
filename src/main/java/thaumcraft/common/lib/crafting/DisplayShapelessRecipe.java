package thaumcraft.common.lib.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;

/**
 * A shapeless recipe that exists only to be shown in the Thaumonomicon - the original's {@code addFakeCraftingRecipe}
 * catalog entries, which were never registered as real recipes. Stands in for recipes whose real matching can't be
 * written as a plain ingredient list (Salis Mundus' three different crystals, the Triple Meat Treat's three different
 * meats): the book shows these ingredients, the real {@link RecipeMagicDust}/{@link RecipeTripleMeatTreat} does the
 * crafting. Never matches, and is hidden from the recipe book.
 */
public class DisplayShapelessRecipe extends ShapelessRecipe {

	private final ItemStack shownResult;

	public DisplayShapelessRecipe(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
		super(group, category, result, ingredients);
		this.shownResult = result;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return false;
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.DISPLAY_SHAPELESS_SERIALIZER.get();
	}

	public static class Serializer implements RecipeSerializer<DisplayShapelessRecipe> {
		private static final MapCodec<DisplayShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(ShapelessRecipe::getGroup),
				CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapelessRecipe::category),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.shownResult),
				Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(list -> {
					Ingredient[] a = list.toArray(Ingredient[]::new);
					return a.length == 0 ? DataResult.<NonNullList<Ingredient>>error(() -> "No ingredients")
							: DataResult.success(NonNullList.of(Ingredient.EMPTY, a));
				}, DataResult::success).forGetter(ShapelessRecipe::getIngredients)
		).apply(inst, DisplayShapelessRecipe::new));

		private static final StreamCodec<RegistryFriendlyByteBuf, DisplayShapelessRecipe> STREAM_CODEC = StreamCodec.of(
				(buf, r) -> {
					buf.writeUtf(r.getGroup());
					buf.writeEnum(r.category());
					buf.writeVarInt(r.getIngredients().size());
					for (Ingredient i : r.getIngredients()) Ingredient.CONTENTS_STREAM_CODEC.encode(buf, i);
					ItemStack.STREAM_CODEC.encode(buf, r.shownResult);
				},
				buf -> {
					String group = buf.readUtf();
					CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
					int n = buf.readVarInt();
					NonNullList<Ingredient> ings = NonNullList.withSize(n, Ingredient.EMPTY);
					for (int i = 0; i < n; i++) ings.set(i, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
					return new DisplayShapelessRecipe(group, category, ItemStack.STREAM_CODEC.decode(buf), ings);
				});

		@Override
		public MapCodec<DisplayShapelessRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, DisplayShapelessRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}

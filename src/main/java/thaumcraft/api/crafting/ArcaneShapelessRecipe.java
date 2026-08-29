package thaumcraft.api.crafting;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.RecipeMatcher;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;

/**
 * Replaces the old (1.12.2) {@code ShapelessArcaneRecipe}. See {@link ArcaneShapedRecipe} for the general
 * story of why this is no longer a subclass of a vanilla/Forge recipe class. Shapeless matching (unlike the
 * shaped case, which reuses vanilla's own {@code ShapedRecipePattern}) uses NeoForge's {@code RecipeMatcher}
 * bipartite-matching utility directly, same as the original's Forge {@code ShapelessOreRecipe} base did.
 */
public class ArcaneShapelessRecipe implements Recipe<ArcaneCraftingInput>, IThaumcraftRecipe {

	private final String group;
	private final String research;
	private final int vis;
	private final AspectList crystals;
	private final NonNullList<Ingredient> ingredients;
	private final ItemStack result;

	public ArcaneShapelessRecipe(String group, String research, int vis, AspectList crystals, NonNullList<Ingredient> ingredients, ItemStack result) {
		this.group = group;
		this.research = research;
		this.vis = vis;
		this.crystals = crystals;
		this.ingredients = ingredients;
		this.result = result;
	}

	@Override
	public boolean matches(ArcaneCraftingInput input, Level level) {
		if (input.grid().ingredientCount() != ingredients.size()) return false;
		List<ItemStack> nonEmpty = new ArrayList<>(input.grid().ingredientCount());
		for (ItemStack stack : input.grid().items()) {
			if (!stack.isEmpty()) nonEmpty.add(stack);
		}
		return RecipeMatcher.findMatches(nonEmpty, ingredients) != null && matchesCrystals(input.crystals());
	}

	/** See {@link ArcaneShapedRecipe#matchesCrystals} - identical semantics. */
	private boolean matchesCrystals(List<ItemStack> ring) {
		if (crystals == null || crystals.size() == 0) return true;
		for (Aspect required : crystals.getAspects()) {
			int need = crystals.getAmount(required);
			boolean found = false;
			for (ItemStack stack : ring) {
				if (stack.isEmpty() || !(stack.getItem() instanceof IEssentiaContainerItem container)) continue;
				AspectList stackAspects = container.getAspects(stack);
				if (stackAspects != null && stackAspects.size() == 1 && stackAspects.getAmount(required) == 1 && stack.getCount() >= need) {
					found = true;
					break;
				}
			}
			if (!found) return false;
		}
		return true;
	}

	@Override
	public ItemStack assemble(ArcaneCraftingInput input, HolderLookup.Provider registries) {
		return result.copy();
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return result.copy();
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= ingredients.size();
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		return ingredients;
	}

	@Override
	public String getGroup() {
		return group;
	}

	@Override
	public String getResearch() {
		return research;
	}

	public int getVis() {
		return vis;
	}

	public AspectList getCrystals() {
		return crystals;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.ARCANE_SHAPELESS_SERIALIZER.get();
	}

	@Override
	public RecipeType<?> getType() {
		return ThaumcraftRecipeTypes.ARCANE_SHAPELESS_TYPE.get();
	}

	public static class Serializer implements RecipeSerializer<ArcaneShapelessRecipe> {
		public static final MapCodec<ArcaneShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
				Codec.STRING.optionalFieldOf("research", "").forGetter(r -> r.research),
				Codec.INT.optionalFieldOf("vis", 0).forGetter(r -> r.vis),
				AspectList.CODEC.optionalFieldOf("crystals", new AspectList()).forGetter(r -> r.crystals),
				Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(
						list -> list.isEmpty()
								? DataResult.error(() -> "No ingredients for shapeless arcane recipe")
								: DataResult.success(NonNullList.of(Ingredient.EMPTY, list.toArray(new Ingredient[0]))),
						DataResult::success
				).forGetter(r -> r.ingredients),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result)
			).apply(inst, ArcaneShapelessRecipe::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneShapelessRecipe> STREAM_CODEC = StreamCodec.of(
				Serializer::toNetwork, Serializer::fromNetwork
		);

		@Override
		public MapCodec<ArcaneShapelessRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, ArcaneShapelessRecipe> streamCodec() {
			return STREAM_CODEC;
		}

		private static ArcaneShapelessRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
			String group = ByteBufCodecs.STRING_UTF8.decode(buf);
			String research = ByteBufCodecs.STRING_UTF8.decode(buf);
			int vis = ByteBufCodecs.VAR_INT.decode(buf);
			AspectList crystals = ByteBufCodecs.fromCodec(AspectList.CODEC).decode(buf);
			int count = ByteBufCodecs.VAR_INT.decode(buf);
			NonNullList<Ingredient> ingredients = NonNullList.withSize(count, Ingredient.EMPTY);
			ingredients.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
			ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
			return new ArcaneShapelessRecipe(group, research, vis, crystals, ingredients, result);
		}

		private static void toNetwork(RegistryFriendlyByteBuf buf, ArcaneShapelessRecipe recipe) {
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.group);
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.research);
			ByteBufCodecs.VAR_INT.encode(buf, recipe.vis);
			ByteBufCodecs.fromCodec(AspectList.CODEC).encode(buf, recipe.crystals);
			ByteBufCodecs.VAR_INT.encode(buf, recipe.ingredients.size());
			for (Ingredient ingredient : recipe.ingredients) {
				Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
			}
			ItemStack.STREAM_CODEC.encode(buf, recipe.result);
		}
	}
}

package thaumcraft.api.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/**
 * Replaces the old (1.12.2) {@code CrucibleRecipe}. Unlike the arcane workbench recipes this one was never
 * a vanilla {@code IRecipe} to begin with - it was already its own bespoke class held in a simple catalog
 * ({@code ThaumcraftApi.getCraftingRecipes()}). It's promoted to a proper data-driven {@code Recipe} here to
 * keep all of Thaumcraft's custom crafting types consistent and datapack-editable.
 */
public class CrucibleRecipe implements Recipe<CrucibleRecipeInput>, IThaumcraftRecipe {

	private final String group;
	private final String research;
	private final ItemStack recipeOutput;
	private final Ingredient catalyst;
	private final AspectList aspects;

	public CrucibleRecipe(String group, String research, ItemStack recipeOutput, Ingredient catalyst, AspectList aspects) {
		this.group = group;
		this.research = research;
		this.recipeOutput = recipeOutput;
		this.catalyst = catalyst;
		this.aspects = aspects;
	}

	@Override
	public boolean matches(CrucibleRecipeInput input, Level level) {
		return matches(input.aspects(), input.catalyst());
	}

	/**
	 * Same signature as the original - kept for the (not yet ported) crucible tile entity to call directly
	 * without having to build a full {@link CrucibleRecipeInput} first.
	 */
	public boolean matches(AspectList itags, ItemStack cat) {
		if (!catalystMatches(cat)) return false;
		if (itags==null) return false;
		for (Aspect tag:getAspects().getAspects()) {
			if (itags.getAmount(tag)<getAspects().getAmount(tag)) return false;
		}
		return true;
	}

	public boolean catalystMatches(ItemStack cat) {
		return catalyst.test(cat);
	}

	public AspectList removeMatching(AspectList itags) {
		AspectList temptags = new AspectList();
		temptags.aspects.putAll(itags.aspects);
		for (Aspect tag:getAspects().getAspects()) {
			temptags.remove(tag, getAspects().getAmount(tag));
		}
		return temptags;
	}

	@Override
	public ItemStack assemble(CrucibleRecipeInput input, HolderLookup.Provider registries) {
		return recipeOutput.copy();
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return recipeOutput.copy();
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return true;
	}

	public ItemStack getRecipeOutput() {
		return recipeOutput;
	}

	public Ingredient getCatalyst() {
		return catalyst;
	}

	public AspectList getAspects() {
		return aspects;
	}

	@Override
	public String getGroup() {
		return group;
	}

	@Override
	public String getResearch() {
		return research;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.CRUCIBLE_SERIALIZER.get();
	}

	@Override
	public RecipeType<?> getType() {
		return ThaumcraftRecipeTypes.CRUCIBLE_TYPE.get();
	}

	public static class Serializer implements RecipeSerializer<CrucibleRecipe> {
		public static final MapCodec<CrucibleRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
				Codec.STRING.optionalFieldOf("research", "").forGetter(r -> r.research),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.recipeOutput),
				Ingredient.CODEC.fieldOf("catalyst").forGetter(r -> r.catalyst),
				AspectList.CODEC.fieldOf("aspects").forGetter(r -> r.aspects)
			).apply(inst, CrucibleRecipe::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, CrucibleRecipe> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8.cast(), r -> r.group,
				ByteBufCodecs.STRING_UTF8.cast(), r -> r.research,
				ItemStack.STREAM_CODEC, r -> r.recipeOutput,
				Ingredient.CONTENTS_STREAM_CODEC, r -> r.catalyst,
				ByteBufCodecs.fromCodec(AspectList.CODEC).cast(), r -> r.aspects,
				CrucibleRecipe::new
		);

		@Override
		public MapCodec<CrucibleRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, CrucibleRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}

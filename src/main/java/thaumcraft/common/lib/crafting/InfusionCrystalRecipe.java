package thaumcraft.common.lib.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.InfusionRecipeInput;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the original's own real "crystal_essence of one specific Aspect + seeds + salis mundus -> a real
 * crystal-cluster block" recipes (the {@code CrystalCluster*} family - {@code CRYSTALFARMER} group) - a real
 * subclass of {@link InfusionRecipe}, same shape {@link InfusionEnchantmentRecipe} already established for
 * "the central item's own real data, not just its identity, decides the match."
 *
 * <p>Real, documented technical finding: a plain JSON {@link Ingredient} can't express "any {@code
 * crystal_essence}, but only the one carrying this specific Aspect" - NeoForge's own real
 * {@code neoforge:components} custom ingredient type ({@code DataComponentIngredient}) looked like the
 * obvious modern answer, but a live {@code GameTest} round-trip (a fire-aspect crystal wrongly matching an
 * air-only recipe) showed it isn't filtering the way its own field names ({@code items}/{@code components}/
 * {@code strict}) suggest, at least not with the plain map-literal syntax this port's own existing
 * {@code crystal_essence_*.json} result-side components already use elsewhere - and chasing the exact
 * working JSON shape further wasn't worth blocking 7 real recipes on. This class checks the real Aspect
 * content directly in Java instead (via {@link ItemGenericEssentiaContainer#getAspects}), the same
 * "read the real item data programmatically" approach {@link InfusionEnchantmentRecipe} already uses for
 * infusion-enchantment levels - {@code sourceInput} itself stays a plain "any crystal_essence" {@link
 * Ingredient} as a cheap first filter, real Aspect matching happens in {@link #matches} on top of it.
 */
public class InfusionCrystalRecipe extends InfusionRecipe {

	private final Aspect requiredAspect;

	public InfusionCrystalRecipe(String group, String research, AspectList aspects, int instability,
			Aspect requiredAspect, NonNullList<Ingredient> components, ItemStack recipeOutput) {
		super(group, research, aspects, instability, Ingredient.of(ItemsTC.crystalEssence.get()), components, recipeOutput);
		this.requiredAspect = requiredAspect;
	}

	@Override
	public boolean matches(InfusionRecipeInput input, Level level) {
		if (!super.matches(input, level)) {
			return false;
		}
		ItemStack central = input.central();
		if (!(central.getItem() instanceof ItemGenericEssentiaContainer container)) {
			return false;
		}
		AspectList carried = container.getAspects(central);
		return carried != null && carried.size() == 1 && carried.getAmount(requiredAspect) > 0;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.INFUSION_CRYSTAL_SERIALIZER.get();
	}

	public static class Serializer implements RecipeSerializer<InfusionCrystalRecipe> {
		public static final MapCodec<InfusionCrystalRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(InfusionCrystalRecipe::getGroup),
				Codec.STRING.optionalFieldOf("research", "").forGetter(InfusionCrystalRecipe::getResearch),
				AspectList.CODEC.fieldOf("aspects").forGetter(InfusionCrystalRecipe::getAspects),
				Codec.INT.optionalFieldOf("instability", 0).forGetter(InfusionCrystalRecipe::getInstability),
				Codec.STRING.fieldOf("centralAspect").forGetter(r -> r.requiredAspect.getTag()),
				Ingredient.CODEC_NONEMPTY.listOf().fieldOf("components").flatXmap(
						list -> list.isEmpty()
								? DataResult.error(() -> "No components for infusion crystal recipe")
								: DataResult.success(NonNullList.of(Ingredient.EMPTY, list.toArray(new Ingredient[0]))),
						DataResult::success
				).forGetter(InfusionCrystalRecipe::getComponents),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(InfusionCrystalRecipe::getRecipeOutput)
			).apply(inst, (group, research, aspects, instability, aspectTag, components, result) ->
					new InfusionCrystalRecipe(group, research, aspects, instability, Aspect.getAspect(aspectTag), components, result)));

		public static final StreamCodec<RegistryFriendlyByteBuf, InfusionCrystalRecipe> STREAM_CODEC = StreamCodec.of(
				Serializer::toNetwork, Serializer::fromNetwork
		);

		@Override
		public MapCodec<InfusionCrystalRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, InfusionCrystalRecipe> streamCodec() {
			return STREAM_CODEC;
		}

		private static InfusionCrystalRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
			String group = ByteBufCodecs.STRING_UTF8.decode(buf);
			String research = ByteBufCodecs.STRING_UTF8.decode(buf);
			AspectList aspects = ByteBufCodecs.fromCodec(AspectList.CODEC).decode(buf);
			int instability = ByteBufCodecs.VAR_INT.decode(buf);
			String aspectTag = ByteBufCodecs.STRING_UTF8.decode(buf);
			int count = ByteBufCodecs.VAR_INT.decode(buf);
			NonNullList<Ingredient> components = NonNullList.withSize(count, Ingredient.EMPTY);
			components.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
			ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
			return new InfusionCrystalRecipe(group, research, aspects, instability, Aspect.getAspect(aspectTag), components, result);
		}

		private static void toNetwork(RegistryFriendlyByteBuf buf, InfusionCrystalRecipe recipe) {
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getGroup());
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getResearch());
			ByteBufCodecs.fromCodec(AspectList.CODEC).encode(buf, recipe.getAspects());
			ByteBufCodecs.VAR_INT.encode(buf, recipe.getInstability());
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.requiredAspect.getTag());
			ByteBufCodecs.VAR_INT.encode(buf, recipe.getComponents().size());
			for (Ingredient ingredient : recipe.getComponents()) {
				Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
			}
			ItemStack.STREAM_CODEC.encode(buf, recipe.getRecipeOutput());
		}
	}
}

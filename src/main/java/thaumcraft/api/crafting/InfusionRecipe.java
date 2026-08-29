package thaumcraft.api.crafting;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.RecipeMatcher;
import thaumcraft.api.aspects.AspectList;

/**
 * Replaces the old (1.12.2) {@code InfusionRecipe}. Like {@link CrucibleRecipe} this was already a bespoke
 * class (not a vanilla {@code IRecipe}), now promoted to a proper data-driven {@code Recipe}.
 *
 * Two behavioural changes forced by the modern {@code Recipe} interface, which - unlike the old
 * {@code matches(List, ItemStack, World, EntityPlayer)} - has no player parameter:
 * <ul>
 * <li>Research-gating ({@code ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(research)}) is no
 * longer part of {@link #matches}. {@link #getResearch()} is still exposed for the infusion altar tile
 * (once ported) to check separately after finding an ingredient match - "is this a valid recipe" and "is
 * this player allowed to craft it" are different questions anyway.</li>
 * <li>{@code recipeOutput} was typed {@code Object} to let a subclass compute a dynamic, player-dependent
 * result; that's now a concrete {@code ItemStack} for the base case, with the per-player overloads below
 * kept as separate virtual methods a subclass can still override (assemble() itself has no player either,
 * so it just falls back to the static result).</li>
 * </ul>
 */
public class InfusionRecipe implements Recipe<InfusionRecipeInput>, IThaumcraftRecipe {

	private final String group;
	private final String research;
	private final AspectList aspects;
	private final int instability;
	private final Ingredient sourceInput; // Ingredient.EMPTY if the central item can be anything
	private final NonNullList<Ingredient> components;
	private final ItemStack recipeOutput;

	public InfusionRecipe(String group, String research, AspectList aspects, int instability, Ingredient sourceInput, NonNullList<Ingredient> components, ItemStack recipeOutput) {
		this.group = group;
		this.research = research;
		this.aspects = aspects;
		this.instability = instability;
		this.sourceInput = sourceInput;
		this.components = components;
		this.recipeOutput = recipeOutput;
	}

	@Override
	public boolean matches(InfusionRecipeInput input, Level level) {
		return (sourceInput == Ingredient.EMPTY || sourceInput.test(input.central()))
				&& RecipeMatcher.findMatches(input.components(), components) != null;
	}

	@Override
	public ItemStack assemble(InfusionRecipeInput input, HolderLookup.Provider registries) {
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

	@Override
	public NonNullList<Ingredient> getIngredients() {
		return components;
	}

	public Ingredient getRecipeInput() {
		return sourceInput;
	}

	public NonNullList<Ingredient> getComponents() {
		return components;
	}

	public ItemStack getRecipeOutput() {
		return recipeOutput;
	}

	public AspectList getAspects() {
		return aspects;
	}

	public int getInstability() {
		return instability;
	}

	/** Overridable per-player result, for recipes whose output depends on more than just the ingredients. */
	public ItemStack getRecipeOutput(Player player, ItemStack input, List<ItemStack> comps) {
		return recipeOutput;
	}

	public AspectList getAspects(Player player, ItemStack input, List<ItemStack> comps) {
		return aspects;
	}

	public int getInstability(Player player, ItemStack input, List<ItemStack> comps) {
		return instability;
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
		return ThaumcraftRecipeTypes.INFUSION_SERIALIZER.get();
	}

	@Override
	public RecipeType<?> getType() {
		return ThaumcraftRecipeTypes.INFUSION_TYPE.get();
	}

	public static class Serializer implements RecipeSerializer<InfusionRecipe> {
		public static final MapCodec<InfusionRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
				Codec.STRING.optionalFieldOf("research", "").forGetter(r -> r.research),
				AspectList.CODEC.fieldOf("aspects").forGetter(r -> r.aspects),
				Codec.INT.optionalFieldOf("instability", 0).forGetter(r -> r.instability),
				Ingredient.CODEC.optionalFieldOf("central", Ingredient.EMPTY).forGetter(r -> r.sourceInput),
				Ingredient.CODEC_NONEMPTY.listOf().fieldOf("components").flatXmap(
						list -> list.isEmpty()
								? DataResult.error(() -> "No components for infusion recipe")
								: DataResult.success(NonNullList.of(Ingredient.EMPTY, list.toArray(new Ingredient[0]))),
						DataResult::success
				).forGetter(r -> r.components),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.recipeOutput)
			).apply(inst, InfusionRecipe::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, InfusionRecipe> STREAM_CODEC = StreamCodec.of(
				Serializer::toNetwork, Serializer::fromNetwork
		);

		@Override
		public MapCodec<InfusionRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, InfusionRecipe> streamCodec() {
			return STREAM_CODEC;
		}

		private static InfusionRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
			String group = ByteBufCodecs.STRING_UTF8.decode(buf);
			String research = ByteBufCodecs.STRING_UTF8.decode(buf);
			AspectList aspects = ByteBufCodecs.fromCodec(AspectList.CODEC).decode(buf);
			int instability = ByteBufCodecs.VAR_INT.decode(buf);
			Ingredient sourceInput = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
			int count = ByteBufCodecs.VAR_INT.decode(buf);
			NonNullList<Ingredient> components = NonNullList.withSize(count, Ingredient.EMPTY);
			components.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
			ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
			return new InfusionRecipe(group, research, aspects, instability, sourceInput, components, result);
		}

		private static void toNetwork(RegistryFriendlyByteBuf buf, InfusionRecipe recipe) {
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.group);
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.research);
			ByteBufCodecs.fromCodec(AspectList.CODEC).encode(buf, recipe.aspects);
			ByteBufCodecs.VAR_INT.encode(buf, recipe.instability);
			Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.sourceInput);
			ByteBufCodecs.VAR_INT.encode(buf, recipe.components.size());
			for (Ingredient ingredient : recipe.components) {
				Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
			}
			ItemStack.STREAM_CODEC.encode(buf, recipe.recipeOutput);
		}
	}
}

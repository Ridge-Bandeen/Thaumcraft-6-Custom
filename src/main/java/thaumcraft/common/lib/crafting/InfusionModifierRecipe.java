package thaumcraft.common.lib.crafting;

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
import net.minecraft.world.item.crafting.RecipeSerializer;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.InfusionRecipeInput;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;

/**
 * A 4th real shape sharing {@link thaumcraft.api.crafting.ThaumcraftRecipeTypes#INFUSION_TYPE} - an infusion
 * recipe that UPGRADES the central item in place, carrying forward whatever real data components it already
 * had (rather than replacing it wholesale with a fresh {@code recipeOutput} copy the way a plain {@link
 * InfusionRecipe} does).
 *
 * <p>Real motivation: the Fortress Helm's own 4 real upgrade recipes (goggles mod + 3 masks - see {@code
 * data/thaumcraft/recipe/infusion_fortress_helm_goggles.json} and siblings) were shipped as plain {@link
 * InfusionRecipe}s, with a real, documented fidelity loss noted at the time: the original's own "a helm can
 * wear a mask AND the goggles mod at once" combinability wasn't preserved, since a plain recipe's result is
 * always a fresh stack built from its own JSON, discarding whatever the central item already carried. This
 * class is that documented follow-up, done for real: {@link #assemble} takes a copy of the real central
 * item and applies only the {@code recipeOutput}'s own real {@linkplain ItemStack#getComponentsPatch()
 * component patch} on top of it - e.g. an already-masked helm infused again with the goggles recipe keeps
 * its real mask, gaining the goggles component alongside it, matching the original's real design.
 *
 * <p>The 4 Fortress Helm upgrade recipes ({@code infusion_fortress_helm_goggles}/{@code _mask_devil}/
 * {@code _mask_ghost}/{@code _mask_fiend}) are switched to this real shape (just their {@code "type"} field -
 * every other field is unchanged) as this fix's own first real consumer.
 */
public class InfusionModifierRecipe extends InfusionRecipe {

	public InfusionModifierRecipe(String group, String research, AspectList aspects, int instability,
			Ingredient sourceInput, NonNullList<Ingredient> components, ItemStack recipeOutput) {
		super(group, research, aspects, instability, sourceInput, components, recipeOutput);
	}

	@Override
	public ItemStack assemble(InfusionRecipeInput input, HolderLookup.Provider registries) {
		ItemStack result = input.central().copy();
		result.applyComponents(getRecipeOutput().getComponentsPatch());
		return result;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.INFUSION_MODIFIER_SERIALIZER.get();
	}

	public static class Serializer implements RecipeSerializer<InfusionModifierRecipe> {
		public static final MapCodec<InfusionModifierRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(InfusionModifierRecipe::getGroup),
				Codec.STRING.optionalFieldOf("research", "").forGetter(InfusionModifierRecipe::getResearch),
				AspectList.CODEC.fieldOf("aspects").forGetter(InfusionModifierRecipe::getAspects),
				Codec.INT.optionalFieldOf("instability", 0).forGetter(InfusionModifierRecipe::getInstability),
				Ingredient.CODEC.optionalFieldOf("central", Ingredient.EMPTY).forGetter(InfusionModifierRecipe::getRecipeInput),
				Ingredient.CODEC_NONEMPTY.listOf().fieldOf("components").flatXmap(
						list -> list.isEmpty()
								? DataResult.error(() -> "No components for infusion modifier recipe")
								: DataResult.success(NonNullList.of(Ingredient.EMPTY, list.toArray(new Ingredient[0]))),
						DataResult::success
				).forGetter(InfusionModifierRecipe::getComponents),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(InfusionModifierRecipe::getRecipeOutput)
			).apply(inst, InfusionModifierRecipe::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, InfusionModifierRecipe> STREAM_CODEC = StreamCodec.of(
				Serializer::toNetwork, Serializer::fromNetwork
		);

		@Override
		public MapCodec<InfusionModifierRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, InfusionModifierRecipe> streamCodec() {
			return STREAM_CODEC;
		}

		private static InfusionModifierRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
			String group = ByteBufCodecs.STRING_UTF8.decode(buf);
			String research = ByteBufCodecs.STRING_UTF8.decode(buf);
			AspectList aspects = ByteBufCodecs.fromCodec(AspectList.CODEC).decode(buf);
			int instability = ByteBufCodecs.VAR_INT.decode(buf);
			Ingredient sourceInput = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
			int count = ByteBufCodecs.VAR_INT.decode(buf);
			NonNullList<Ingredient> components = NonNullList.withSize(count, Ingredient.EMPTY);
			components.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
			ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
			return new InfusionModifierRecipe(group, research, aspects, instability, sourceInput, components, result);
		}

		private static void toNetwork(RegistryFriendlyByteBuf buf, InfusionModifierRecipe recipe) {
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getGroup());
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getResearch());
			ByteBufCodecs.fromCodec(AspectList.CODEC).encode(buf, recipe.getAspects());
			ByteBufCodecs.VAR_INT.encode(buf, recipe.getInstability());
			Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.getRecipeInput());
			ByteBufCodecs.VAR_INT.encode(buf, recipe.getComponents().size());
			for (Ingredient ingredient : recipe.getComponents()) {
				Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
			}
			ItemStack.STREAM_CODEC.encode(buf, recipe.getRecipeOutput());
		}
	}
}

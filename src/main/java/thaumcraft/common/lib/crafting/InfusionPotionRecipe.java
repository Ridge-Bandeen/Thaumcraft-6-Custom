package thaumcraft.common.lib.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.InfusionRecipeInput;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;

/**
 * A 5th real shape sharing {@link ThaumcraftRecipeTypes#INFUSION_TYPE} - real support for "one of the
 * pedestal items must carry a specific real vanilla potion," the same real gap the original's own
 * VerdantHeartLife/VerdantHeartSustain recipes needed (a real Strong Healing/Strong Regeneration potion
 * item respectively - see {@code data/thaumcraft/recipe/infusion_verdant_heart_life.json} and its sibling).
 *
 * <p>Same real technique {@link InfusionCrystalRecipe} already established for "the central item's own real
 * data, not just its identity, decides the match" - rather than build a whole new {@code ICustomIngredient}/
 * {@code IngredientType} plumbing layer just for 2 recipes, the JSON-side ingredient for that one pedestal
 * slot stays a plain, broad {@code {"item": "minecraft:potion"}} (any potion), and {@link #matches} checks
 * the real {@link PotionContents} component of the matched components directly in Java on top of it.
 */
public class InfusionPotionRecipe extends InfusionRecipe {

	private final Holder<Potion> requiredPotion;

	public InfusionPotionRecipe(String group, String research, AspectList aspects, int instability,
			Ingredient sourceInput, NonNullList<Ingredient> components, ItemStack recipeOutput, Holder<Potion> requiredPotion) {
		super(group, research, aspects, instability, sourceInput, components, recipeOutput);
		this.requiredPotion = requiredPotion;
	}

	@Override
	public boolean matches(InfusionRecipeInput input, Level level) {
		if (!super.matches(input, level)) {
			return false;
		}
		for (ItemStack stack : input.components()) {
			PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
			if (contents != null && contents.is(requiredPotion)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.INFUSION_POTION_SERIALIZER.get();
	}

	public static class Serializer implements RecipeSerializer<InfusionPotionRecipe> {
		public static final MapCodec<InfusionPotionRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(InfusionPotionRecipe::getGroup),
				Codec.STRING.optionalFieldOf("research", "").forGetter(InfusionPotionRecipe::getResearch),
				AspectList.CODEC.fieldOf("aspects").forGetter(InfusionPotionRecipe::getAspects),
				Codec.INT.optionalFieldOf("instability", 0).forGetter(InfusionPotionRecipe::getInstability),
				Ingredient.CODEC.optionalFieldOf("central", Ingredient.EMPTY).forGetter(InfusionPotionRecipe::getRecipeInput),
				Ingredient.CODEC_NONEMPTY.listOf().fieldOf("components").flatXmap(
						list -> list.isEmpty()
								? DataResult.error(() -> "No components for infusion potion recipe")
								: DataResult.success(NonNullList.of(Ingredient.EMPTY, list.toArray(new Ingredient[0]))),
						DataResult::success
				).forGetter(InfusionPotionRecipe::getComponents),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(InfusionPotionRecipe::getRecipeOutput),
				RegistryFixedCodec.create(Registries.POTION).fieldOf("potion").forGetter(r -> r.requiredPotion)
			).apply(inst, InfusionPotionRecipe::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, InfusionPotionRecipe> STREAM_CODEC = StreamCodec.of(
				Serializer::toNetwork, Serializer::fromNetwork
		);

		@Override
		public MapCodec<InfusionPotionRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, InfusionPotionRecipe> streamCodec() {
			return STREAM_CODEC;
		}

		private static InfusionPotionRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
			String group = ByteBufCodecs.STRING_UTF8.decode(buf);
			String research = ByteBufCodecs.STRING_UTF8.decode(buf);
			AspectList aspects = ByteBufCodecs.fromCodec(AspectList.CODEC).decode(buf);
			int instability = ByteBufCodecs.VAR_INT.decode(buf);
			Ingredient sourceInput = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
			int count = ByteBufCodecs.VAR_INT.decode(buf);
			NonNullList<Ingredient> components = NonNullList.withSize(count, Ingredient.EMPTY);
			components.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
			ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
			Holder<Potion> potion = ByteBufCodecs.holderRegistry(Registries.POTION).decode(buf);
			return new InfusionPotionRecipe(group, research, aspects, instability, sourceInput, components, result, potion);
		}

		private static void toNetwork(RegistryFriendlyByteBuf buf, InfusionPotionRecipe recipe) {
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
			ByteBufCodecs.holderRegistry(Registries.POTION).encode(buf, recipe.requiredPotion);
		}
	}
}

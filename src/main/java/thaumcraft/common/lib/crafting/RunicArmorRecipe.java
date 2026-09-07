package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.RecipeMatcher;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.InfusionRecipeInput;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.api.items.ThaumcraftDataComponents;

/**
 * Real port of the old (1.12.2) {@code InfusionRunicAugmentRecipe} - real 6th shape sharing
 * {@link ThaumcraftRecipeTypes#INFUSION_TYPE} (see {@link InfusionEnchantmentRecipe}'s own doc comment for
 * the "one shared type, one serializer per concrete shape" precedent this and every other real dynamic
 * infusion recipe in this port already follow - {@code TileInfusionMatrix}'s existing lookup needs no
 * changes to find this). Modeled closely on {@link InfusionEnchantmentRecipe} (same real "one level per
 * craft, tracked on the item itself" shape) rather than {@link InfusionModifierRecipe} - the original's own
 * real escalating cost (more Amber each level) has no clean place in a fixed component list, so
 * {@link #matches} computes the real required component list itself instead of using the inherited
 * {@link #getComponents()} directly.
 *
 * Real armor piece, any slot, gated on the real {@code RUNICSHIELDING} research (checked separately by
 * {@code TileInfusionMatrix.craftingStart} via {@link #getResearch()}, same split every other real infusion
 * recipe in this port already relies on). Each craft raises the item's real
 * {@link ThaumcraftDataComponents#RUNIC_CHARGE} by exactly one - the real payoff, an absorption-shield tick
 * that reads this value off worn/held gear, lives in {@code RunicShieldEvents}.
 *
 * Real, documented cuts from the original: {@code IBauble} baubles-slot eligibility is dropped (baubles
 * aren't ported - see {@code WornGearHelper}'s own doc comment for this whole port's standing decision on
 * that), leaving real armor pieces as the only eligible central item. No hard level cap either way (matches
 * the original, which never capped this - the escalating Amber cost is the only real brake on how far a
 * player pushes it).
 */
public class RunicArmorRecipe extends InfusionRecipe {

	public RunicArmorRecipe(String group, String research, AspectList aspects, int instability, NonNullList<Ingredient> components) {
		super(group, research, aspects, instability, Ingredient.EMPTY, components, ItemStack.EMPTY);
	}

	private static int currentLevel(ItemStack stack) {
		Integer level = stack.get(ThaumcraftDataComponents.RUNIC_CHARGE.get());
		return level == null ? 0 : level;
	}

	/** The real base components (from JSON - a Salis Mundus and 1 Amber, matching the original's own base
	 * cost) plus {@code currentLevel} extra copies of the same Amber ingredient (index 1) - the original's
	 * own real escalating cost, 1 more Amber required per existing runic level. */
	private List<Ingredient> requiredComponents(int currentLevel) {
		List<Ingredient> required = new ArrayList<>(getComponents());
		if (getComponents().size() > 1) {
			Ingredient amber = getComponents().get(1);
			for (int i = 0; i < currentLevel; i++) {
				required.add(amber);
			}
		}
		return required;
	}

	@Override
	public boolean matches(InfusionRecipeInput input, Level level) {
		ItemStack central = input.central();
		if (!(central.getItem() instanceof ArmorItem)) return false;
		List<Ingredient> required = requiredComponents(currentLevel(central));
		return RecipeMatcher.findMatches(input.components(), NonNullList.of(Ingredient.EMPTY, required.toArray(new Ingredient[0]))) != null;
	}

	@Override
	public ItemStack getRecipeOutput(Player player, ItemStack input, List<ItemStack> comps) {
		if (input.isEmpty()) return ItemStack.EMPTY;
		ItemStack out = input.copy();
		out.set(ThaumcraftDataComponents.RUNIC_CHARGE.get(), currentLevel(input) + 1);
		return out;
	}

	/** Matches the original's own real {@code 20 + 20 * 2^level} vis-cost curve exactly, split across
	 * Protect/Crystal/Energy the same way the original's own {@code getAspects} did. */
	@Override
	public AspectList getAspects(Player player, ItemStack input, List<ItemStack> comps) {
		AspectList out = new AspectList();
		if (input.isEmpty()) return out;
		int vis = 20 + (int) (20.0 * Math.pow(2.0, currentLevel(input)));
		out.add(Aspect.PROTECT, vis);
		out.add(Aspect.CRYSTAL, vis / 2);
		out.add(Aspect.ENERGY, vis / 2);
		return out;
	}

	@Override
	public int getInstability(Player player, ItemStack input, List<ItemStack> comps) {
		if (input.isEmpty()) return getInstability();
		return 5 + currentLevel(input) / 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.RUNIC_ARMOR_SERIALIZER.get();
	}

	public static class Serializer implements RecipeSerializer<RunicArmorRecipe> {
		public static final MapCodec<RunicArmorRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(RunicArmorRecipe::getGroup),
				Codec.STRING.optionalFieldOf("research", "").forGetter(RunicArmorRecipe::getResearch),
				AspectList.CODEC.fieldOf("aspects").forGetter(RunicArmorRecipe::getAspects),
				Codec.INT.optionalFieldOf("instability", 5).forGetter(RunicArmorRecipe::getInstability),
				Ingredient.CODEC_NONEMPTY.listOf().fieldOf("components").flatXmap(
						list -> list.isEmpty()
								? DataResult.error(() -> "No components for runic armor recipe")
								: DataResult.success(NonNullList.of(Ingredient.EMPTY, list.toArray(new Ingredient[0]))),
						DataResult::success
				).forGetter(RunicArmorRecipe::getComponents)
			).apply(inst, RunicArmorRecipe::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, RunicArmorRecipe> STREAM_CODEC = StreamCodec.of(
				Serializer::toNetwork, Serializer::fromNetwork
		);

		@Override
		public MapCodec<RunicArmorRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, RunicArmorRecipe> streamCodec() {
			return STREAM_CODEC;
		}

		private static RunicArmorRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
			String group = ByteBufCodecs.STRING_UTF8.decode(buf);
			String research = ByteBufCodecs.STRING_UTF8.decode(buf);
			AspectList aspects = ByteBufCodecs.fromCodec(AspectList.CODEC).decode(buf);
			int instability = ByteBufCodecs.VAR_INT.decode(buf);
			int count = ByteBufCodecs.VAR_INT.decode(buf);
			NonNullList<Ingredient> components = NonNullList.withSize(count, Ingredient.EMPTY);
			components.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
			return new RunicArmorRecipe(group, research, aspects, instability, components);
		}

		private static void toNetwork(RegistryFriendlyByteBuf buf, RunicArmorRecipe recipe) {
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getGroup());
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getResearch());
			ByteBufCodecs.fromCodec(AspectList.CODEC).encode(buf, recipe.getAspects());
			ByteBufCodecs.VAR_INT.encode(buf, recipe.getInstability());
			ByteBufCodecs.VAR_INT.encode(buf, recipe.getComponents().size());
			for (Ingredient ingredient : recipe.getComponents()) {
				Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
			}
		}
	}
}

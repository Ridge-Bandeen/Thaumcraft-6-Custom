package thaumcraft.common.lib.crafting;

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
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;

/**
 * Replaces the old (1.12.2) {@code InfusionEnchantmentRecipe} - a real subclass of the modern
 * {@link InfusionRecipe} (matching the original's own class hierarchy, and confirmed workable since none of
 * {@link InfusionRecipe}'s overridable methods are {@code final} - see that class's own doc comment for the
 * per-player {@code getRecipeOutput}/{@code getAspects} extension points this relies on). One recipe covers
 * every level of one {@link EnumInfusionEnchantment} - {@link #matches} accepts any central item currently
 * below the enchantment's max level, and each successful craft raises the level by exactly one, same as the
 * original.
 *
 * Two behavioural differences from the original, both following precedent already set by {@link InfusionRecipe}
 * itself:
 * <ul>
 * <li>Research-gating is not part of {@link #matches} - {@code TileInfusionMatrix.craftingStart} already
 * checks {@link #getResearch()} separately after finding a match, same as every other {@code InfusionRecipe}.
 * The original checked {@code ThaumcraftCapabilities.knowsResearch} inline in {@code matches()} instead,
 * before that split existed.</li>
 * <li>Tool-class matching (weapon/tool/armor-slot/chargable) is delegated to
 * {@link EnumInfusionEnchantment#isValidTool} - see that method's own doc comment for what changed (modern
 * {@code ItemTags} instead of {@code getToolClasses()}, and why bauble-slot classes never match).</li>
 * </ul>
 *
 * As the original, each enchanting has a chance - one in ten per infusion enchantment already on the item - to make the
 * item itself warping: +1 {@link thaumcraft.api.items.ThaumcraftDataComponents#ITEM_WARP} (the original's "TC.WARP").
 */
public class InfusionEnchantmentRecipe extends InfusionRecipe {

	private final EnumInfusionEnchantment enchantment;
	/** Set only on the book's display copies (the original's {@code IE...FAKE} catalog recipes): the tool the page shows
	 * the enchantment going onto. A recipe with one never matches - the real recipe does the crafting. */
	private final ItemStack display;

	public InfusionEnchantmentRecipe(String group, String research, AspectList aspects, int instability,
			NonNullList<Ingredient> components, EnumInfusionEnchantment enchantment) {
		this(group, research, aspects, instability, components, enchantment, ItemStack.EMPTY);
	}

	public InfusionEnchantmentRecipe(String group, String research, AspectList aspects, int instability,
			NonNullList<Ingredient> components, EnumInfusionEnchantment enchantment, ItemStack display) {
		super(group, research, aspects, instability, display.isEmpty() ? Ingredient.EMPTY : Ingredient.of(display.getItem()),
				components, ItemStack.EMPTY);
		this.enchantment = enchantment;
		this.display = display;
	}

	@Override
	public boolean matches(InfusionRecipeInput input, Level level) {
		if (!display.isEmpty()) return false;
		ItemStack central = input.central();
		if (central.isEmpty()) {
			return false;
		}
		if (EnumInfusionEnchantment.getInfusionEnchantmentLevel(central, enchantment) >= enchantment.maxLevel) {
			return false;
		}
		if (!enchantment.isValidTool(central)) {
			return false;
		}
		return RecipeMatcher.findMatches(input.components(), getComponents()) != null;
	}

	@Override
	public ItemStack getRecipeOutput(Player player, ItemStack input, List<ItemStack> comps) {
		if (input.isEmpty()) {
			return ItemStack.EMPTY;
		}
		int nextLevel = EnumInfusionEnchantment.getInfusionEnchantmentLevel(input, enchantment) + 1;
		if (nextLevel > enchantment.maxLevel) {
			return ItemStack.EMPTY;
		}
		ItemStack out = input.copy();
		int existing = EnumInfusionEnchantment.getInfusionEnchantments(input).size();
		if (net.minecraft.util.RandomSource.create().nextInt(10) < existing) {
			out.set(thaumcraft.api.items.ThaumcraftDataComponents.ITEM_WARP.get(),
					input.getOrDefault(thaumcraft.api.items.ThaumcraftDataComponents.ITEM_WARP.get(), 0) + 1);
		}
		EnumInfusionEnchantment.addInfusionEnchantment(out, enchantment, nextLevel);
		return out;
	}

	@Override
	public AspectList getAspects(Player player, ItemStack input, List<ItemStack> comps) {
		AspectList out = new AspectList();
		if (input.isEmpty()) {
			return out;
		}
		int nextLevel = EnumInfusionEnchantment.getInfusionEnchantmentLevel(input, enchantment) + 1;
		if (nextLevel > enchantment.maxLevel) {
			return out;
		}
		List<EnumInfusionEnchantment> existing = EnumInfusionEnchantment.getInfusionEnchantments(input);
		int otherEnchantments = existing.size() - (existing.contains(enchantment) ? 1 : 0);
		float modifier = nextLevel + otherEnchantments * 0.33f;
		for (Aspect a : getAspects().getAspects()) {
			out.add(a, (int) (getAspects().getAmount(a) * modifier));
		}
		return out;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.INFUSION_ENCHANTMENT_SERIALIZER.get();
	}

	public static class Serializer implements RecipeSerializer<InfusionEnchantmentRecipe> {
		public static final MapCodec<InfusionEnchantmentRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(InfusionEnchantmentRecipe::getGroup),
				Codec.STRING.optionalFieldOf("research", "").forGetter(InfusionEnchantmentRecipe::getResearch),
				AspectList.CODEC.fieldOf("aspects").forGetter(InfusionEnchantmentRecipe::getAspects),
				Codec.INT.optionalFieldOf("instability", 4).forGetter(InfusionEnchantmentRecipe::getInstability),
				Ingredient.CODEC_NONEMPTY.listOf().fieldOf("components").flatXmap(
						list -> list.isEmpty()
								? DataResult.error(() -> "No components for infusion enchantment recipe")
								: DataResult.success(NonNullList.of(Ingredient.EMPTY, list.toArray(new Ingredient[0]))),
						DataResult::success
				).forGetter(InfusionEnchantmentRecipe::getComponents),
				Codec.STRING.fieldOf("enchantment").forGetter(r -> r.enchantment.name()),
				ItemStack.OPTIONAL_CODEC.optionalFieldOf("display", ItemStack.EMPTY).forGetter(r -> r.display)
			).apply(inst, (group, research, aspects, instability, components, enchantmentName, display) ->
					new InfusionEnchantmentRecipe(group, research, aspects, instability, components,
							EnumInfusionEnchantment.valueOf(enchantmentName), display)));

		public static final StreamCodec<RegistryFriendlyByteBuf, InfusionEnchantmentRecipe> STREAM_CODEC = StreamCodec.of(
				Serializer::toNetwork, Serializer::fromNetwork
		);

		@Override
		public MapCodec<InfusionEnchantmentRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, InfusionEnchantmentRecipe> streamCodec() {
			return STREAM_CODEC;
		}

		private static InfusionEnchantmentRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
			String group = ByteBufCodecs.STRING_UTF8.decode(buf);
			String research = ByteBufCodecs.STRING_UTF8.decode(buf);
			AspectList aspects = ByteBufCodecs.fromCodec(AspectList.CODEC).decode(buf);
			int instability = ByteBufCodecs.VAR_INT.decode(buf);
			int count = ByteBufCodecs.VAR_INT.decode(buf);
			NonNullList<Ingredient> components = NonNullList.withSize(count, Ingredient.EMPTY);
			components.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
			String enchantmentName = ByteBufCodecs.STRING_UTF8.decode(buf);
			ItemStack display = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
			return new InfusionEnchantmentRecipe(group, research, aspects, instability, components,
					EnumInfusionEnchantment.valueOf(enchantmentName), display);
		}

		private static void toNetwork(RegistryFriendlyByteBuf buf, InfusionEnchantmentRecipe recipe) {
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getGroup());
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.getResearch());
			ByteBufCodecs.fromCodec(AspectList.CODEC).encode(buf, recipe.getAspects());
			ByteBufCodecs.VAR_INT.encode(buf, recipe.getInstability());
			ByteBufCodecs.VAR_INT.encode(buf, recipe.getComponents().size());
			for (Ingredient ingredient : recipe.getComponents()) {
				Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
			}
			ByteBufCodecs.STRING_UTF8.encode(buf, recipe.enchantment.name());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, recipe.display);
		}
	}
}

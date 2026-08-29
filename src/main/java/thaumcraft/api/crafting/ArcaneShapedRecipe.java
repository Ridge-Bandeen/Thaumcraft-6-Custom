package thaumcraft.api.crafting;

import com.mojang.serialization.Codec;
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
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;

/**
 * Replaces the old (1.12.2) {@code ShapedArcaneRecipe}, which subclassed Forge's {@code ShapedOreRecipe}
 * (itself a vanilla {@code IRecipe}) and layered an "also must be in an arcane workbench, and the 6 crystal
 * ring slots must hold enough of the required aspects" check on top of vanilla's shape matching. Vanilla's
 * {@code Ingredient}/recipe classes are no longer subclassable, so this now implements {@code Recipe} directly,
 * reusing vanilla's own {@link ShapedRecipePattern} for the 3x3 shape check and doing the crystal check itself.
 * See {@link ArcaneCraftingInput}.
 */
public class ArcaneShapedRecipe implements Recipe<ArcaneCraftingInput>, IThaumcraftRecipe {

	private final String group;
	private final String research;
	private final int vis;
	private final AspectList crystals;
	private final ShapedRecipePattern pattern;
	private final ItemStack result;

	public ArcaneShapedRecipe(String group, String research, int vis, AspectList crystals, ShapedRecipePattern pattern, ItemStack result) {
		this.group = group;
		this.research = research;
		this.vis = vis;
		this.crystals = crystals;
		this.pattern = pattern;
		this.result = result;
	}

	@Override
	public boolean matches(ArcaneCraftingInput input, Level level) {
		return pattern.matches(input.grid()) && matchesCrystals(input.crystals());
	}

	/**
	 * Each required aspect needs its own single crystal-ring slot holding a stack, sized at least the
	 * required amount, of crystals of exactly that one aspect - mirrors the original per-aspect,
	 * per-slot (not summed across slots) check.
	 */
	private boolean matchesCrystals(java.util.List<ItemStack> ring) {
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
		return width >= pattern.width() && height >= pattern.height();
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		return pattern.ingredients();
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
		return ThaumcraftRecipeTypes.ARCANE_SHAPED_SERIALIZER.get();
	}

	@Override
	public RecipeType<?> getType() {
		return ThaumcraftRecipeTypes.ARCANE_SHAPED_TYPE.get();
	}

	public static class Serializer implements RecipeSerializer<ArcaneShapedRecipe> {
		public static final MapCodec<ArcaneShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
				Codec.STRING.optionalFieldOf("research", "").forGetter(r -> r.research),
				Codec.INT.optionalFieldOf("vis", 0).forGetter(r -> r.vis),
				AspectList.CODEC.optionalFieldOf("crystals", new AspectList()).forGetter(r -> r.crystals),
				ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result)
			).apply(inst, ArcaneShapedRecipe::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneShapedRecipe> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8.cast(), r -> r.group,
				ByteBufCodecs.STRING_UTF8.cast(), r -> r.research,
				ByteBufCodecs.VAR_INT.cast(), r -> r.vis,
				ByteBufCodecs.fromCodec(AspectList.CODEC).cast(), r -> r.crystals,
				ShapedRecipePattern.STREAM_CODEC, r -> r.pattern,
				ItemStack.STREAM_CODEC, r -> r.result,
				ArcaneShapedRecipe::new
		);

		@Override
		public MapCodec<ArcaneShapedRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, ArcaneShapedRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}

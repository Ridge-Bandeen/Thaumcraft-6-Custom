package thaumcraft.common.lib.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;

/**
 * A furnace recipe whose output is whatever item a tag holds - the original's {@code ModConfig} smelting of the tin,
 * silver and lead clusters into two of whichever mod's ingot it found in the ore dictionary. Paired with a
 * "tag not empty" load condition, so with no such mod it simply isn't there.
 */
public class TagSmeltingRecipe extends SmeltingRecipe {

	private final TagKey<Item> resultTag;
	private final int count;

	public TagSmeltingRecipe(String group, CookingBookCategory category, Ingredient ingredient, TagKey<Item> resultTag, int count, float experience, int cookingTime) {
		super(group, category, ingredient, ItemStack.EMPTY, experience, cookingTime);
		this.resultTag = resultTag;
		this.count = count;
	}

	private ItemStack resolve() {
		for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(resultTag)) return new ItemStack(holder, count);
		return ItemStack.EMPTY;
	}

	@Override
	public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
		return resolve();
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return resolve();
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ThaumcraftRecipeTypes.TAG_SMELTING_SERIALIZER.get();
	}

	public static class Serializer implements RecipeSerializer<TagSmeltingRecipe> {
		private static final MapCodec<TagSmeltingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.getGroup()),
				CookingBookCategory.CODEC.fieldOf("category").orElse(CookingBookCategory.MISC).forGetter(r -> r.category()),
				Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(r -> r.getIngredients().get(0)),
				TagKey.codec(Registries.ITEM).fieldOf("result_tag").forGetter(r -> r.resultTag),
				Codec.INT.optionalFieldOf("count", 1).forGetter(r -> r.count),
				Codec.FLOAT.fieldOf("experience").orElse(0.0f).forGetter(r -> r.getExperience()),
				Codec.INT.fieldOf("cookingtime").orElse(200).forGetter(r -> r.getCookingTime())
		).apply(inst, TagSmeltingRecipe::new));

		private static final StreamCodec<RegistryFriendlyByteBuf, TagSmeltingRecipe> STREAM_CODEC = StreamCodec.of(
				(buf, r) -> {
					buf.writeUtf(r.getGroup());
					buf.writeEnum(r.category());
					Ingredient.CONTENTS_STREAM_CODEC.encode(buf, r.getIngredients().get(0));
					buf.writeResourceLocation(r.resultTag.location());
					ByteBufCodecs.VAR_INT.encode(buf, r.count);
					buf.writeFloat(r.getExperience());
					ByteBufCodecs.VAR_INT.encode(buf, r.getCookingTime());
				},
				buf -> new TagSmeltingRecipe(buf.readUtf(), buf.readEnum(CookingBookCategory.class), Ingredient.CONTENTS_STREAM_CODEC.decode(buf),
						TagKey.create(Registries.ITEM, buf.readResourceLocation()), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(),
						ByteBufCodecs.VAR_INT.decode(buf)));

		@Override
		public MapCodec<TagSmeltingRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, TagSmeltingRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}

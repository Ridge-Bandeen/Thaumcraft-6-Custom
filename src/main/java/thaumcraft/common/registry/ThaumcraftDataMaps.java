package thaumcraft.common.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import thaumcraft.Thaumcraft;

/** Thaumcraft's data maps (data/&lt;ns&gt;/data_maps/&lt;registry&gt;/&lt;name&gt;.json). */
public final class ThaumcraftDataMaps {

	/**
	 * Refining (the infusion enchantment, also the Arcane Bore's): a drop listed here may come out as {@code result}
	 * instead, its count scaled by the drop's count - the original's {@code Utils.addSpecialMiningResult} table.
	 * {@code chance} is the original's per-entry multiplier on Refining's {@code (1 + level) * 0.125} roll.
	 */
	public record Refining(ItemStack result, float chance) {
		public static final Codec<Refining> CODEC = RecordCodecBuilder.create(i -> i.group(
				ItemStack.CODEC.fieldOf("result").forGetter(Refining::result),
				Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(Refining::chance)).apply(i, Refining::new));
	}

	public static final DataMapType<Item, Refining> REFINING = DataMapType.builder(
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "refining"), Registries.ITEM, Refining.CODEC).build();

	/** One Infernal Furnace smelting bonus: a chance (the original's default 0.33) of an extra {@code result}. */
	public record SmeltingBonus(ItemStack result, float chance) {
		public static final Codec<SmeltingBonus> CODEC = RecordCodecBuilder.create(i -> i.group(
				ItemStack.CODEC.fieldOf("result").forGetter(SmeltingBonus::result),
				Codec.FLOAT.optionalFieldOf("chance", 0.33f).forGetter(SmeltingBonus::chance)).apply(i, SmeltingBonus::new));
	}

	/**
	 * The Infernal Furnace's smelting bonuses, keyed by the item being smelted (an item or {@code #tag} key) - the
	 * original's {@code ThaumcraftApi.addSmeltingBonus} table. Each bonus is rolled once per bellows plus one.
	 */
	public static final DataMapType<Item, java.util.List<SmeltingBonus>> SMELTING_BONUS = DataMapType.builder(
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "smelting_bonus"), Registries.ITEM, SmeltingBonus.CODEC.listOf()).build();

	/** A champion-capable monster's weight: the original's "championWhiteList" ({@code [Entity]:[level]}). */
	public record ChampionWeight(int weight) {
		public static final Codec<ChampionWeight> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("weight", 0).forGetter(ChampionWeight::weight)).apply(i, ChampionWeight::new));
	}

	/**
	 * The monsters that may spawn as champions (data/&lt;ns&gt;/data_maps/entity_type/champion_weight.json) - the original's
	 * champion whitelist, which matched a listed class and its subclasses, so zombie kin are listed one by one here.
	 * A weight above 1 makes champions likelier: weight - 1 more in a hundred.
	 */
	public static final DataMapType<net.minecraft.world.entity.EntityType<?>, ChampionWeight> CHAMPION_WEIGHT = DataMapType.builder(
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "champion_weight"), Registries.ENTITY_TYPE, ChampionWeight.CODEC).build();

	private ThaumcraftDataMaps() {
	}

	public static void register(RegisterDataMapTypesEvent event) {
		event.register(REFINING);
		event.register(SMELTING_BONUS);
		event.register(CHAMPION_WEIGHT);
	}
}

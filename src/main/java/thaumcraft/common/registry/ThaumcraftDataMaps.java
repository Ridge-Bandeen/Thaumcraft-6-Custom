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

/** Thaumcraft's item data maps (data/&lt;ns&gt;/data_maps/item/&lt;name&gt;.json). */
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

	private ThaumcraftDataMaps() {
	}

	public static void register(RegisterDataMapTypesEvent event) {
		event.register(REFINING);
		event.register(SMELTING_BONUS);
	}
}

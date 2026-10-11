package thaumcraft.common.lib.utils;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.internal.WeightedRandomLoot;
import thaumcraft.api.items.ItemsTC;

/**
 * The original's loot generation ({@code Utils.generateLoot}/{@code genGear} and the {@code ModConfig} loot list), behind
 * loot bags and the urns and crates found in the world. Each roll is, now and then (2.5% per tier), a piece of worn gear -
 * leather up to void metal, a little damaged, sometimes enchanted - and otherwise a weighted pick from the bag tier's pool
 * (mostly gold nuggets, then gold, ender pearls, diamonds, potions, pearls...). Books come out enchanted.
 */
public final class LootGenerator {

	private LootGenerator() {
	}

	/** The original's {@code generateLoot}: one item for a bag or urn of this rarity (0 common, 1 uncommon, 2 rare). */
	public static ItemStack generateLoot(int rarity, RandomSource rand, RegistryAccess access) {
		ItemStack is = ItemStack.EMPTY;
		if (rarity > 0 && rand.nextFloat() < 0.025f * rarity) {
			is = genGear(rarity, rand, access);
			if (is.isEmpty()) is = generateLoot(rarity, rand, access);
		} else {
			List<WeightedRandomLoot> pool = switch (rarity) {
				case 1 -> WeightedRandomLoot.lootBagUncommon;
				case 2 -> WeightedRandomLoot.lootBagRare;
				default -> WeightedRandomLoot.lootBagCommon;
			};
			is = WeightedRandom.getRandomItem(rand, pool).map(l -> l.item).orElse(ItemStack.EMPTY);
		}
		if (is.is(Items.BOOK)) {
			is = enchant(rand, is.copy(), (int) (5.0f + rarity * 0.75f * rand.nextInt(18)), access);
		}
		return is.copy();
	}

	private static ItemStack genGear(int rarity, RandomSource rand, RegistryAccess access) {
		int quality = rand.nextInt(2);
		if (rand.nextFloat() < 0.2f) ++quality;
		if (rand.nextFloat() < 0.15f) ++quality;
		if (rand.nextFloat() < 0.1f) ++quality;
		if (rand.nextFloat() < 0.095f) ++quality;
		if (rand.nextFloat() < 0.095f) ++quality;
		Item item = gearItemForSlot(rand.nextInt(5), quality);
		if (item == null) return ItemStack.EMPTY;
		ItemStack is = new ItemStack(item);
		if (is.isDamageableItem()) is.setDamageValue(rand.nextInt(1 + is.getMaxDamage() / 6));
		if (rand.nextInt(4) < rarity) {
			is = enchant(rand, is, (int) (5.0f + rarity * 0.75f * rand.nextInt(18)), access);
		}
		return is.copy();
	}

	/** {@code EnchantmentHelper.addRandomEnchantment(rand, stack, level, false)}: enchanting-table enchantments, no treasure. */
	private static ItemStack enchant(RandomSource rand, ItemStack stack, int level, RegistryAccess access) {
		return EnchantmentHelper.enchantItem(rand, stack, level, access,
				access.registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE));
	}

	/** Slot 0 a weapon, 1-4 boots to helmet; quality 0-6 from leather (or an iron axe) up to void metal. */
	private static Item gearItemForSlot(int slot, int quality) {
		Item[] tier = switch (slot) {
			case 4 -> new Item[] { Items.LEATHER_HELMET, Items.GOLDEN_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET,
					ItemsTC.thaumiumHelm.get(), Items.DIAMOND_HELMET, ItemsTC.voidHelm.get() };
			case 3 -> new Item[] { Items.LEATHER_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE,
					ItemsTC.thaumiumChest.get(), Items.DIAMOND_CHESTPLATE, ItemsTC.voidChest.get() };
			case 2 -> new Item[] { Items.LEATHER_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS,
					ItemsTC.thaumiumLegs.get(), Items.DIAMOND_LEGGINGS, ItemsTC.voidLegs.get() };
			case 1 -> new Item[] { Items.LEATHER_BOOTS, Items.GOLDEN_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS,
					ItemsTC.thaumiumBoots.get(), Items.DIAMOND_BOOTS, ItemsTC.voidBoots.get() };
			case 0 -> new Item[] { Items.IRON_AXE, Items.IRON_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_SWORD,
					ItemsTC.thaumiumSword.get(), Items.DIAMOND_SWORD, ItemsTC.voidSword.get() };
			default -> null;
		};
		return tier != null && quality >= 0 && quality < tier.length ? tier[quality] : null;
	}

	/** The original's {@code ModConfig} loot list - registered at setup, before {@code ThaumcraftSetupEvent} lets addons add more. */
	public static void registerDefaults() {
		WeightedRandomLoot.lootBagCommon.clear();
		WeightedRandomLoot.lootBagUncommon.clear();
		WeightedRandomLoot.lootBagRare.clear();
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.GOLD_NUGGET, 1), 2500, 0);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.GOLD_NUGGET, 2), 2250, 1);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.GOLD_NUGGET, 3), 2000, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.salisMundus.get()), 3, 0);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.salisMundus.get()), 6, 1);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.salisMundus.get()), 9, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.CHORUS_FRUIT), 5, 0, 1, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.COMPASS), 5, 0, 1, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.COOKIE), 5, 0, 1, 2);
		ThaumcraftApi.addLootBagItem(pearl(7), 1, 0);
		ThaumcraftApi.addLootBagItem(pearl(7), 3, 1);
		ThaumcraftApi.addLootBagItem(pearl(6), 1, 1);
		ThaumcraftApi.addLootBagItem(pearl(5), 9, 2);
		ThaumcraftApi.addLootBagItem(pearl(3), 3, 2);
		ThaumcraftApi.addLootBagItem(pearl(0), 1, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.NETHER_STAR), 1, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.DIAMOND), 10, 0);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.DIAMOND), 50, 1, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.EMERALD), 15, 0);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.EMERALD), 75, 1, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.GOLD_INGOT), 100, 0, 1, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.ENDER_PEARL), 100, 0, 1, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.amuletVisFound.get()), 6, 1, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.amuletMundane.get()), 10, 0);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.ringMundane.get()), 10, 0);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.girdleMundane.get()), 10, 0);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.ringApprentice.get()), 5, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.amuletFancy.get()), 5, 1);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.ringFancy.get()), 5, 1);
		ThaumcraftApi.addLootBagItem(new ItemStack(ItemsTC.girdleFancy.get()), 5, 1);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.EXPERIENCE_BOTTLE), 5, 0);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.EXPERIENCE_BOTTLE), 10, 1);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.EXPERIENCE_BOTTLE), 20, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 1, 0);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 2, 1);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 3, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.GOLDEN_APPLE), 3, 0);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.GOLDEN_APPLE), 6, 1);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.GOLDEN_APPLE), 9, 2);
		ThaumcraftApi.addLootBagItem(new ItemStack(Items.BOOK), 10, 0, 1, 2);
		for (Holder<Potion> potion : BuiltInRegistries.POTION.holders().toList()) {
			ThaumcraftApi.addLootBagItem(PotionContents.createItemStack(Items.POTION, potion), 2, 0, 1, 2);
			ThaumcraftApi.addLootBagItem(PotionContents.createItemStack(Items.SPLASH_POTION, potion), 2, 0, 1, 2);
			ThaumcraftApi.addLootBagItem(PotionContents.createItemStack(Items.LINGERING_POTION, potion), 2, 1, 2);
		}
	}

	/** A Primordial Pearl with this much wear (the original's damage value). */
	private static ItemStack pearl(int damage) {
		ItemStack stack = new ItemStack(ItemsTC.primordialPearl.get());
		stack.setDamageValue(damage);
		return stack;
	}
}

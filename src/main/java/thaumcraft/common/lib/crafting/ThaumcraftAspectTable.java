package thaumcraft.common.lib.crafting;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;

/**
 * The explicit item-to-aspect table: the original's whole {@code ConfigAspects#registerItemAspects} (about 330 entries),
 * carried over with its own values. Generated 2026-09-25 from the original source and then completed by hand:
 * <ul>
 * <li>1.12 ore-dictionary names became item tags ({@code c:ingots/tin}, ...); entries for metals this project has no items
 * for (tin, silver, lead, brass, bronze, steel, uranium, gems, rubber) still register against those tags, so any
 * mod that supplies them gets the original values.</li>
 * <li>Metadata variants became the separate 1.21 items (stone bricks, skulls, dyes, golden apples, ...); wildcard
 * families (wool, doors, boats, terracotta, ...) use the vanilla tags; the 16-colour groups are expanded.</li>
 * <li>Entries for blocks 1.21 no longer has as items (fire, portals, tripwire) are dropped.</li>
 * <li>{@code of(x)} is {@code new AspectList(new ItemStack(x))}: another item's whole answer, evaluated on demand.</li>
 * </ul>
 */
final class ThaumcraftAspectTable {

	private ThaumcraftAspectTable() {
	}

	private static TagKey<Item> t(String id) {
		return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
	}

	private static AspectList of(Item item) {
		return ThaumcraftObjectTags.of(item);
	}

	private static void item(Item item, AspectList aspects) {
		ThaumcraftObjectTags.item(item, aspects);
	}

	private static void item(Item item, Supplier<AspectList> aspects) {
		ThaumcraftObjectTags.item(item, aspects);
	}

	private static void tag(TagKey<Item> tag, AspectList aspects) {
		ThaumcraftObjectTags.tag(tag, aspects);
	}

	private static void tag(TagKey<Item> tag, Supplier<AspectList> aspects) {
		ThaumcraftObjectTags.tag(tag, aspects);
	}

	private static void complexItem(Item item, AspectList aspects) {
		ThaumcraftObjectTags.complexItem(item, aspects);
	}

	private static void complexItem(Item item, Supplier<AspectList> aspects) {
		ThaumcraftObjectTags.complexItem(item, aspects);
	}

	private static void complexTag(TagKey<Item> tag, AspectList aspects) {
		ThaumcraftObjectTags.complexTag(tag, aspects);
	}

	static void register() {
		vanilla();
		colours();
		modern();
		thaumcraft();
	}

	private static void vanilla() {
		tag(t("c:ores/lapis"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.SENSES, 15));
		tag(t("c:ores/diamond"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.DESIRE, 15).add(Aspect.CRYSTAL, 15));
		tag(t("c:gems/diamond"), new AspectList().add(Aspect.CRYSTAL, 15).add(Aspect.DESIRE, 15));
		tag(t("c:ores/redstone"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENERGY, 15));
		tag(t("c:ores/emerald"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10).add(Aspect.CRYSTAL, 15));
		tag(t("c:gems/emerald"), new AspectList().add(Aspect.CRYSTAL, 15).add(Aspect.DESIRE, 10));
		tag(t("c:ores/quartz"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.CRYSTAL, 10));
		tag(t("c:gems/quartz"), new AspectList().add(Aspect.CRYSTAL, 5));
		tag(t("c:ores/iron"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.METAL, 15));
		tag(t("c:dusts/iron"), new AspectList().add(Aspect.METAL, 15).add(Aspect.ENTROPY, 1));
		tag(t("c:ingots/iron"), new AspectList().add(Aspect.METAL, 15));
		tag(t("c:ores/gold"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.METAL, 10).add(Aspect.DESIRE, 10));
		tag(t("c:dusts/gold"), new AspectList().add(Aspect.METAL, 10).add(Aspect.DESIRE, 10).add(Aspect.ENTROPY, 1));
		tag(t("c:ingots/gold"), new AspectList().add(Aspect.METAL, 10).add(Aspect.DESIRE, 10));
		tag(t("c:ores/coal"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENERGY, 15).add(Aspect.FIRE, 15));
		item(Items.COAL, new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10));
		item(Items.CHARCOAL, new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10));
		tag(t("c:dusts/redstone"), new AspectList().add(Aspect.ENERGY, 10));
		tag(t("c:dusts/glowstone"), new AspectList().add(Aspect.SENSES, 5).add(Aspect.LIGHT, 10));
		complexItem(Blocks.GLOWSTONE.asItem(), new AspectList());
		tag(t("c:ingots/copper"), new AspectList().add(Aspect.METAL, 10).add(Aspect.EXCHANGE, 5));
		tag(t("c:dusts/copper"), new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.EXCHANGE, 5));
		tag(t("c:ores/copper"), new AspectList().add(Aspect.METAL, 10).add(Aspect.EARTH, 5).add(Aspect.EXCHANGE, 5));
		tag(t("c:raw_materials/copper"), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.EXCHANGE, 10));
		tag(t("c:ingots/tin"), new AspectList().add(Aspect.METAL, 10).add(Aspect.CRYSTAL, 5));
		tag(t("c:dusts/tin"), new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.CRYSTAL, 5));
		tag(t("c:ores/tin"), new AspectList().add(Aspect.METAL, 10).add(Aspect.EARTH, 5).add(Aspect.CRYSTAL, 5));
		tag(t("c:raw_materials/tin"), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.CRYSTAL, 10));
		tag(t("c:ingots/silver"), new AspectList().add(Aspect.METAL, 10).add(Aspect.DESIRE, 5));
		tag(t("c:dusts/silver"), new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.DESIRE, 5));
		tag(t("c:ores/silver"), new AspectList().add(Aspect.METAL, 10).add(Aspect.EARTH, 5).add(Aspect.DESIRE, 5));
		tag(t("c:raw_materials/silver"), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10));
		tag(t("c:ingots/lead"), new AspectList().add(Aspect.METAL, 10).add(Aspect.ORDER, 5));
		tag(t("c:dusts/lead"), new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.ORDER, 5));
		tag(t("c:ores/lead"), new AspectList().add(Aspect.METAL, 10).add(Aspect.EARTH, 5).add(Aspect.ORDER, 5));
		tag(t("c:raw_materials/lead"), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.ORDER, 10));
		tag(t("c:ingots/brass"), new AspectList().add(Aspect.METAL, 10).add(Aspect.TOOL, 5));
		tag(t("c:dusts/brass"), new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.TOOL, 5));
		tag(t("c:ingots/bronze"), new AspectList().add(Aspect.METAL, 10).add(Aspect.TOOL, 5));
		tag(t("c:dusts/bronze"), new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.TOOL, 5));
		tag(t("c:ores/uranium"), new AspectList().add(Aspect.METAL, 10).add(Aspect.DEATH, 5).add(Aspect.ENERGY, 10));
		tag(t("c:raw_materials/uranium"), new AspectList().add(Aspect.METAL, 10).add(Aspect.DEATH, 5).add(Aspect.ENERGY, 10));
		tag(t("c:ingots/uranium"), new AspectList().add(Aspect.METAL, 10).add(Aspect.DEATH, 5).add(Aspect.ENERGY, 10));
		tag(t("c:gems/ruby"), new AspectList().add(Aspect.CRYSTAL, 10).add(Aspect.DESIRE, 10));
		tag(t("c:gems/green_sapphire"), new AspectList().add(Aspect.CRYSTAL, 10).add(Aspect.DESIRE, 10));
		tag(t("c:gems/sapphire"), new AspectList().add(Aspect.CRYSTAL, 10).add(Aspect.DESIRE, 10));
		tag(t("c:ingots/steel"), new AspectList().add(Aspect.METAL, 15).add(Aspect.ORDER, 5));
		tag(t("c:rubbers"), new AspectList().add(Aspect.MOTION, 5).add(Aspect.TOOL, 5));
		tag(t("c:stones"), new AspectList().add(Aspect.EARTH, 5));
		item(Blocks.GRANITE.asItem(), new AspectList().add(Aspect.EARTH, 5));
		item(Blocks.DIORITE.asItem(), new AspectList().add(Aspect.EARTH, 5));
		item(Blocks.ANDESITE.asItem(), new AspectList().add(Aspect.EARTH, 5));
		tag(t("c:cobblestones"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENTROPY, 1));
		item(Blocks.BEDROCK.asItem(), new AspectList().add(Aspect.VOID, 25).add(Aspect.ENTROPY, 25).add(Aspect.EARTH, 25).add(Aspect.DARKNESS, 25));
		item(Blocks.DIRT.asItem(), new AspectList().add(Aspect.EARTH, 5));
		item(Blocks.PODZOL.asItem(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.PLANT, 1));
		item(Blocks.FARMLAND.asItem(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.WATER, 2).add(Aspect.ORDER, 2));
		tag(ItemTags.SAND, new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENTROPY, 5));
		item(Blocks.GRASS_BLOCK.asItem(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.PLANT, 2));
		item(Blocks.DIRT_PATH.asItem(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.PLANT, 2).add(Aspect.ORDER, 2));
		tag(t("c:end_stones"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.DARKNESS, 5));
		tag(t("c:gravels"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENTROPY, 2));
		item(Blocks.MYCELIUM.asItem(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.PLANT, 1).add(Aspect.FLUX, 1));
		item(Items.CLAY_BALL, new AspectList().add(Aspect.WATER, 5).add(Aspect.EARTH, 5));
		item(Blocks.TERRACOTTA.asItem(), () -> of(Blocks.CLAY.asItem()).add(Aspect.FIRE, 1));
		tag(ItemTags.TERRACOTTA, () -> of(Blocks.CLAY.asItem()).add(Aspect.FIRE, 1).add(Aspect.SENSES, 1));
		item(Items.BRICK, () -> of(Items.CLAY_BALL).add(Aspect.FIRE, 1));
		tag(t("c:netherracks"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.FIRE, 2));
		item(Items.NETHER_BRICK, () -> of(Blocks.NETHERRACK.asItem()).add(Aspect.FIRE, 1).add(Aspect.ORDER, 1));
		item(Blocks.SOUL_SAND.asItem(), new AspectList().add(Aspect.EARTH, 3).add(Aspect.TRAP, 1).add(Aspect.SOUL, 3));
		tag(t("c:glass_blocks"), new AspectList().add(Aspect.CRYSTAL, 5));
		item(Blocks.MOSSY_COBBLESTONE.asItem(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.PLANT, 3).add(Aspect.ENTROPY, 1));
		tag(t("c:obsidians"), new AspectList().add(Aspect.EARTH, 5).add(Aspect.FIRE, 5).add(Aspect.DARKNESS, 5));
		item(Blocks.MOSSY_STONE_BRICKS.asItem(), () -> of(Blocks.STONE_BRICKS.asItem()).add(Aspect.PLANT, 1));
		item(Blocks.CRACKED_STONE_BRICKS.asItem(), () -> of(Blocks.STONE_BRICKS.asItem()).add(Aspect.ENTROPY, 1));
		item(Blocks.CHISELED_STONE_BRICKS.asItem(), () -> of(Blocks.STONE_BRICKS.asItem()).add(Aspect.ORDER, 1));
		item(Blocks.CHISELED_SANDSTONE.asItem(), () -> of(Blocks.SANDSTONE.asItem()).add(Aspect.ORDER, 1));
		item(Blocks.CUT_SANDSTONE.asItem(), () -> of(Blocks.SANDSTONE.asItem()).add(Aspect.ORDER, 1));
		tag(ItemTags.LOGS, new AspectList().add(Aspect.PLANT, 20));
		tag(ItemTags.SAPLINGS, new AspectList().add(Aspect.PLANT, 15).add(Aspect.LIFE, 5));
		tag(ItemTags.LEAVES, new AspectList().add(Aspect.PLANT, 5));
		item(Blocks.SHORT_GRASS.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1));
		item(Blocks.FERN.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1));
		item(Blocks.SUNFLOWER.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1).add(Aspect.SENSES, 5).add(Aspect.LIFE, 1));
		item(Blocks.LILAC.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1).add(Aspect.SENSES, 5).add(Aspect.LIFE, 1));
		item(Blocks.TALL_GRASS.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1));
		item(Blocks.LARGE_FERN.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1));
		item(Blocks.ROSE_BUSH.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1).add(Aspect.SENSES, 5).add(Aspect.LIFE, 1));
		item(Blocks.PEONY.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AIR, 1).add(Aspect.SENSES, 5).add(Aspect.LIFE, 1));
		item(Blocks.LILY_PAD.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.WATER, 1));
		item(Blocks.DEAD_BUSH.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.ENTROPY, 1));
		item(Blocks.VINE.asItem(), new AspectList().add(Aspect.PLANT, 5));
		item(Items.WHEAT_SEEDS, new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1));
		item(Items.MELON_SEEDS, new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1));
		item(Items.PUMPKIN_SEEDS, new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1));
		item(Items.BEETROOT_SEEDS, new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1));
		tag(t("c:crops/nether_wart"), new AspectList().add(Aspect.PLANT, 1).add(Aspect.FLUX, 2).add(Aspect.ALCHEMY, 3));
		tag(ItemTags.SMALL_FLOWERS, new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1).add(Aspect.SENSES, 5));
		tag(ItemTags.SMALL_FLOWERS, new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 1).add(Aspect.SENSES, 5));
		item(Blocks.CACTUS.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.WATER, 5).add(Aspect.AVERSION, 1));
		item(Blocks.BROWN_MUSHROOM.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.DARKNESS, 2).add(Aspect.EARTH, 2));
		item(Blocks.RED_MUSHROOM.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.DARKNESS, 2).add(Aspect.FIRE, 2));
		item(Blocks.BROWN_MUSHROOM_BLOCK.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.DARKNESS, 2).add(Aspect.EARTH, 2));
		item(Blocks.RED_MUSHROOM_BLOCK.asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.DARKNESS, 2).add(Aspect.FIRE, 2));
		item(Items.SUGAR_CANE, new AspectList().add(Aspect.PLANT, 5).add(Aspect.WATER, 3).add(Aspect.AIR, 2));
		tag(t("c:crops/wheat"), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5));
		item(Items.APPLE, new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5));
		tag(t("c:crops/carrot"), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5).add(Aspect.SENSES, 5));
		tag(t("c:crops/potato"), new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 5));
		item(Items.BEETROOT, new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5).add(Aspect.DESIRE, 1));
		item(Items.BAKED_POTATO, new AspectList().add(Aspect.PLANT, 5).add(Aspect.LIFE, 5));
		item(Items.POISONOUS_POTATO, new AspectList().add(Aspect.PLANT, 5).add(Aspect.DEATH, 5));
		item(Blocks.PUMPKIN.asItem(), new AspectList().add(Aspect.PLANT, 10));
		complexItem(Blocks.MELON.asItem(), new AspectList().add(Aspect.PLANT, 10).remove(Aspect.LIFE, 5));
		item(Items.MELON_SLICE, new AspectList().add(Aspect.PLANT, 1));
		item(Blocks.SPONGE.asItem(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.TRAP, 5).add(Aspect.VOID, 5));
		item(Blocks.WET_SPONGE.asItem(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.TRAP, 5).add(Aspect.WATER, 5));
		complexItem(Items.SUGAR, new AspectList().add(Aspect.DESIRE, 1).add(Aspect.ENERGY, 1));
		complexItem(Items.CAKE, new AspectList().add(Aspect.DESIRE, 1).add(Aspect.LIFE, 2));
		complexItem(Items.PUMPKIN_PIE, new AspectList().add(Aspect.DESIRE, 1).add(Aspect.LIFE, 2));
		tag(ItemTags.WOOL, new AspectList().add(Aspect.BEAST, 15).add(Aspect.CRAFT, 5));
		item(Items.EXPERIENCE_BOTTLE, new AspectList().add(Aspect.MIND, 20));
		item(Items.NAME_TAG, new AspectList().add(Aspect.MIND, 10).add(Aspect.BEAST, 10));
		item(Items.IRON_HORSE_ARMOR, new AspectList().add(Aspect.METAL, 15).add(Aspect.PROTECT, 10).add(Aspect.BEAST, 5));
		item(Items.GOLDEN_HORSE_ARMOR, new AspectList().add(Aspect.METAL, 10).add(Aspect.PROTECT, 15).add(Aspect.BEAST, 5));
		item(Items.DIAMOND_HORSE_ARMOR, new AspectList().add(Aspect.CRYSTAL, 15).add(Aspect.PROTECT, 20).add(Aspect.BEAST, 5));
		item(Blocks.MAGMA_BLOCK.asItem(), new AspectList().add(Aspect.FIRE, 10).add(Aspect.EARTH, 5));
		item(Blocks.CHORUS_FLOWER.asItem(), new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.SENSES, 5).add(Aspect.PLANT, 5));
		item(Blocks.CHORUS_PLANT.asItem(), new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.PLANT, 5));
		item(Items.CHORUS_FRUIT, new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.SENSES, 5).add(Aspect.PLANT, 5));
		item(Items.POPPED_CHORUS_FRUIT, new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.SENSES, 5).add(Aspect.PLANT, 4).add(Aspect.FIRE, 1));
		item(Blocks.ICE.asItem(), new AspectList().add(Aspect.COLD, 20));
		item(Blocks.PACKED_ICE.asItem(), new AspectList().add(Aspect.COLD, 15).add(Aspect.ORDER, 5));
		item(Items.SNOWBALL, new AspectList().add(Aspect.COLD, 1));
		complexItem(Items.COOKIE, new AspectList().add(Aspect.DESIRE, 1));
		item(Items.POTION, new AspectList().add(Aspect.WATER, 5).add(Aspect.CRYSTAL, 5));
		complexItem(Blocks.TORCH.asItem(), new AspectList().add(Aspect.LIGHT, 5));
		item(Blocks.COBWEB.asItem(), new AspectList().add(Aspect.TRAP, 5).add(Aspect.BEAST, 1));
		item(Items.FLINT, new AspectList().add(Aspect.EARTH, 5).add(Aspect.TOOL, 5));
		tag(t("c:strings"), new AspectList().add(Aspect.BEAST, 5).add(Aspect.CRAFT, 1));
		tag(t("c:slime_balls"), new AspectList().add(Aspect.WATER, 5).add(Aspect.LIFE, 5).add(Aspect.ALCHEMY, 1));
		tag(t("c:leathers"), new AspectList().add(Aspect.BEAST, 5).add(Aspect.PROTECT, 5));
		item(Items.ROTTEN_FLESH, new AspectList().add(Aspect.MAN, 5).add(Aspect.LIFE, 5).add(Aspect.ENTROPY, 5));
		tag(t("c:feathers"), new AspectList().add(Aspect.FLIGHT, 5).add(Aspect.AIR, 5));
		tag(t("c:bones"), new AspectList().add(Aspect.DEATH, 5).add(Aspect.LIFE, 5));
		tag(t("c:eggs"), new AspectList().add(Aspect.LIFE, 5).add(Aspect.BEAST, 5));
		item(Items.SPIDER_EYE, new AspectList().add(Aspect.SENSES, 5).add(Aspect.BEAST, 5).add(Aspect.DEATH, 5));
		tag(t("c:gunpowders"), new AspectList().add(Aspect.FIRE, 10).add(Aspect.ENTROPY, 10).add(Aspect.ALCHEMY, 5));
		item(Items.COD, new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.WATER, 5));
		item(Items.SALMON, new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.WATER, 5));
		item(Items.TROPICAL_FISH, new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.WATER, 5));
		item(Items.PUFFERFISH, new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.WATER, 5));
		item(Items.COOKED_COD, new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
		item(Items.COOKED_SALMON, new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
		item(Items.CHICKEN, new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.AIR, 5));
		item(Items.COOKED_CHICKEN, new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
		item(Items.PORKCHOP, new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 5));
		item(Items.COOKED_PORKCHOP, new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
		item(Items.BEEF, new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 5));
		item(Items.COOKED_BEEF, new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
		item(Items.MUTTON, new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 5));
		item(Items.COOKED_MUTTON, new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
		item(Items.RABBIT, new AspectList().add(Aspect.BEAST, 5).add(Aspect.LIFE, 5).add(Aspect.EARTH, 5));
		item(Items.COOKED_RABBIT, new AspectList().add(Aspect.CRAFT, 1).add(Aspect.BEAST, 5).add(Aspect.LIFE, 5));
		item(Items.RABBIT_HIDE, new AspectList().add(Aspect.BEAST, 5).add(Aspect.PROTECT, 2));
		item(Items.RABBIT_FOOT, new AspectList().add(Aspect.BEAST, 5).add(Aspect.PROTECT, 5).add(Aspect.MOTION, 10).add(Aspect.ALCHEMY, 5));
		item(Items.BLAZE_ROD, new AspectList().add(Aspect.FIRE, 15).add(Aspect.ENERGY, 5));
		item(Items.SADDLE, new AspectList().add(Aspect.BEAST, 10).add(Aspect.MOTION, 10).add(Aspect.ORDER, 5));
		tag(t("c:ender_pearls"), new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.MOTION, 15));
		item(Items.GHAST_TEAR, new AspectList().add(Aspect.UNDEAD, 5).add(Aspect.SOUL, 10).add(Aspect.ALCHEMY, 10));
		item(Items.SKELETON_SKULL, new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.UNDEAD, 10));
		item(Items.WITHER_SKELETON_SKULL, new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.UNDEAD, 10));
		item(Items.ZOMBIE_HEAD, new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.MAN, 10));
		item(Items.PLAYER_HEAD, new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.MAN, 10));
		item(Items.CREEPER_HEAD, new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.ENTROPY, 5).add(Aspect.FIRE, 5));
		item(Items.DRAGON_HEAD, new AspectList().add(Aspect.DEATH, 10).add(Aspect.SOUL, 10).add(Aspect.FIRE, 10).add(Aspect.DARKNESS, 10));
		item(Items.DRAGON_BREATH, new AspectList().add(Aspect.DARKNESS, 10).add(Aspect.ENTROPY, 10).add(Aspect.FIRE, 10).add(Aspect.ALCHEMY, 10));
		complexItem(Items.CAULDRON, new AspectList().add(Aspect.ALCHEMY, 15));
		complexItem(Items.FERMENTED_SPIDER_EYE, new AspectList().add(Aspect.ALCHEMY, 5));
		complexItem(Items.BLAZE_POWDER, new AspectList().add(Aspect.ALCHEMY, 5));
		complexItem(Items.GLISTERING_MELON_SLICE, new AspectList().add(Aspect.ALCHEMY, 5));
		complexItem(Items.MAGMA_CREAM, new AspectList().add(Aspect.ALCHEMY, 5));
		item(Items.TOTEM_OF_UNDYING, new AspectList().add(Aspect.ORDER, 10).add(Aspect.ENTROPY, 10).add(Aspect.LIFE, 25).add(Aspect.UNDEAD, 10));
		item(Items.SHULKER_SHELL, new AspectList().add(Aspect.PROTECT, 10).add(Aspect.ELDRITCH, 5).add(Aspect.BEAST, 5).add(Aspect.VOID, 5));
		item(Blocks.BLACK_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.BLUE_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.GREEN_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.RED_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.YELLOW_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.LIGHT_GRAY_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.WHITE_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.ORANGE_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.PINK_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.GRAY_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.CYAN_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.LIGHT_BLUE_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.LIME_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.MAGENTA_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Blocks.BROWN_SHULKER_BOX.asItem(), () -> of(Blocks.PURPLE_SHULKER_BOX.asItem()));
		item(Items.MUSIC_DISC_11, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.DESIRE, 15));
		item(Items.MUSIC_DISC_13, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.WATER, 5).add(Aspect.DESIRE, 10));
		item(Items.MUSIC_DISC_CAT, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.BEAST, 5).add(Aspect.DESIRE, 10));
		item(Items.MUSIC_DISC_CHIRP, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10));
		item(Items.MUSIC_DISC_FAR, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.ELDRITCH, 5).add(Aspect.DESIRE, 10));
		item(Items.MUSIC_DISC_MALL, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.MAN, 5).add(Aspect.DESIRE, 10));
		item(Items.MUSIC_DISC_MELLOHI, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.CRAFT, 5).add(Aspect.DESIRE, 10));
		item(Items.MUSIC_DISC_STAL, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.DARKNESS, 5).add(Aspect.DESIRE, 10));
		item(Items.MUSIC_DISC_STRAD, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.ENERGY, 5).add(Aspect.DESIRE, 10));
		item(Items.MUSIC_DISC_WARD, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.LIFE, 5).add(Aspect.DESIRE, 10));
		item(Items.MUSIC_DISC_BLOCKS, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.TOOL, 5).add(Aspect.DESIRE, 10));
		item(Items.MUSIC_DISC_WAIT, new AspectList().add(Aspect.SENSES, 15).add(Aspect.AIR, 5).add(Aspect.TRAP, 5).add(Aspect.DESIRE, 10));
		tag(t("c:nether_stars"), new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.MAGIC, 20).add(Aspect.ORDER, 20).add(Aspect.AURA, 10));
		item(Items.CHAINMAIL_HELMET, new AspectList().add(Aspect.METAL, 42));
		item(Items.CHAINMAIL_CHESTPLATE, new AspectList().add(Aspect.METAL, 67));
		item(Items.CHAINMAIL_LEGGINGS, new AspectList().add(Aspect.METAL, 58));
		item(Items.CHAINMAIL_BOOTS, new AspectList().add(Aspect.METAL, 33));
		complexItem(Items.PAPER, new AspectList().add(Aspect.MIND, 2));
		complexItem(Items.ENCHANTED_BOOK, () -> of(Items.BOOK));
		complexItem(Blocks.BOOKSHELF.asItem(), new AspectList().add(Aspect.MIND, 8));
		item(Blocks.DRAGON_EGG.asItem(), new AspectList().add(Aspect.ELDRITCH, 15).add(Aspect.BEAST, 15).add(Aspect.DARKNESS, 15).add(Aspect.MOTION, 15).add(Aspect.MAGIC, 5));
		item(Blocks.END_PORTAL_FRAME.asItem(), new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.ENERGY, 10).add(Aspect.MOTION, 10).add(Aspect.MAGIC, 5));
		item(Blocks.SPAWNER.asItem(), new AspectList().add(Aspect.BEAST, 20).add(Aspect.MOTION, 20).add(Aspect.UNDEAD, 20).add(Aspect.MAGIC, 20));
		item(Items.PRISMARINE_SHARD, new AspectList().add(Aspect.WATER, 5).add(Aspect.EARTH, 5));
		item(Items.PRISMARINE_CRYSTALS, new AspectList().add(Aspect.WATER, 5).add(Aspect.CRYSTAL, 5).add(Aspect.LIGHT, 5));
		item(Items.ELYTRA, new AspectList().add(Aspect.FLIGHT, 20).add(Aspect.MOTION, 15));
		item(Blocks.END_ROD.asItem(), new AspectList().add(Aspect.FIRE, 1).add(Aspect.LIGHT, 5));
		complexItem(Blocks.CHEST.asItem(), new AspectList().add(Aspect.VOID, 15));
		complexItem(Blocks.TRAPPED_CHEST.asItem(), new AspectList().add(Aspect.TRAP, 10));
		complexItem(Items.ENDER_EYE, new AspectList().add(Aspect.SENSES, 10).add(Aspect.MAGIC, 5));
		complexItem(Items.ARROW, new AspectList().add(Aspect.AVERSION, 5));
		complexItem(Items.GLASS_BOTTLE, new AspectList().add(Aspect.VOID, 5));
		complexItem(Items.GOLDEN_APPLE, new AspectList().add(Aspect.MAGIC, 5).add(Aspect.LIFE, 10));
		complexItem(Items.ENCHANTED_GOLDEN_APPLE, new AspectList().add(Aspect.MAGIC, 5).add(Aspect.LIFE, 15).add(Aspect.PROTECT, 15));
		complexItem(Items.BOWL, new AspectList().add(Aspect.VOID, 5));
		complexItem(Items.MUSHROOM_STEW, new AspectList().add(Aspect.LIFE, 5));
		complexItem(Items.MINECART, new AspectList().add(Aspect.MOTION, 15));
		complexItem(Items.IRON_DOOR, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.WOODEN_DOORS, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.WOODEN_DOORS, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.WOODEN_DOORS, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.WOODEN_DOORS, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.WOODEN_DOORS, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.WOODEN_DOORS, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.BOATS, new AspectList().add(Aspect.WATER, 10).add(Aspect.MOTION, 15));
		complexTag(ItemTags.BOATS, new AspectList().add(Aspect.WATER, 10).add(Aspect.MOTION, 15));
		complexTag(ItemTags.BOATS, new AspectList().add(Aspect.WATER, 10).add(Aspect.MOTION, 15));
		complexTag(ItemTags.BOATS, new AspectList().add(Aspect.WATER, 10).add(Aspect.MOTION, 15));
		complexTag(ItemTags.BOATS, new AspectList().add(Aspect.WATER, 10).add(Aspect.MOTION, 15));
		complexTag(ItemTags.BOATS, new AspectList().add(Aspect.WATER, 10).add(Aspect.MOTION, 15));
		complexItem(Items.FLINT_AND_STEEL, new AspectList().add(Aspect.FIRE, 10).add(Aspect.TOOL, 5));
		complexItem(Items.FISHING_ROD, new AspectList().add(Aspect.WATER, 10).add(Aspect.TOOL, 5));
		complexItem(Items.SHIELD, new AspectList().add(Aspect.PROTECT, 20));
		complexItem(Items.SPECTRAL_ARROW, new AspectList().add(Aspect.SENSES, 10).add(Aspect.MAGIC, 5));
		complexItem(Items.BUCKET, new AspectList().add(Aspect.VOID, 5));
		item(Items.WATER_BUCKET, () -> of(Items.BUCKET).add(Aspect.WATER, 20));
		item(Items.LAVA_BUCKET, () -> of(Items.BUCKET).add(Aspect.FIRE, 15).add(Aspect.EARTH, 5));
		item(Items.MILK_BUCKET, () -> of(Items.BUCKET).add(Aspect.LIFE, 10).add(Aspect.BEAST, 5).add(Aspect.WATER, 5));
		complexItem(Items.BREWING_STAND, new AspectList().add(Aspect.CRAFT, 15).add(Aspect.ALCHEMY, 25));
		complexItem(Blocks.STONE_BUTTON.asItem(), new AspectList().add(Aspect.MECHANISM, 5));
		complexItem(Blocks.RAIL.asItem(), new AspectList().add(Aspect.MOTION, 10));
		complexItem(Blocks.DETECTOR_RAIL.asItem(), new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.SENSES, 1));
		complexItem(Blocks.POWERED_RAIL.asItem(), new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.ENERGY, 1));
		complexItem(Blocks.ACTIVATOR_RAIL.asItem(), new AspectList().add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.FENCE_GATES, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.FENCE_GATES, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.FENCE_GATES, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.FENCE_GATES, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.FENCE_GATES, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.FENCE_GATES, new AspectList().add(Aspect.TRAP, 5).add(Aspect.MECHANISM, 5));
		complexTag(ItemTags.WOODEN_PRESSURE_PLATES, new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.SENSES, 5));
		complexItem(Blocks.STONE_PRESSURE_PLATE.asItem(), new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.SENSES, 5));
		complexItem(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.asItem(), new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.SENSES, 5));
		complexItem(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.asItem(), new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.SENSES, 5));
		complexItem(Blocks.LEVER.asItem(), new AspectList().add(Aspect.MECHANISM, 5));
		complexItem(Blocks.PISTON.asItem(), new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.MOTION, 10));
		complexItem(Blocks.STICKY_PISTON.asItem(), new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.MOTION, 10));
		complexItem(Blocks.JUKEBOX.asItem(), new AspectList().add(Aspect.SENSES, 20).add(Aspect.MECHANISM, 10).add(Aspect.AIR, 15));
		complexItem(Blocks.NOTE_BLOCK.asItem(), new AspectList().add(Aspect.SENSES, 20).add(Aspect.MECHANISM, 10).add(Aspect.AIR, 15));
		complexTag(ItemTags.WOODEN_TRAPDOORS, new AspectList().add(Aspect.MOTION, 5));
		complexItem(Blocks.FURNACE.asItem(), new AspectList().add(Aspect.FIRE, 10));
		complexItem(Blocks.ENCHANTING_TABLE.asItem(), new AspectList().add(Aspect.MAGIC, 25).add(Aspect.CRAFT, 15));
		complexItem(Blocks.CRAFTING_TABLE.asItem(), new AspectList().add(Aspect.CRAFT, 20));
		complexItem(Items.CLOCK, new AspectList().add(Aspect.MECHANISM, 10));
		complexItem(Blocks.BEACON.asItem(), new AspectList().add(Aspect.AURA, 10).add(Aspect.MAGIC, 10).add(Aspect.EXCHANGE, 10));
		complexTag(ItemTags.WOODEN_BUTTONS, new AspectList().add(Aspect.MECHANISM, 5));
		complexItem(Items.CARROT_ON_A_STICK, new AspectList().add(Aspect.MOTION, 5).add(Aspect.DESIRE, 10));
		complexItem(Items.FLOWER_POT, new AspectList().add(Aspect.VOID, 5).add(Aspect.PLANT, 5));
		complexItem(Items.GOLDEN_CARROT, new AspectList().add(Aspect.SENSES, 10).add(Aspect.ALCHEMY, 5));
		complexItem(Blocks.ENDER_CHEST.asItem(), new AspectList().merge(Aspect.EXCHANGE, 10).merge(Aspect.MOTION, 10).merge(Aspect.VOID, 20));
		complexItem(Items.COMPARATOR, new AspectList().merge(Aspect.MECHANISM, 15).merge(Aspect.ORDER, 5).merge(Aspect.SENSES, 5));
		complexItem(Items.REPEATER, new AspectList().merge(Aspect.MECHANISM, 15).merge(Aspect.ENERGY, 10));
		complexItem(Blocks.HOPPER.asItem(), new AspectList().merge(Aspect.MECHANISM, 5).merge(Aspect.EXCHANGE, 10).merge(Aspect.VOID, 5));
		complexItem(Blocks.DROPPER.asItem(), new AspectList().merge(Aspect.MECHANISM, 5).merge(Aspect.EXCHANGE, 10).merge(Aspect.VOID, 5));
		complexItem(Blocks.DISPENSER.asItem(), new AspectList().merge(Aspect.MECHANISM, 5).merge(Aspect.EXCHANGE, 10).merge(Aspect.VOID, 5));
		item(Blocks.TRIPWIRE_HOOK.asItem(), new AspectList().add(Aspect.SENSES, 5).add(Aspect.MECHANISM, 5).add(Aspect.TRAP, 5));
		complexItem(Blocks.DAYLIGHT_DETECTOR.asItem(), new AspectList().merge(Aspect.SENSES, 10).merge(Aspect.LIGHT, 10).merge(Aspect.MECHANISM, 5));
		complexTag(t("c:gears"), new AspectList().add(Aspect.MECHANISM, 5));
		item(Items.INK_SAC, new AspectList().add(Aspect.WATER, 2).add(Aspect.BEAST, 2));
		item(Items.GREEN_DYE, () -> of(Blocks.CACTUS.asItem()).add(Aspect.WATER, 1).add(Aspect.FIRE, 1));
		item(Items.COCOA_BEANS, new AspectList().add(Aspect.DESIRE, 2).add(Aspect.ENERGY, 2));
		item(Items.LAPIS_LAZULI, new AspectList().add(Aspect.EARTH, 2).add(Aspect.DESIRE, 2));
		item(Items.BONE_MEAL, new AspectList().add(Aspect.LIFE, 2).add(Aspect.DEATH, 1).add(Aspect.PLANT, 1));
		item(ItemsTC.clusterIron.get(), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5));
		item(ItemsTC.clusterGold.get(), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10));
		item(ItemsTC.clusterCinnabar.get(), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.ALCHEMY, 5).add(Aspect.DEATH, 5));
		item(ItemsTC.clusterQuartz.get(), new AspectList().add(Aspect.ORDER, 5).add(Aspect.CRYSTAL, 10));
		item(ItemsTC.clusterCopper.get(), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.EXCHANGE, 10));
		item(ItemsTC.clusterTin.get(), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.CRYSTAL, 10));
		item(ItemsTC.clusterSilver.get(), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10));
		// the original registered ORDER twice for lead (5 then 10); AspectList#add sums them
		item(ItemsTC.clusterLead.get(), new AspectList().add(Aspect.ORDER, 5).add(Aspect.METAL, 15).add(Aspect.EARTH, 5).add(Aspect.ORDER, 10));
		item(ItemsTC.oreCinnabar.get(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.METAL, 10).add(Aspect.ALCHEMY, 5).add(Aspect.DEATH, 5));
		item(ItemsTC.oreAmber.get(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.TRAP, 10).add(Aspect.CRYSTAL, 10));
		item(ItemsTC.quicksilver.get(), new AspectList().add(Aspect.METAL, 10).add(Aspect.DEATH, 5).add(Aspect.ALCHEMY, 5));
		item(ItemsTC.amber.get(), new AspectList().add(Aspect.TRAP, 10).add(Aspect.CRYSTAL, 10));
	}

	private static void colours() {
		item(Items.WHITE_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.ORANGE_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.MAGENTA_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.LIGHT_BLUE_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.YELLOW_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.LIME_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.PINK_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.GRAY_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.LIGHT_GRAY_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.CYAN_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.PURPLE_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.BLUE_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.BROWN_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.GREEN_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.RED_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		item(Items.BLACK_GLAZED_TERRACOTTA, () -> of(Items.WHITE_TERRACOTTA).add(Aspect.SENSES, 1).add(Aspect.FIRE, 1));
		complexItem(Items.WHITE_CONCRETE, () -> of(Items.WHITE_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.ORANGE_CONCRETE, () -> of(Items.ORANGE_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.MAGENTA_CONCRETE, () -> of(Items.MAGENTA_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.LIGHT_BLUE_CONCRETE, () -> of(Items.LIGHT_BLUE_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.YELLOW_CONCRETE, () -> of(Items.YELLOW_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.LIME_CONCRETE, () -> of(Items.LIME_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.PINK_CONCRETE, () -> of(Items.PINK_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.GRAY_CONCRETE, () -> of(Items.GRAY_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.LIGHT_GRAY_CONCRETE, () -> of(Items.LIGHT_GRAY_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.CYAN_CONCRETE, () -> of(Items.CYAN_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.PURPLE_CONCRETE, () -> of(Items.PURPLE_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.BLUE_CONCRETE, () -> of(Items.BLUE_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.BROWN_CONCRETE, () -> of(Items.BROWN_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.GREEN_CONCRETE, () -> of(Items.GREEN_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.RED_CONCRETE, () -> of(Items.RED_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
		complexItem(Items.BLACK_CONCRETE, () -> of(Items.BLACK_CONCRETE_POWDER).add(Aspect.WATER, 1).add(Aspect.ORDER, 1));
	}

	/** Materials that only exist since the original's Minecraft version (it had no raw metals, amethyst, ...): the values
	 * the port had already assigned them, kept so nothing regresses. */
	private static void modern() {
		item(Items.RAW_IRON, new AspectList().add(Aspect.METAL, 15).add(Aspect.ENTROPY, 1));
		item(Items.RAW_GOLD, new AspectList().add(Aspect.METAL, 10).add(Aspect.DESIRE, 10).add(Aspect.ENTROPY, 1));
		item(Items.RAW_COPPER, new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.EXCHANGE, 5));
		item(Items.NETHERITE_SCRAP, new AspectList().add(Aspect.METAL, 15).add(Aspect.FIRE, 10));
		item(Items.AMETHYST_SHARD, new AspectList().add(Aspect.CRYSTAL, 10).add(Aspect.MAGIC, 5));
	}

	private static Item blk(net.neoforged.neoforge.registries.DeferredBlock<? extends net.minecraft.world.level.block.Block> b) {
		return b.get().asItem();
	}

	/** The mod's own entries from the original table. Ones for content this port has not built yet (eldritch blocks, the
	 * runed tablet, celestial notes, Eldritch Eye, ambient grass, the Pure fluid bucket) are simply not listed. */
	private static void thaumcraft() {
		for (var candle : BlocksTC.candles.values()) complexItem(blk(candle), new AspectList().add(Aspect.LIGHT, 5));
		for (var banner : BlocksTC.banners.values()) complexItem(blk(banner), new AspectList().add(Aspect.ELDRITCH, 5));
		for (var crystal : BlocksTC.crystals.entrySet()) {
			item(blk(crystal.getValue()), new AspectList().add(crystal.getKey(), 15).add(Aspect.CRYSTAL, 10));
		}

		item(ItemsTC.phial.get(), new AspectList().add(Aspect.VOID, 3));
		complexItem(blk(BlocksTC.tableWood), new AspectList().add(Aspect.TOOL, 1));
		complexItem(blk(BlocksTC.tableStone), new AspectList().add(Aspect.TOOL, 1));
		item(blk(BlocksTC.arcaneWorkbench), () -> of(Items.CRAFTING_TABLE).add(Aspect.MAGIC, 5).add(Aspect.AURA, 5));
		item(ItemsTC.tripleMeatTreat.get(), new AspectList().add(Aspect.LIFE, 10).add(Aspect.DESIRE, 10));
		item(ItemsTC.bucketDeath.get(), () -> of(Items.BUCKET).add(Aspect.DEATH, 15).add(Aspect.ENTROPY, 15));
		item(ItemsTC.nuggetRareearth.get(), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ORDER, 5).add(Aspect.METAL, 5));

		item(blk(BlocksTC.taintFibre), new AspectList().add(Aspect.PLANT, 5).add(Aspect.FLUX, 10));
		item(blk(BlocksTC.taintCrust), new AspectList().add(Aspect.LIFE, 5).add(Aspect.FLUX, 5));
		item(blk(BlocksTC.taintSoil), new AspectList().add(Aspect.EARTH, 5).add(Aspect.FLUX, 5));
		item(blk(BlocksTC.taintGeyser), new AspectList().add(Aspect.AURA, 5).add(Aspect.WATER, 5).add(Aspect.FLUX, 10));
		item(blk(BlocksTC.taintRock), new AspectList().add(Aspect.EARTH, 10).add(Aspect.FLUX, 5));
		item(blk(BlocksTC.taintFeature), new AspectList().add(Aspect.AURA, 5).add(Aspect.BEAST, 5).add(Aspect.FLUX, 10));
		item(blk(BlocksTC.taintLog), new AspectList().add(Aspect.PLANT, 5).add(Aspect.FLUX, 5));

		item(blk(BlocksTC.logGreatwood), new AspectList().add(Aspect.PLANT, 20).add(Aspect.LIFE, 5));
		item(blk(BlocksTC.logSilverwood), new AspectList().add(Aspect.PLANT, 20).add(Aspect.AURA, 5));
		item(blk(BlocksTC.leavesGreatwood), new AspectList().add(Aspect.PLANT, 5));
		item(blk(BlocksTC.leavesSilverwood), new AspectList().add(Aspect.PLANT, 5));
		item(blk(BlocksTC.saplingGreatwood), new AspectList().add(Aspect.PLANT, 15).add(Aspect.LIFE, 5));
		item(blk(BlocksTC.saplingSilverwood), new AspectList().add(Aspect.PLANT, 15).add(Aspect.AURA, 5));
		item(blk(BlocksTC.shimmerleaf), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AURA, 10).add(Aspect.ENERGY, 5));
		item(blk(BlocksTC.cinderpearl), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AURA, 5).add(Aspect.FIRE, 10));
		item(blk(BlocksTC.vishroom), new AspectList().add(Aspect.PLANT, 2).add(Aspect.DEATH, 1).add(Aspect.MAGIC, 1).add(Aspect.ENTROPY, 1));

		item(blk(BlocksTC.stoneAncient), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
		item(blk(BlocksTC.stoneAncientTile), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
		item(blk(BlocksTC.stoneAncientRock), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
		item(blk(BlocksTC.stoneEldritchTile), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
		item(blk(BlocksTC.stoneAncientDoorway), new AspectList().add(Aspect.METAL, 5).add(Aspect.ELDRITCH, 5).add(Aspect.TRAP, 5));
		item(blk(BlocksTC.stoneAncientGlyphed), new AspectList().add(Aspect.METAL, 5).add(Aspect.ELDRITCH, 5).add(Aspect.MIND, 5));
		item(blk(BlocksTC.stonePorous), new AspectList().add(Aspect.EARTH, 5).add(Aspect.VOID, 5));

		item(blk(BlocksTC.researchTable), () -> of(blk(BlocksTC.tableWood)).add(Aspect.MIND, 5));
		item(ItemsTC.brain.get(), new AspectList().add(Aspect.LIFE, 5).add(Aspect.MIND, 20).add(Aspect.UNDEAD, 10));
		item(ItemsTC.lootBagCommon.get(), new AspectList().add(Aspect.DESIRE, 10));
		item(ItemsTC.lootBagUncommon.get(), new AspectList().add(Aspect.DESIRE, 20));
		item(ItemsTC.lootBagRare.get(), new AspectList().add(Aspect.DESIRE, 30));
		item(blk(BlocksTC.lootUrnCommon), new AspectList().add(Aspect.DESIRE, 10).add(Aspect.EARTH, 2));
		item(blk(BlocksTC.lootUrnUncommon), new AspectList().add(Aspect.DESIRE, 20).add(Aspect.EARTH, 2));
		item(blk(BlocksTC.lootUrnRare), new AspectList().add(Aspect.DESIRE, 30).add(Aspect.EARTH, 2));
		item(blk(BlocksTC.lootCrateCommon), new AspectList().add(Aspect.DESIRE, 10).add(Aspect.PLANT, 2));
		item(blk(BlocksTC.lootCrateUncommon), new AspectList().add(Aspect.DESIRE, 20).add(Aspect.PLANT, 2));
		item(blk(BlocksTC.lootCrateRare), new AspectList().add(Aspect.DESIRE, 30).add(Aspect.PLANT, 2));
		for (Item chunk : new Item[] { ItemsTC.chunkBeef.get(), ItemsTC.chunkChicken.get(), ItemsTC.chunkPork.get(), ItemsTC.chunkFish.get(),
				ItemsTC.chunkRabbit.get(), ItemsTC.chunkMutton.get() }) {
			item(chunk, new AspectList().add(Aspect.LIFE, 5).add(Aspect.ENTROPY, 1));
		}
		item(ItemsTC.salisMundus.get(), new AspectList().add(Aspect.MAGIC, 5).add(Aspect.ENERGY, 5));
		item(blk(BlocksTC.crucible), () -> of(Items.CAULDRON).add(Aspect.CRAFT, 20).add(Aspect.ALCHEMY, 20));
		item(ItemsTC.thaumonomicon.get(), () -> of(Items.BOOKSHELF).merge(Aspect.MAGIC, 10));
		complexItem(blk(BlocksTC.pedestal), new AspectList().add(Aspect.MAGIC, 3).add(Aspect.AIR, 3));
		complexItem(blk(BlocksTC.pedestalAncient), new AspectList().add(Aspect.MAGIC, 3).add(Aspect.ELDRITCH, 3));
		complexItem(blk(BlocksTC.pedestalEldritch), new AspectList().add(Aspect.MAGIC, 3).add(Aspect.ELDRITCH, 3));
		complexItem(ItemsTC.thaumometer.get(), new AspectList().add(Aspect.SENSES, 10).add(Aspect.AURA, 10));
		complexItem(ItemsTC.goggles.get(), new AspectList().merge(Aspect.SENSES, 10).merge(Aspect.AURA, 10));
		complexItem(blk(BlocksTC.arcaneEar), new AspectList().add(Aspect.SENSES, 20));
		item(ItemsTC.amuletVisFound.get(), new AspectList().add(Aspect.AURA, 20).add(Aspect.METAL, 5).add(Aspect.MAGIC, 5));
		item(ItemsTC.amuletVisCrafted.get(), new AspectList().add(Aspect.AURA, 20).add(Aspect.METAL, 5).add(Aspect.MAGIC, 5));
		item(ItemsTC.curiosityBand.get(), new AspectList().add(Aspect.AURA, 5).add(Aspect.METAL, 5).add(Aspect.MAGIC, 20));

		item(ItemsTC.crimsonPlateChest.get(), () -> of(Items.IRON_CHESTPLATE).add(Aspect.ELDRITCH, 5));
		item(ItemsTC.crimsonPraetorChest.get(), () -> of(Items.IRON_CHESTPLATE).add(Aspect.ELDRITCH, 10));
		item(ItemsTC.crimsonRobeChest.get(), () -> of(Items.LEATHER_CHESTPLATE).add(Aspect.MAGIC, 5).add(Aspect.ELDRITCH, 5));
		item(ItemsTC.crimsonPlateLegs.get(), () -> of(Items.IRON_LEGGINGS).add(Aspect.ELDRITCH, 5));
		item(ItemsTC.crimsonPraetorLegs.get(), () -> of(Items.IRON_LEGGINGS).add(Aspect.ELDRITCH, 10));
		item(ItemsTC.crimsonRobeLegs.get(), () -> of(Items.LEATHER_LEGGINGS).add(Aspect.MAGIC, 5).add(Aspect.ELDRITCH, 5));
		item(ItemsTC.crimsonPlateHelm.get(), () -> of(Items.IRON_HELMET).add(Aspect.ELDRITCH, 5));
		item(ItemsTC.crimsonPraetorHelm.get(), () -> of(Items.IRON_HELMET).add(Aspect.ELDRITCH, 10));
		item(ItemsTC.crimsonRobeHelm.get(), () -> of(Items.LEATHER_HELMET).add(Aspect.MAGIC, 5).add(Aspect.ELDRITCH, 5));
		item(ItemsTC.crimsonBoots.get(), () -> of(Items.IRON_BOOTS).add(Aspect.ELDRITCH, 5));
		item(ItemsTC.crimsonBlade.get(), () -> of(Items.IRON_SWORD).add(Aspect.ELDRITCH, 10).add(Aspect.DEATH, 10));
		// the original's Curio meta 4 (the plain one); its other six variants share this one item here
		// the seven curiosities, as the original ConfigAspects registered them by metadata
		item(ItemsTC.curio.get(), new AspectList().add(Aspect.MIND, 15).add(Aspect.MAGIC, 15));
		item(ItemsTC.curioPreserved.get(), new AspectList().add(Aspect.MIND, 15).add(Aspect.BEAST, 15));
		item(ItemsTC.curioAncient.get(), new AspectList().add(Aspect.MIND, 15).add(Aspect.DEATH, 15));
		item(ItemsTC.curioEldritch.get(), new AspectList().add(Aspect.MIND, 15).add(Aspect.ELDRITCH, 15));
		item(ItemsTC.curioKnowledge.get(), new AspectList().add(Aspect.MIND, 30));
		item(ItemsTC.curioTwisted.get(), new AspectList().add(Aspect.MIND, 15).add(Aspect.FLUX, 15));
		item(ItemsTC.curioRites.get(), new AspectList().add(Aspect.MIND, 15).add(Aspect.ELDRITCH, 5).add(Aspect.SOUL, 5).add(Aspect.MAGIC, 5));
		item(ItemsTC.primordialPearl.get(), new AspectList().add(Aspect.AIR, 10).add(Aspect.FIRE, 10).add(Aspect.WATER, 10)
				.add(Aspect.EARTH, 10).add(Aspect.ORDER, 10).add(Aspect.ENTROPY, 10));
	}
}

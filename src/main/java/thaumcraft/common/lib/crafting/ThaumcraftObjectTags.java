package thaumcraft.common.lib.crafting;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;

/**
 * Replaces the original (1.12.2) {@code AspectHelper}/{@code ThaumcraftCraftingManager} object-tag pair -
 * the global "what aspects does this arbitrary item have" database, backing
 * {@link thaumcraft.api.aspects.AspectHelper#getObjectAspects}/{@code generateTags} for real (both were a
 * permanent {@code null}-returning stub in {@code ThaumcraftInternalMethodHandler} until now). First real
 * consumers: the Smelter and Alembic (both need arbitrary-item aspects, not just
 * {@link IEssentiaContainerItem} items).
 *
 * The original kept one big flat map (33 hardcoded {@code registerObjectTag(String oreDictName, ...)}
 * calls) keyed by Forge's pre-1.13 ore-dictionary strings, which don't exist anymore. This port's
 * registration API takes real {@link TagKey}s instead - almost every one of the original's ore-dict names
 * has a 1:1 modern equivalent already shipped in {@code net.neoforged.neoforge.common.Tags.Items}, so this
 * is mostly a rename, not a redesign (kept code-driven rather than moved to a datapack format - a design
 * question raised directly with the user, see the port-tracking memory notes for the reasoning). A handful
 * of the original's entries reference materials that were never vanilla and haven't been ported by this
 * project either (silver/lead/tin/brass/bronze) - those simply have nothing to register against right now
 * and are left out rather than preserved as dead entries.
 *
 * Simplified/cut from the original for this pass, all real features:
 * <ul>
 * <li><b>No recipe-tracing fallback.</b> The original derived aspects for an item with no explicit
 * registration by walking its crafting/smelting/crucible/infusion recipe backward and summing ingredient
 * aspects. An item with no explicit tag match and no bonus-tag heuristic hit here just gets {@code null} -
 * same "no aspects known" outcome the original had for anything neither explicitly tagged nor
 * recipe-traceable.</li>
 * <li><b>No entity tags here</b> - the original's parallel {@code registerEntityTag} table (mob scans) is a
 * real, separate sibling class now: {@link ThaumcraftEntityTags}.</li>
 * <li><b>Tool-tier bonus is coarser.</b> The original scaled {@code Aspect.TOOL} off
 * {@code Item.ToolMaterial}'s harvest level (0-4, gone in modern Minecraft - tool-vs-block interaction
 * moved to tags). This uses {@link TieredItem#getTier()}'s durability ({@code getUses()}) as a proxy for
 * material quality instead - same intent (better tool material -> more Tool aspect), coarser buckets.</li>
 * <li><b>Enchantment bonus drops the per-enchantment rarity multiplier</b> (the original added +2/+4/+6 to
 * the Magic total per enchantment based on its {@code Enchantment.Rarity}) - just sums levels now. Every
 * individual enchantment's own aspect mapping (Sharpness -> Aversion, Efficiency -> Tool, etc.) is kept.</li>
 * </ul>
 */
public final class ThaumcraftObjectTags {

	private static final Map<Item, AspectList> ITEM_TAGS = new LinkedHashMap<>();
	private static final Map<TagKey<Item>, AspectList> TAG_TAGS = new LinkedHashMap<>();

	private ThaumcraftObjectTags() {
	}

	public static void register(Item item, AspectList aspects) {
		ITEM_TAGS.put(item, aspects);
	}

	public static void register(TagKey<Item> tag, AspectList aspects) {
		TAG_TAGS.put(tag, aspects);
	}

	/** The real implementation behind {@link AspectHelper#getObjectAspects}/{@code generateTags}. */
	public static AspectList getObjectTags(ItemStack stack) {
		if (stack.isEmpty()) return null;

		AspectList explicit = ITEM_TAGS.get(stack.getItem());
		if (explicit == null) {
			for (Map.Entry<TagKey<Item>, AspectList> entry : TAG_TAGS.entrySet()) {
				if (stack.is(entry.getKey())) {
					explicit = entry.getValue();
					break;
				}
			}
		}
		return capAspects(getBonusTags(stack, explicit), 500);
	}

	private static AspectList capAspects(AspectList source, int amount) {
		if (source == null) return null;
		AspectList out = new AspectList();
		for (Aspect aspect : source.getAspects()) {
			if (aspect != null) out.merge(aspect, Math.min(amount, source.getAmount(aspect)));
		}
		return out;
	}

	/**
	 * Adds derived bonuses on top of an item's explicit tag (armor/tool/weapon type, dye color,
	 * enchantments) - or, for an {@link IEssentiaContainerItem} that isn't ignoring its own contents,
	 * replaces the explicit tag outright with its actual held aspects (matches the original: a filled
	 * Warded Jar/Crystal Essence reports what it's holding, not a generic "jar" tag).
	 */
	private static AspectList getBonusTags(ItemStack stack, AspectList sourceTags) {
		AspectList tmp = new AspectList();
		if (stack.isEmpty()) return tmp;

		Item item = stack.getItem();
		if (item instanceof IEssentiaContainerItem container && !container.ignoreContainedAspects()) {
			AspectList held = container.getAspects(stack);
			if (held != null) {
				for (Aspect tag : held.getAspects()) {
					if (tag != null && held.getAmount(tag) > 0) tmp.add(tag, held.getAmount(tag));
				}
			}
		} else if (sourceTags != null) {
			for (Aspect tag : sourceTags.getAspects()) {
				if (tag != null) tmp.add(tag, sourceTags.getAmount(tag));
			}
		}

		if (item instanceof ArmorItem armor) {
			tmp.merge(Aspect.PROTECT, armor.getDefense() * 4);
		} else if (item instanceof TieredItem tiered) {
			int tierScore = Math.max(1, tiered.getTier().getUses() / 50);
			tmp.merge(Aspect.TOOL, tierScore * 4);
			if (item instanceof SwordItem) tmp.merge(Aspect.AVERSION, tierScore * 4);
		} else if (item instanceof BowItem) {
			tmp.merge(Aspect.AVERSION, 10).merge(Aspect.FLIGHT, 5);
		} else if (item instanceof ShearsItem) {
			tmp.merge(Aspect.TOOL, 8);
		}

		if (stack.is(Tags.Items.DYES)) {
			tmp.merge(Aspect.SENSES, 5);
		}

		int magicScore = enchantmentAspects(stack, tmp);
		if (magicScore > 0) tmp.merge(Aspect.MAGIC, magicScore);

		return AspectHelper.cullTags(tmp);
	}

	private static int enchantmentAspects(ItemStack stack, AspectList tmp) {
		ItemEnchantments enchantments = stack.getTagEnchantments();
		int total = 0;
		for (var entry : enchantments.entrySet()) {
			Holder<Enchantment> ench = entry.getKey();
			int lvl = entry.getIntValue() * 3;
			if (ench.is(Enchantments.AQUA_AFFINITY)) tmp.merge(Aspect.WATER, lvl);
			else if (ench.is(Enchantments.BANE_OF_ARTHROPODS)) tmp.merge(Aspect.BEAST, lvl / 2).merge(Aspect.AVERSION, lvl / 2);
			else if (ench.is(Enchantments.BLAST_PROTECTION)) tmp.merge(Aspect.PROTECT, lvl / 2).merge(Aspect.ENTROPY, lvl / 2);
			else if (ench.is(Enchantments.EFFICIENCY)) tmp.merge(Aspect.TOOL, lvl);
			else if (ench.is(Enchantments.FEATHER_FALLING)) tmp.merge(Aspect.FLIGHT, lvl);
			else if (ench.is(Enchantments.FIRE_ASPECT)) tmp.merge(Aspect.FIRE, lvl / 2).merge(Aspect.AVERSION, lvl / 2);
			else if (ench.is(Enchantments.FIRE_PROTECTION)) tmp.merge(Aspect.PROTECT, lvl / 2).merge(Aspect.FIRE, lvl / 2);
			else if (ench.is(Enchantments.FLAME)) tmp.merge(Aspect.FIRE, lvl);
			else if (ench.is(Enchantments.FORTUNE)) tmp.merge(Aspect.DESIRE, lvl);
			else if (ench.is(Enchantments.INFINITY)) tmp.merge(Aspect.CRAFT, lvl);
			else if (ench.is(Enchantments.KNOCKBACK)) tmp.merge(Aspect.AIR, lvl);
			else if (ench.is(Enchantments.LOOTING)) tmp.merge(Aspect.DESIRE, lvl);
			else if (ench.is(Enchantments.POWER)) tmp.merge(Aspect.AVERSION, lvl);
			else if (ench.is(Enchantments.PROJECTILE_PROTECTION)) tmp.merge(Aspect.PROTECT, lvl);
			else if (ench.is(Enchantments.PROTECTION)) tmp.merge(Aspect.PROTECT, lvl);
			else if (ench.is(Enchantments.PUNCH)) tmp.merge(Aspect.AIR, lvl);
			else if (ench.is(Enchantments.RESPIRATION)) tmp.merge(Aspect.AIR, lvl);
			else if (ench.is(Enchantments.SHARPNESS)) tmp.merge(Aspect.AVERSION, lvl);
			else if (ench.is(Enchantments.SILK_TOUCH)) tmp.merge(Aspect.EXCHANGE, lvl);
			else if (ench.is(Enchantments.THORNS)) tmp.merge(Aspect.AVERSION, lvl);
			else if (ench.is(Enchantments.SMITE)) tmp.merge(Aspect.UNDEAD, lvl / 2).merge(Aspect.AVERSION, lvl / 2);
			else if (ench.is(Enchantments.UNBREAKING)) tmp.merge(Aspect.EARTH, lvl);
			else if (ench.is(Enchantments.DEPTH_STRIDER)) tmp.merge(Aspect.WATER, lvl);
			else if (ench.is(Enchantments.LUCK_OF_THE_SEA)) tmp.merge(Aspect.DESIRE, lvl);
			else if (ench.is(Enchantments.LURE)) tmp.merge(Aspect.BEAST, lvl);
			else if (ench.is(Enchantments.FROST_WALKER)) tmp.merge(Aspect.COLD, lvl);
			else if (ench.is(Enchantments.MENDING)) tmp.merge(Aspect.CRAFT, lvl);
			total += entry.getIntValue();
		}
		return total;
	}

	/**
	 * The real item-tag table (replaces {@code ConfigAspects#registerItemAspects}) - a genuinely useful
	 * starting set (ores/ingots/nuggets/raw materials/gems/dusts/rods/storage blocks, all real
	 * {@code net.neoforged.neoforge.common.Tags.Items} matches), not the original's full 333 entries. Add
	 * to this as specific items need real aspects; nothing here is load-bearing for any one specific
	 * feature, so it's safe to extend incrementally.
	 */
	public static void init() {
		register(Tags.Items.ORES_COAL, new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENERGY, 15).add(Aspect.FIRE, 15));
		register(Items.COAL, new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10));
		register(Items.CHARCOAL, new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10));

		register(Tags.Items.ORES_IRON, new AspectList().add(Aspect.EARTH, 5).add(Aspect.METAL, 15));
		register(Tags.Items.RAW_MATERIALS_IRON, new AspectList().add(Aspect.METAL, 15).add(Aspect.ENTROPY, 1));
		register(Tags.Items.INGOTS_IRON, new AspectList().add(Aspect.METAL, 15));
		register(Tags.Items.NUGGETS_IRON, new AspectList().add(Aspect.METAL, 4));

		register(Tags.Items.ORES_GOLD, new AspectList().add(Aspect.EARTH, 5).add(Aspect.METAL, 10).add(Aspect.DESIRE, 10));
		register(Tags.Items.RAW_MATERIALS_GOLD, new AspectList().add(Aspect.METAL, 10).add(Aspect.DESIRE, 10).add(Aspect.ENTROPY, 1));
		register(Tags.Items.INGOTS_GOLD, new AspectList().add(Aspect.METAL, 10).add(Aspect.DESIRE, 10));
		register(Tags.Items.NUGGETS_GOLD, new AspectList().add(Aspect.METAL, 3).add(Aspect.DESIRE, 3));

		register(Tags.Items.ORES_COPPER, new AspectList().add(Aspect.METAL, 10).add(Aspect.EARTH, 5).add(Aspect.EXCHANGE, 5));
		register(Tags.Items.RAW_MATERIALS_COPPER, new AspectList().add(Aspect.METAL, 10).add(Aspect.ENTROPY, 1).add(Aspect.EXCHANGE, 5));
		register(Tags.Items.INGOTS_COPPER, new AspectList().add(Aspect.METAL, 10).add(Aspect.EXCHANGE, 5));

		register(Tags.Items.INGOTS_NETHERITE, new AspectList().add(Aspect.METAL, 20).add(Aspect.FIRE, 10).add(Aspect.ENTROPY, 5));
		register(Items.NETHERITE_SCRAP, new AspectList().add(Aspect.METAL, 15).add(Aspect.FIRE, 10));

		register(Tags.Items.ORES_DIAMOND, new AspectList().add(Aspect.EARTH, 5).add(Aspect.DESIRE, 15).add(Aspect.CRYSTAL, 15));
		register(Tags.Items.GEMS_DIAMOND, new AspectList().add(Aspect.CRYSTAL, 15).add(Aspect.DESIRE, 15));
		register(Tags.Items.ORES_EMERALD, new AspectList().add(Aspect.EARTH, 5).add(Aspect.DESIRE, 10).add(Aspect.CRYSTAL, 15));
		register(Tags.Items.GEMS_EMERALD, new AspectList().add(Aspect.CRYSTAL, 15).add(Aspect.DESIRE, 10));
		register(Tags.Items.ORES_QUARTZ, new AspectList().add(Aspect.EARTH, 5).add(Aspect.CRYSTAL, 10));
		register(Tags.Items.GEMS_QUARTZ, new AspectList().add(Aspect.CRYSTAL, 5));
		register(Tags.Items.ORES_LAPIS, new AspectList().add(Aspect.EARTH, 5).add(Aspect.SENSES, 15));
		register(Tags.Items.GEMS_LAPIS, new AspectList().add(Aspect.SENSES, 15));
		register(Tags.Items.GEMS_AMETHYST, new AspectList().add(Aspect.CRYSTAL, 10).add(Aspect.MAGIC, 5));

		register(Tags.Items.ORES_REDSTONE, new AspectList().add(Aspect.EARTH, 5).add(Aspect.ENERGY, 15));
		register(Tags.Items.DUSTS_REDSTONE, new AspectList().add(Aspect.ENERGY, 10));
		register(Tags.Items.DUSTS_GLOWSTONE, new AspectList().add(Aspect.SENSES, 5).add(Aspect.LIGHT, 10));
		register(Items.GLOWSTONE, new AspectList().add(Aspect.SENSES, 5).add(Aspect.LIGHT, 20));

		register(Tags.Items.RODS_BLAZE, new AspectList().add(Aspect.FIRE, 10).add(Aspect.MAGIC, 5));
		register(Tags.Items.RODS_WOODEN, new AspectList().add(Aspect.PLANT, 4));

		register(Tags.Items.STRINGS, new AspectList().add(Aspect.BEAST, 4));
		register(Tags.Items.BONES, new AspectList().add(Aspect.UNDEAD, 4).add(Aspect.BEAST, 2));
		register(Items.BONE_MEAL, new AspectList().add(Aspect.PLANT, 4));
		register(Tags.Items.GUNPOWDERS, new AspectList().add(Aspect.ENTROPY, 8).add(Aspect.FIRE, 4));
		register(Tags.Items.SLIME_BALLS, new AspectList().add(Aspect.WATER, 6).add(Aspect.LIFE, 4));
		register(Tags.Items.ENDER_PEARLS, new AspectList().add(Aspect.VOID, 10).add(Aspect.MAGIC, 5));
		register(Tags.Items.FEATHERS, new AspectList().add(Aspect.FLIGHT, 4).add(Aspect.BEAST, 2));
		register(Tags.Items.EGGS, new AspectList().add(Aspect.LIFE, 6).add(Aspect.BEAST, 2));
		register(Tags.Items.LEATHERS, new AspectList().add(Aspect.BEAST, 4).add(Aspect.EARTH, 2));

		register(thaumcraft.api.blocks.BlocksTC.cinderpearl.get().asItem(), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AURA, 5).add(Aspect.FIRE, 10));

		register(thaumcraft.api.items.ItemsTC.primordialPearl.get(), new AspectList().add(Aspect.AIR, 10).add(Aspect.FIRE, 10)
				.add(Aspect.WATER, 10).add(Aspect.EARTH, 10).add(Aspect.ORDER, 10).add(Aspect.ENTROPY, 10));

		register(Items.OBSIDIAN, new AspectList().add(Aspect.EARTH, 10).add(Aspect.ENTROPY, 10).add(Aspect.FIRE, 5));
		register(Items.NETHER_STAR, new AspectList().add(Aspect.MAGIC, 25).add(Aspect.UNDEAD, 15).add(Aspect.DEATH, 10));
		register(Items.ENCHANTED_BOOK, new AspectList().add(Aspect.MAGIC, 10).add(Aspect.MIND, 5));
		register(Items.BOOK, new AspectList().add(Aspect.MIND, 5));
	}
}

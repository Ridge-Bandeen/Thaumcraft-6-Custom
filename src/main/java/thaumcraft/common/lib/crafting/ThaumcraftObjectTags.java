package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.crafting.ArcaneRecipe;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;

/**
 * Replaces the original (1.12.2) {@code CommonInternals.objectTags} database and the {@code ThaumcraftCraftingManager}
 * half that reads and derives it - the global "what aspects does this arbitrary item have" lookup behind
 * {@link AspectHelper#getObjectAspects}/{@code generateTags}: the Crucible's dissolving, the Smelter/Alembic, the
 * Thaumometer's scans, the Pech, the theorycraft cards, the Potion Sprayer.
 *
 * <p><b>Rebuilt 2026-09-25.</b> It had been a ~80-entry "useful starting set" with invented values and no recipe
 * tracing, so almost nothing except Crystal Essence could be dissolved in a crucible (a playtest finding). It now
 * follows the original's design:
 * <ol>
 * <li><b>The explicit table</b> ({@link ThaumcraftAspectTable}, the original's whole {@code ConfigAspects} item list -
 * about 330 entries): {@link #item}/{@link #tag} give a fixed value; {@link #complexItem}/{@link #complexTag} add to
 * whatever the recipes would give ({@code registerComplexObjectTag}).</li>
 * <li><b>Recipe tracing</b> for everything else ({@code generateTags}): a Crucible recipe's own aspects (square-rooted)
 * plus its catalyst, else an Infusion recipe likewise, else the cheapest crafting/arcane recipe - three quarters of
 * the ingredients' aspects, divided by the output count, less what the recipe hands back (buckets etc.), plus a Magic
 * cost for arcane recipes. Recursive over the ingredients with the original's cycle guard and depth limit.</li>
 * <li><b>Bonuses</b> for what the item is (armor, weapons, tools, dyes, enchantments, potions), then a 500 cap.</li>
 * </ol>
 *
 * <p>Differences forced by 1.21: there are no ore-dictionary names (item tags stand in) and no metadata (the variants
 * the original told apart by damage value are separate items); explicit values that build on another item's
 * ({@code new AspectList(new ItemStack(...))}) are evaluated lazily, because recipes only exist once a world is loaded;
 * and every derived answer is cached against the current {@link RecipeManager} (dropped whenever recipes reload).
 * Tag-keyed entries are matched in registration order; an item's own entry always wins over a tag's.
 */
public final class ThaumcraftObjectTags {

	private static final Map<Item, Supplier<AspectList>> ITEMS = new LinkedHashMap<>();
	private static final Map<TagKey<Item>, Supplier<AspectList>> TAGS = new LinkedHashMap<>();
	private static final Map<Item, Supplier<AspectList>> COMPLEX_ITEMS = new LinkedHashMap<>();
	private static final Map<TagKey<Item>, Supplier<AspectList>> COMPLEX_TAGS = new LinkedHashMap<>();

	/** The answer for an item before bonuses, per item; an empty list means "nothing known". */
	private static final Map<Item, AspectList> RESOLVED = new ConcurrentHashMap<>();
	private static final AspectList NOTHING = new AspectList();
	private static RecipeManager cachedFor;
	private static volatile Supplier<Level> clientLevel = () -> null;

	private static final ThreadLocal<List<Item>> HISTORY = ThreadLocal.withInitial(ArrayList::new);
	private static final ThreadLocal<int[]> CUTS = ThreadLocal.withInitial(() -> new int[1]);

	private ThaumcraftObjectTags() {
	}

	// ---- registration -------------------------------------------------------------------------------------

	public static void item(Item item, AspectList aspects) {
		ITEMS.put(item, () -> aspects);
	}

	public static void item(Item item, Supplier<AspectList> aspects) {
		ITEMS.put(item, aspects);
	}

	public static void tag(TagKey<Item> tag, AspectList aspects) {
		TAGS.put(tag, () -> aspects);
	}

	public static void tag(TagKey<Item> tag, Supplier<AspectList> aspects) {
		TAGS.put(tag, aspects);
	}

	public static void complexItem(Item item, AspectList aspects) {
		COMPLEX_ITEMS.put(item, () -> aspects);
	}

	public static void complexItem(Item item, Supplier<AspectList> aspects) {
		COMPLEX_ITEMS.put(item, aspects);
	}

	public static void complexTag(TagKey<Item> tag, AspectList aspects) {
		COMPLEX_TAGS.put(tag, () -> aspects);
	}

	/** An item's whole answer as a list to build on ({@code new AspectList(new ItemStack(item))}), never null. */
	public static AspectList of(Item item) {
		AspectList found = getObjectTags(new ItemStack(item));
		return found == null ? new AspectList() : found;
	}

	/** Lets the client side supply its level for recipe lookups when no integrated server is running. */
	public static void setClientLevelSupplier(Supplier<Level> supplier) {
		clientLevel = supplier;
	}

	/** Forget everything derived from recipes (they were reloaded or re-synced). */
	public static void clearDerived() {
		RESOLVED.clear();
		cachedFor = null;
	}

	// ---- lookup --------------------------------------------------------------------------------------------

	@Nullable
	private static Level currentLevel() {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		Level level = server != null ? server.overworld() : null;
		return level != null ? level : clientLevel.get();
	}

	/** The real implementation behind {@link AspectHelper#getObjectAspects}/{@code generateTags}. */
	public static AspectList getObjectTags(ItemStack stack) {
		if (stack.isEmpty()) return null;
		Level level = currentLevel();
		if (level != null && cachedFor != level.getRecipeManager()) {
			RESOLVED.clear();
			cachedFor = level.getRecipeManager();
		}

		AspectList base;
		AspectList potion = potionTags(level, stack);
		if (potion != null) {
			base = potion;
		} else {
			AspectList found = baseTags(stack.getItem());
			base = found == null ? null : found.copy();
		}
		return capAspects(getBonusTags(stack, base), 500);
	}

	private static AspectList capAspects(AspectList source, int amount) {
		if (source == null) return null;
		AspectList out = new AspectList();
		for (Aspect aspect : source.getAspects()) {
			if (aspect != null) out.merge(aspect, Math.min(amount, source.getAmount(aspect)));
		}
		return out;
	}

	/** Potions, splash and lingering potions and tipped arrows: the potion's own cost plus a fixed flavour per kind. */
	@Nullable
	private static AspectList potionTags(@Nullable Level level, ItemStack stack) {
		if (level == null || !stack.has(DataComponents.POTION_CONTENTS)) return null;
		Item item = stack.getItem();
		Aspect extra;
		int amount = 5;
		if (item == Items.POTION) extra = Aspect.WATER;
		else if (item == Items.TIPPED_ARROW) extra = Aspect.AVERSION;
		else if (item == Items.SPLASH_POTION) extra = Aspect.ENERGY;
		else if (item == Items.LINGERING_POTION) extra = Aspect.TRAP;
		else return null;
		PotionBrewing brewing = level.potionBrewing();
		AspectList list = PotionAspects.of(brewing, stack);
		return list.add(extra, amount);
	}

	/** An item's aspects before bonuses: its explicit entry, or the recipes' answer plus any complex additions. */
	@Nullable
	private static AspectList baseTags(Item item) {
		AspectList cached = RESOLVED.get(item);
		if (cached != null) return cached == NOTHING ? null : cached;

		List<Item> history = HISTORY.get();
		if (history.contains(item) || history.size() >= 100) {
			CUTS.get()[0]++;
			return null;
		}
		history.add(item);
		int cutsBefore = CUTS.get()[0];
		try {
			AspectList result = explicit(item);
			if (result == null) {
				AspectList additions = complexAdditions(item);
				AspectList derived = derive(item);
				if (additions != null) {
					AspectList sum = derived == null ? new AspectList() : derived.copy();
					for (Aspect aspect : additions.getAspects()) {
						sum.add(aspect, additions.getAmount(aspect));
					}
					result = sum;
				} else {
					result = derived;
				}
			}
			result = result == null ? null : capAspects(result, 500);
			if (CUTS.get()[0] == cutsBefore) {
				RESOLVED.put(item, result == null || result.size() == 0 ? NOTHING : result);
			}
			return result;
		} finally {
			history.remove(history.size() - 1);
		}
	}

	@Nullable
	private static AspectList explicit(Item item) {
		Supplier<AspectList> direct = ITEMS.get(item);
		if (direct != null) return direct.get();
		Holder<Item> holder = item.builtInRegistryHolder();
		for (Map.Entry<TagKey<Item>, Supplier<AspectList>> entry : TAGS.entrySet()) {
			if (holder.is(entry.getKey())) return entry.getValue().get();
		}
		return null;
	}

	@Nullable
	private static AspectList complexAdditions(Item item) {
		Supplier<AspectList> direct = COMPLEX_ITEMS.get(item);
		if (direct != null) return direct.get();
		Holder<Item> holder = item.builtInRegistryHolder();
		for (Map.Entry<TagKey<Item>, Supplier<AspectList>> entry : COMPLEX_TAGS.entrySet()) {
			if (holder.is(entry.getKey())) return entry.getValue().get();
		}
		return null;
	}

	// ---- recipe tracing (the original's generateTags) --------------------------------------------------------

	@Nullable
	private static AspectList derive(Item item) {
		Level level = currentLevel();
		if (level == null) return null;
		RecipeManager recipes = level.getRecipeManager();
		RegistryAccess access = level.registryAccess();

		AspectList ret = fromCrucible(recipes, access, item);
		if (ret != null) return ret;
		ret = fromInfusion(recipes, access, item);
		if (ret != null) return ret;
		return fromCrafting(recipes, access, item);
	}

	@Nullable
	private static AspectList fromCrucible(RecipeManager recipes, RegistryAccess access, Item item) {
		for (RecipeHolder<CrucibleRecipe> holder : recipes.getAllRecipesFor(ThaumcraftRecipeTypes.CRUCIBLE_TYPE.get())) {
			CrucibleRecipe recipe = holder.value();
			ItemStack out = recipe.getRecipeOutput();
			if (out.isEmpty() || out.getItem() != item) continue;
			ItemStack[] catalysts = recipe.getCatalyst().getItems();
			if (catalysts.length == 0 || catalysts[0].isEmpty()) return null;
			AspectList catalystTags = getObjectTags(catalysts[0]);
			AspectList result = new AspectList();
			if (catalystTags != null) {
				for (Aspect aspect : catalystTags.getAspects()) {
					result.add(aspect, catalystTags.getAmount(aspect));
				}
			}
			AspectList cost = recipe.getAspects();
			for (Aspect aspect : cost.getAspects()) {
				result.add(aspect, (int) (Math.sqrt(cost.getAmount(aspect)) / out.getCount()));
			}
			return dropNonPositive(result);
		}
		return null;
	}

	@Nullable
	private static AspectList fromInfusion(RecipeManager recipes, RegistryAccess access, Item item) {
		for (RecipeHolder<InfusionRecipe> holder : recipes.getAllRecipesFor(ThaumcraftRecipeTypes.INFUSION_TYPE.get())) {
			InfusionRecipe recipe = holder.value();
			ItemStack out = recipe.getRecipeOutput();
			if (out.isEmpty() || out.getItem() != item) continue;
			List<Ingredient> ingredients = new ArrayList<>();
			ingredients.add(recipe.getRecipeInput());
			ingredients.addAll(recipe.getComponents());
			AspectList result = new AspectList();
			AspectList fromIngredients = aspectsFromIngredients(ingredients, out, List.of());
			for (Aspect aspect : fromIngredients.getAspects()) {
				result.add(aspect, fromIngredients.getAmount(aspect));
			}
			AspectList cost = recipe.getAspects();
			for (Aspect aspect : cost.getAspects()) {
				result.add(aspect, (int) (Math.sqrt(cost.getAmount(aspect)) / out.getCount()));
			}
			return dropNonPositive(result);
		}
		return null;
	}

	@Nullable
	private static AspectList fromCrafting(RecipeManager recipes, RegistryAccess access, Item item) {
		AspectList best = null;
		int bestSize = Integer.MAX_VALUE;
		List<RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>>> candidates = new ArrayList<>();
		for (RecipeHolder<CraftingRecipe> holder : recipes.getAllRecipesFor(RecipeType.CRAFTING)) candidates.add(holder);
		for (RecipeHolder<ArcaneRecipe> holder : recipes.getAllRecipesFor(ThaumcraftRecipeTypes.ARCANE_TYPE.get())) candidates.add(holder);

		for (RecipeHolder<? extends net.minecraft.world.item.crafting.Recipe<?>> holder : candidates) {
			net.minecraft.world.item.crafting.Recipe<?> recipe = holder.value();
			ItemStack out;
			try {
				out = recipe.getResultItem(access);
			} catch (Exception e) {
				continue;
			}
			if (out == null || out.isEmpty() || out.getItem() != item) continue;
			try {
				List<ItemStack> remaining = new ArrayList<>();
				if (recipe instanceof CraftingRecipe crafting) {
					List<ItemStack> firsts = new ArrayList<>();
					for (Ingredient ingredient : crafting.getIngredients()) {
						ItemStack[] stacks = ingredient.getItems();
						firsts.add(stacks.length > 0 ? stacks[0] : ItemStack.EMPTY);
					}
					if (firsts.size() <= 9) {
						while (firsts.size() < 9) firsts.add(ItemStack.EMPTY);
						for (ItemStack rest : crafting.getRemainingItems(CraftingInput.of(3, 3, firsts))) {
							if (!rest.isEmpty()) remaining.add(rest);
						}
					}
				}
				AspectList ph = aspectsFromIngredients(recipe.getIngredients(), out, remaining);
				if (recipe instanceof ArcaneRecipe arcane && arcane.getVis() > 0) {
					ph.add(Aspect.MAGIC, (int) (Math.sqrt(1 + arcane.getVis() / 2) / (float) out.getCount()));
				}
				ph = dropNonPositive(ph);
				if (ph.visSize() >= bestSize || ph.visSize() <= 0) continue;
				best = ph;
				bestSize = ph.visSize();
			} catch (Exception e) {
				// a recipe that can't be walked simply doesn't contribute
			}
		}
		return best;
	}

	private static AspectList aspectsFromIngredients(List<Ingredient> ingredients, ItemStack recipeOut, List<ItemStack> remaining) {
		AspectList mid = new AspectList();
		for (Ingredient ingredient : ingredients) {
			ItemStack[] stacks = ingredient.getItems();
			if (stacks.length == 0 || stacks[0].isEmpty()) continue;
			AspectList tags = getObjectTags(stacks[0]);
			if (tags == null) continue;
			for (Aspect aspect : tags.getAspects()) {
				if (aspect != null) mid.add(aspect, tags.getAmount(aspect));
			}
		}
		for (ItemStack rest : remaining) {
			AspectList tags = getObjectTags(rest);
			if (tags == null) continue;
			for (Aspect aspect : tags.getAspects()) {
				mid.reduce(aspect, tags.getAmount(aspect));
			}
		}
		AspectList out = new AspectList();
		for (Aspect aspect : mid.getAspects()) {
			if (aspect == null) continue;
			float v = mid.getAmount(aspect) * 0.75f / recipeOut.getCount();
			if (v < 1.0f && v > 0.75f) v = 1.0f;
			out.add(aspect, (int) v);
		}
		return dropNonPositive(out);
	}

	private static AspectList dropNonPositive(AspectList list) {
		AspectList out = new AspectList();
		for (Aspect aspect : list.getAspects()) {
			if (aspect != null && list.getAmount(aspect) > 0) out.add(aspect, list.getAmount(aspect));
		}
		return out;
	}

	// ---- bonuses -------------------------------------------------------------------------------------------

	/**
	 * Adds derived bonuses on top of an item's base tag (armor/weapon/tool type, dye color, enchantments) - or, for an
	 * {@link IEssentiaContainerItem} that isn't ignoring its own contents, replaces the base tag outright with its actual
	 * held aspects (matches the original: a filled Warded Jar/Crystal Essence reports what it's holding).
	 */
	private static AspectList getBonusTags(ItemStack stack, @Nullable AspectList sourceTags) {
		AspectList tmp = new AspectList();
		if (stack.isEmpty()) return tmp;

		Item item = stack.getItem();
		if (item instanceof IEssentiaContainerItem container && !container.ignoreContainedAspects()) {
			sourceTags = null;
			AspectList held = container.getAspects(stack);
			if (held != null) {
				for (Aspect tag : held.getAspects()) {
					if (tag != null && held.getAmount(tag) > 0) tmp.add(tag, held.getAmount(tag));
				}
			}
		}
		if (sourceTags != null) {
			for (Aspect tag : sourceTags.getAspects()) {
				if (tag != null) tmp.add(tag, sourceTags.getAmount(tag));
			}
		}

		if (item instanceof ArmorItem armor) {
			tmp.merge(Aspect.PROTECT, armor.getDefense() * 4);
		} else if (item instanceof SwordItem sword && sword.getTier().getAttackDamageBonus() + 1.0f > 0.0f) {
			tmp.merge(Aspect.AVERSION, (int) (sword.getTier().getAttackDamageBonus() + 1.0f) * 4);
		} else if (item instanceof BowItem) {
			tmp.merge(Aspect.AVERSION, 10).merge(Aspect.FLIGHT, 5);
		} else if (item instanceof DiggerItem digger && !(item instanceof HoeItem)) {
			tmp.merge(Aspect.TOOL, (harvestLevel(digger.getTier()) + 1) * 4);
		} else if (item instanceof ShearsItem || item instanceof HoeItem) {
			int max = new ItemStack(item).getMaxDamage();
			if (max <= 59) tmp.merge(Aspect.TOOL, 4);
			else if (max <= 131) tmp.merge(Aspect.TOOL, 8);
			else if (max <= 250) tmp.merge(Aspect.TOOL, 12);
			else tmp.merge(Aspect.TOOL, 16);
		}

		if (stack.is(Tags.Items.DYES)) {
			tmp.merge(Aspect.SENSES, 5);
		}

		int magicScore = enchantmentAspects(stack, tmp);
		if (magicScore > 0) tmp.merge(Aspect.MAGIC, magicScore);

		return AspectHelper.cullTags(tmp);
	}

	/** The pre-1.13 harvest level (wood/gold 0, stone 1, iron 2, diamond 3, netherite 4) from a tier's durability. */
	private static int harvestLevel(Tier tier) {
		int uses = tier.getUses();
		if (uses <= 59 || uses == 32) return 0;
		if (uses <= 131) return 1;
		if (uses <= 250) return 2;
		if (uses <= 1561) return 3;
		return 4;
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
			// the original added 2 / 4 / 6 Magic for uncommon / rare / very rare enchantments; 1.21 has weights (10/5/2/1)
			int weight = ench.value().getWeight();
			total += weight >= 10 ? 0 : weight >= 5 ? 2 : weight >= 2 ? 4 : 6;
			total += entry.getIntValue() * 3;
		}
		return total;
	}

	/** Registers the whole explicit table - the original's {@code ConfigAspects#registerItemAspects}. */
	public static void init() {
		ITEMS.clear();
		TAGS.clear();
		COMPLEX_ITEMS.clear();
		COMPLEX_TAGS.clear();
		RESOLVED.clear();
		ThaumcraftAspectTable.register();
	}
}

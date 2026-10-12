package thaumcraft.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.FocusPowerModifier;
import thaumcraft.api.internal.DummyInternalMethodHandler;
import thaumcraft.api.internal.IInternalMethodHandler;

/**
 * Replaces the old (1.12.2) {@code ThaumcraftApi} - trimmed drastically from the original (555 lines).
 * This port keeps only what's actually still needed: the {@link #internalMethods} field
 * {@link IInternalMethodHandler} implementations register into (see
 * {@link thaumcraft.common.ThaumcraftInternalMethodHandler} for this port's real one), which is what
 * finally lets {@code AuraHelper} (and, eventually, addon/legacy code depending on this API) actually work
 * instead of being a permanent stub.
 *
 * Most of the rest of the original - {@code registerResearchLocation}, the whole family of
 * {@code addArcaneCraftingRecipe}/{@code addCrucibleRecipe}/{@code addInfusionCraftingRecipe}/
 * {@code addSmeltingBonus} static registration methods, scannable-thing registration, etc. - is
 * superseded entirely by this port's modern data-driven systems (JSON recipes under
 * {@code data/thaumcraft/recipe}, {@code DeferredRegister}-based item/block/entity registries) and isn't
 * called by anything this port's already-ported code needs. Not worth reviving a static registration API
 * nothing in this codebase would ever call. Revisit only if a genuine addon-compatibility need comes up.
 *
 * {@link #addWarpToItem}/{@link #getWarp} are the real exception, and a good illustration of why that last
 * sentence is worth taking seriously: they look like more superseded static registration, but the value
 * they carry is read at <i>craft time</i>, not load time, so no data-driven system replaces them. They back
 * the real warp-on-craft penalty - see {@link thaumcraft.common.lib.events.WarpEvents}.
 */
public class ThaumcraftApi {

	/**
	 * Calling methods from this only works properly once Thaumcraft's real handler has replaced this
	 * default - see {@link thaumcraft.common.ThaumcraftInternalMethodHandler}.
	 */
	public static IInternalMethodHandler internalMethods = new DummyInternalMethodHandler();

	/**
	 * Registers a real permanent-warp cost for crafting this item, exactly as the original's own
	 * {@code addWarpToItem} did - the one piece of the original's static registration API that turned out to
	 * still be load-bearing rather than superseded by this port's data-driven systems, because the value it
	 * registers is read at craft time rather than at load time.
	 *
	 * <p>Two real, deliberate differences from the original. First, the key: the original keyed its map by
	 * {@code Arrays.asList(item, itemDamage)} because 1.12.2 items carried metadata variants. Modern items
	 * don't - this port split every variant into its own real item - so the key is just the {@link Item}.
	 * Second, where the map lives: the original kept it in {@code CommonInternals}, which is still an
	 * unported 1.12.2 file (its {@code warpMap} field is dead here, never read or written). Reviving that
	 * whole file for one field would drag in {@code AspectList}, {@code IThaumcraftRecipe},
	 * {@code SmeltBonus} and the rest of its unported baggage, so the map lives here beside its only two
	 * real accessors instead.
	 */
	/**
	 * Adds possible loot to loot bags (and the urns and crates found in the world). As a reference, the weight of a gold
	 * nugget is 2500 in a common bag and a diamond's is 50 in the better bags.
	 * @param bagTypes which bags to add it to (several may be given): 0 = common, 1 = uncommon, 2 = rare; none means common
	 */
	public static void addLootBagItem(ItemStack item, int weight, int... bagTypes) {
		if (bagTypes == null || bagTypes.length == 0) {
			thaumcraft.api.internal.WeightedRandomLoot.lootBagCommon.add(new thaumcraft.api.internal.WeightedRandomLoot(item, weight));
			return;
		}
		for (int rarity : bagTypes) {
			switch (rarity) {
				case 0 -> thaumcraft.api.internal.WeightedRandomLoot.lootBagCommon.add(new thaumcraft.api.internal.WeightedRandomLoot(item, weight));
				case 1 -> thaumcraft.api.internal.WeightedRandomLoot.lootBagUncommon.add(new thaumcraft.api.internal.WeightedRandomLoot(item, weight));
				case 2 -> thaumcraft.api.internal.WeightedRandomLoot.lootBagRare.add(new thaumcraft.api.internal.WeightedRandomLoot(item, weight));
				default -> {
				}
			}
		}
	}

	/**
	 * Replaces the gear a loot bag, urn or crate can roll for one slot (slot 0 a weapon, 1-4 boots, leggings, chestplate,
	 * helmet). {@code byQuality} runs from worst to best; the built-in ladders have seven steps (leather or an iron axe up
	 * to void metal), and a roll above the end of a shorter ladder simply rolls again. Register from
	 * {@code ThaumcraftSetupEvent}; without a call the built-in ladder is used.
	 */
	public static void setLootBagGear(int slot, net.minecraft.world.item.Item... byQuality) {
		if (slot < 0 || slot >= thaumcraft.api.internal.WeightedRandomLoot.lootBagGear.length) {
			throw new IllegalArgumentException("loot bag gear slot must be 0-4, got " + slot);
		}
		thaumcraft.api.internal.WeightedRandomLoot.lootBagGear[slot] = byQuality == null ? null : byQuality.clone();
	}

	/**
	 * Adds to an infusion's stability regeneration wherever an Infusion Matrix stands: each modifier's answer for the
	 * matrix's level and position is summed into the stability it regains per cycle (the symmetry bonus), whenever the
	 * matrix rescans its surroundings. Positive steadies infusions there, negative unsettles them. Main thread only.
	 */
	public static void addInfusionStabilityModifier(BiFunction<Level, BlockPos, Float> modifier) {
		STABILITY_MODIFIERS.add(modifier);
	}

	/** The sum of the registered infusion stability modifiers at a matrix (0 without any). */
	public static float getInfusionStabilityModifier(Level level, BlockPos matrix) {
		float total = 0;
		for (BiFunction<Level, BlockPos, Float> modifier : STABILITY_MODIFIERS) {
			Float f = modifier.apply(level, matrix);
			if (f != null) total += f;
		}
		return total;
	}

	/**
	 * Changes the aspects an item stack has, by its data components or anything else about the stack: each modifier gets
	 * the stack and its aspects so far (a copy it may change and return, empty for a stack with none) and returns the
	 * aspects to use. Everything that reads item aspects sees the result: the Crucible, the Alchemical Furnace, scanning,
	 * tooltips, the Research Table. Applied after the item's own aspects are looked up, so it may run often; only read
	 * the stack.
	 */
	public static void addAspectModifier(BiFunction<ItemStack, AspectList, AspectList> modifier) {
		ASPECT_MODIFIERS.add(modifier);
	}

	/** Runs the registered aspect modifiers over a stack's aspects; null if nothing is left. */
	public static AspectList applyAspectModifiers(ItemStack stack, AspectList aspects) {
		if (ASPECT_MODIFIERS.isEmpty()) return aspects;
		AspectList out = aspects == null ? new AspectList() : aspects.copy();
		for (BiFunction<ItemStack, AspectList, AspectList> modifier : ASPECT_MODIFIERS) {
			AspectList next = modifier.apply(stack, out);
			out = next == null ? new AspectList() : next;
		}
		return out.size() == 0 ? null : out;
	}

	/** Changes the power of a focus's effects by the focus stack; see {@link FocusPowerModifier}. */
	public static void addFocusPowerModifier(FocusPowerModifier modifier) {
		FOCUS_POWER_MODIFIERS.add(modifier);
	}

	/** Sets each effect of a package about to be cast from this focus to the registered modifiers' power. */
	public static void applyFocusPowerModifiers(ItemStack focus, FocusPackage focusPackage) {
		if (FOCUS_POWER_MODIFIERS.isEmpty()) return;
		for (FocusEffect effect : focusPackage.getFocusEffects()) {
			float power = 1.0f;
			for (FocusPowerModifier modifier : FOCUS_POWER_MODIFIERS) {
				power = modifier.modify(focus, effect, power);
			}
			effect.setPowerScale(power);
		}
	}

	private static final List<BiFunction<Level, BlockPos, Float>> STABILITY_MODIFIERS = new CopyOnWriteArrayList<>();
	private static final List<BiFunction<ItemStack, AspectList, AspectList>> ASPECT_MODIFIERS = new CopyOnWriteArrayList<>();
	private static final List<FocusPowerModifier> FOCUS_POWER_MODIFIERS = new CopyOnWriteArrayList<>();

	public static void addWarpToItem(ItemStack craftresult, int amount) {
		if (craftresult == null || craftresult.isEmpty()) return;
		WARP_MAP.put(craftresult.getItem(), amount);
	}

	/** The real permanent warp crafting this stack costs, or 0 if it costs none. */
	public static int getWarp(ItemStack in) {
		if (in == null || in.isEmpty()) return 0;
		return WARP_MAP.getOrDefault(in.getItem(), 0);
	}

	private static final Map<Item, Integer> WARP_MAP = new HashMap<>();
}

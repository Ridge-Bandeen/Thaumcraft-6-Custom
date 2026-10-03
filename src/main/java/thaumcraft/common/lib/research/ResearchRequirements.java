package thaumcraft.common.lib.research;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchStage;

/**
 * The real "have I crafted / do I have this?" half of the true original's own research-stage gating - the
 * system {@code ThaumcraftResearchLoader}'s own doc comment named as the reason it deliberately skipped
 * parsing {@code required_craft}/{@code required_item} at all ("display-only without the crafting/
 * possession-triggered auto-progression hook, itself a real, separate, not-yet-ported system"). This is
 * that system.
 *
 * <p>Real mechanic, matching the original: every {@code required_craft} entry across all loaded research
 * registers a real reference here at load time; crafting a matching item grants a real virtual research key
 * ({@code "[#]" + code}, the original's own key shape) via {@link thaumcraft.common.lib.events.ResearchCraftingEvents}.
 * {@code required_item} is checked live against the player's own real inventory instead - nothing needs
 * recording for that, exactly as in the original.
 *
 * <p>Real, deliberate modernisation of the reference code: the original hashed {@code stack.toString()} (a
 * 1.12.2 string form, meaningless here, and unstable across versions). This hashes the target's own real
 * registry id instead - stable across runs and readable in a debugger - while keeping the original's own
 * {@code int[] craftReference}/{@code "[#]"} key shape so the API and the saved-knowledge format both stay
 * the same shape the original used.
 *
 * <p><b>Restored 2026-09-27: this now really gates stage advancement</b>, at the user's own explicit request
 * ("match the user experience perfectly with the original") once a scan for the {@code enchanted_placeholder}
 * gap turned up that {@link ThaumonomiconMenu#startResearch} had never actually consulted this class at all -
 * {@code ThaumcraftInternalMethodHandler#completeResearch} silently advanced a multi-stage entry through
 * every one of its stages atomically on a single click, ignoring every stage's own {@code required_item}/
 * {@code required_craft}/{@code required_knowledge} gate for every research entry in the game, not only the
 * 3 stages that happen to use the enchantment-placeholder trick. See {@link ThaumonomiconMenu#startResearch}
 * for the real per-stage consumer this class now feeds, and that menu's own doc comment for what's still a
 * real, deliberate simplification (the original's own graphical per-requirement checklist is a genuine
 * separate client-rendering project; a plain status line stands in for it for now).
 *
 * <p><b>Real correction found while wiring this up</b>: an earlier pass here left a doc comment claiming the
 * gate was "any ONE of the listed items/crafts" - checked directly against the true original's own
 * {@code PacketSyncProgressToServer#checkRequisites} (never against an assumption) once this was actually
 * being turned on for real, and that is wrong: the original requires and consumes <i>every</i> listed
 * alternative, not just one. {@link #isStageSatisfied} below now matches that AND semantics exactly - kept
 * even where it reads as an odd design (e.g. {@code basics.json}'s early stage wants both scribing tools
 * <i>and</i> a research table crafted), same "preserve a confirmed original behaviour rather than the more
 * sensible-looking guess" precedent the arcane workbench's own vis-discount bug already established.
 *
 * <p>The shipped research JSON is still authored in 1.12.2 item-spec form ({@code thaumcraft:ingot;1;2}
 * metadata variants, old-format NBT, {@code oredict:} names, even one malformed double-namespace id), so a
 * meaningful fraction of requirements can't resolve to a real modern item at all - those are dropped, exactly
 * as {@link #resolveSpec}'s own doc comment already documented. Two of the four {@code enchanted_placeholder}
 * requirements this file now resolves (in {@code infusion.json}) carry that same malformed
 * {@code "minecraft:thaumcraft:enchanted_placeholder"} double-namespace id - a real typo already present in
 * Thaumcraft 6's own shipped data (confirmed against this repo's own {@code master} branch, not just this
 * port's converted copy), so those two stages are genuinely unsatisfiable in the true original too. Left
 * broken here on purpose, matching that same "faithfully preserve a confirmed original bug" precedent, not
 * "fixed" to what a corrected id would imply.
 */
public class ResearchRequirements {

	/** A real registered {@code required_craft} target: an {@link Item} or an {@link TagKey}, plus the real
	 * code its virtual {@code "[#]"} research key is built from. */
	private record Reference(Predicate<ItemStack> matches, int code) {
	}

	private static final List<Reference> CRAFT_REFERENCES = new ArrayList<>();

	/** The handful of real legacy ore-dictionary names the shipped research data actually uses, mapped to
	 * their real modern tag. Legacy names are singular and un-namespaced ({@code chest}); modern common tags
	 * are plural and namespaced ({@code c:chests}), so there's no reliable mechanical rule - only an
	 * explicit table is honest. Anything not listed falls back to {@code c:<name>}, which may simply match
	 * nothing (a real, silent no-op rather than a crash). */
	private static final Map<String, String> LEGACY_ORE_ALIASES = Map.of("chest", "chests");

	/**
	 * The real 1.12.2-spec → modern-id table, built by reading the true original's own registrations rather
	 * than guessing. Three real, distinct kinds of entry live here:
	 *
	 * <ul>
	 * <li><b>Metadata variants this port split into separate real items.</b> The subtype orders come straight
	 * from the original's own {@code ConfigItems} arrays - {@code ItemTCBase("ingot", {thaumium, void, brass})}
	 * makes {@code ingot;1;2} brass, {@code ItemTCBase("nugget", {iron, copper, tin, silver, lead, quicksilver,
	 * thaumium, void, brass, quartz, rareearth})} makes {@code nugget;1;10} rare earth, and
	 * {@code ItemTCBase("plate", {brass, iron, thaumium, void})} makes {@code plate;1;2}/{@code ;1;3} thaumium
	 * and void. Guessing any of these would have been wrong - the orders aren't alphabetical or tiered.</li>
	 * <li><b>Things this port deliberately renamed</b>, each already verified present under the new name:
	 * {@code smelter_basic} → {@code smelter}, {@code wand_workbench} → {@code focal_manipulator}.</li>
	 * <li><b>Vanilla's own 1.12.2 → modern renames</b>, including the two flattened {@code minecraft:dye}
	 * metadata cases ({@code dye} with no meta is meta 0, ink sac; {@code dye;1;15} is bone meal).</li>
	 * </ul>
	 *
	 * {@code thaumcraft:arcane_stone} → {@code thaumcraft:stone_arcane} is the one entry that is a real,
	 * deliberate <i>fix</i> rather than a port mapping: the original registers the block as {@code stone_arcane}
	 * and never as {@code arcane_stone}, so that requirement has always been silently dead in shipped
	 * Thaumcraft 6. The intent is unambiguous, so it's repaired here rather than faithfully left broken.
	 */
	private static final Map<String, String> LEGACY_ITEM_ALIASES = Map.ofEntries(
			Map.entry("thaumcraft:ingot;0", "thaumcraft:ingot_thaumium"),
			Map.entry("thaumcraft:ingot;2", "thaumcraft:ingot_brass"),
			Map.entry("thaumcraft:nugget;10", "thaumcraft:nugget_rareearth"),
			Map.entry("thaumcraft:plate;2", "thaumcraft:plate_thaumium"),
			Map.entry("thaumcraft:plate;3", "thaumcraft:plate_void"),
			// ItemTCBase("plate", {brass, iron, thaumium, void}), ("mind", {clockwork, biothaumic}),
			// ("module", {vision, aggression}); ItemTurretPlacer("turret", {basic, advanced, bore});
			// ItemAmuletVis("amulet_vis", {found, crafted}); ItemSealPlacer "seal" meta 0 is the blank seal.
			// A bare id with no metadata is meta 0, same as the original's own parseJSONtoItemStack.
			Map.entry("thaumcraft:plate", "thaumcraft:plate_brass"),
			Map.entry("thaumcraft:plate;0", "thaumcraft:plate_brass"),
			Map.entry("thaumcraft:plate;1", "thaumcraft:plate_iron"),
			Map.entry("thaumcraft:mind", "thaumcraft:mind_clockwork"),
			Map.entry("thaumcraft:mind;0", "thaumcraft:mind_clockwork"),
			Map.entry("thaumcraft:mind;1", "thaumcraft:mind_biothaumic"),
			Map.entry("thaumcraft:module", "thaumcraft:module_vision"),
			Map.entry("thaumcraft:module;0", "thaumcraft:module_vision"),
			Map.entry("thaumcraft:module;1", "thaumcraft:module_aggression"),
			Map.entry("thaumcraft:turret", "thaumcraft:turret_placer_basic"),
			Map.entry("thaumcraft:turret;0", "thaumcraft:turret_placer_basic"),
			Map.entry("thaumcraft:turret;1", "thaumcraft:turret_placer_advanced"),
			Map.entry("thaumcraft:turret;2", "thaumcraft:turret_placer_bore"),
			Map.entry("thaumcraft:amulet_vis", "thaumcraft:amulet_vis_stone"),
			Map.entry("thaumcraft:amulet_vis;0", "thaumcraft:amulet_vis_stone"),
			Map.entry("thaumcraft:amulet_vis;1", "thaumcraft:amulet_vis"),
			Map.entry("thaumcraft:seal", "thaumcraft:seal_blank"),
			// ItemCurio("curio", {arcane, preserved, ancient, eldritch, knowledge, twisted, rites})
			Map.entry("thaumcraft:curio;1", "thaumcraft:curio_preserved"),
			Map.entry("thaumcraft:curio;2", "thaumcraft:curio_ancient"),
			Map.entry("thaumcraft:curio;3", "thaumcraft:curio_eldritch"),
			Map.entry("thaumcraft:curio;4", "thaumcraft:curio_knowledge"),
			Map.entry("thaumcraft:curio;5", "thaumcraft:curio_twisted"),
			Map.entry("thaumcraft:curio;6", "thaumcraft:curio_rites"),
			Map.entry("minecraft:hardened_clay", "minecraft:terracotta"),
			Map.entry("thaumcraft:smelter_basic", "thaumcraft:smelter"),
			Map.entry("thaumcraft:wand_workbench", "thaumcraft:focal_manipulator"),
			Map.entry("thaumcraft:arcane_stone", "thaumcraft:stone_arcane"),
			Map.entry("minecraft:web", "minecraft:cobweb"),
			Map.entry("minecraft:noteblock", "minecraft:note_block"),
			Map.entry("minecraft:dye", "minecraft:ink_sac"),
			Map.entry("minecraft:dye;15", "minecraft:bone_meal"));

	/**
	 * The 1.12.2 numeric enchantment id → modern registry key table, for {@code enchanted_placeholder}'s own
	 * legacy {@code {ench:[{id:Ns,lvl:Ms}]}} NBT tail. Kept intentionally short - only the 4 ids the shipped
	 * research JSON actually uses (Silk Touch, Fortune, Sharpness, Protection) - rather than transcribing the
	 * full 1.12.2 table from memory: a wrong guess for an id nobody references would be strictly worse than
	 * leaving it out (a genuinely unresolvable spec is dropped safely, same as any other unresolvable spec).
	 */
	private static final Map<Integer, ResourceKey<Enchantment>> LEGACY_ENCHANT_IDS = Map.of(
			0, net.minecraft.world.item.enchantment.Enchantments.PROTECTION,
			16, net.minecraft.world.item.enchantment.Enchantments.SHARPNESS,
			33, net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH,
			35, net.minecraft.world.item.enchantment.Enchantments.FORTUNE);

	private static final Pattern ENCHANT_NBT_ENTRY = Pattern.compile("id:(\\d+)s?,\\s*lvl:(\\d+)s?");

	/** The original's own {@code ItemEnchantmentPlaceholder} matching (see {@code InventoryUtils#checkEnchantedPlaceholder}):
	 * not a real held item at all, but a stand-in for "any real item bearing every one of these vanilla enchantments,
	 * each at least at this level" - one real tool with Silk Touch satisfies a placeholder asking for Silk Touch 1,
	 * whatever the tool actually is. */
	public record EnchantSpec(Map<ResourceKey<Enchantment>, Integer> required) {
	}

	/** An aspect-tagged {@code required_item} ({@code crystal_essence;1;0;{Aspects:[{amount:1,key:'aer'}]}}, the
	 * vitium/aer/... phials): the original matched these with relaxed NBT, so an Aer crystal is wanted, not any
	 * crystal. Satisfied by a stack of that item holding at least each listed aspect amount. */
	public record AspectItemSpec(Item item, thaumcraft.api.aspects.AspectList aspects) {
	}

	private static final Pattern ASPECT_NBT_ENTRY = Pattern.compile("\\{amount:(\\d+),\\s*key:'([a-z]+)'\\}");

	/** Called by {@code ThaumcraftResearchLoader} at the start of every real datapack reload. */
	public static void clear() {
		CRAFT_REFERENCES.clear();
	}

	/**
	 * Resolves one real legacy research item spec into either an {@link Item} or a {@link TagKey}.
	 *
	 * <p>Metadata is honoured wherever the original's own subtype arrays give it a real meaning (see
	 * {@link #LEGACY_ITEM_ALIASES}). Of the legacy NBT blob, the two shapes the shipped data actually uses are
	 * honoured: the enchantment list on {@code enchanted_placeholder} ({@link EnchantSpec}) and the aspect list on
	 * {@code crystal_essence}/{@code phial} ({@link AspectItemSpec}, since 2026-09-27 - "an Aer crystal", not any
	 * crystal, as the original's relaxed-NBT match required).
	 *
	 * <p>Real finding worth recording, from reading the original's own registrations rather than assuming:
	 * a handful of the shipped specs are <b>already dead in the true original</b> - no {@code thaumcraft:metal},
	 * {@code thaumcraft:nitor} or {@code thaumcraft:leather} item was ever registered by Thaumcraft 6 (its
	 * metal blocks are {@code metal_brass}/{@code metal_thaumium}/…, its nitor is {@code nitor_<dye>}, and
	 * there is no tallow-adjacent "leather" item at all). Those specs resolve to nothing here for exactly the
	 * reason they resolved to nothing upstream, so skipping them is the faithful behaviour, not a porting gap.
	 * {@code enchanted_placeholder} (well-formed, i.e. the two entries in
	 * {@code auromancy.json}) now resolves to a real {@link EnchantSpec} instead - see that record's own doc
	 * comment; the two malformed double-namespace copies in {@code infusion.json} are left unresolved, matching
	 * a confirmed typo in Thaumcraft 6's own shipped data (see this class's own doc comment).
	 *
	 * @return an {@code Item}, a {@code TagKey<Item>}, an {@link EnchantSpec}, or {@code null} if this spec has
	 * no real modern form.
	 */
	public static Object resolveSpec(String spec) {
		if (spec == null || spec.isEmpty()) return null;
		String[] parts = spec.split(";");
		String head = parts[0];

		if (head.equals("thaumcraft:enchanted_placeholder") && parts.length > 3) {
			Map<ResourceKey<Enchantment>, Integer> required = new LinkedHashMap<>();
			Matcher m = ENCHANT_NBT_ENTRY.matcher(spec);
			while (m.find()) {
				ResourceKey<Enchantment> key = LEGACY_ENCHANT_IDS.get(Integer.parseInt(m.group(1)));
				if (key == null) return null; // an id this table doesn't know - drop the whole spec rather than half-match it
				required.merge(key, Integer.parseInt(m.group(2)), Math::max);
			}
			return required.isEmpty() ? null : new EnchantSpec(required);
		}

		if (head.startsWith("oredict:")) {
			String name = head.substring("oredict:".length());
			if (name.isEmpty()) return null;
			return TagKey.create(BuiltInRegistries.ITEM.key(),
					ResourceLocation.fromNamespaceAndPath("c", LEGACY_ORE_ALIASES.getOrDefault(name, name)));
		}

		// parts[1] is the legacy stack size and parts[2] the legacy metadata; anything past that is the
		// legacy NBT blob (see this method's own doc comment for why the NBT half can't be honoured).
		String resolved = null;
		if (parts.length > 2) resolved = LEGACY_ITEM_ALIASES.get(head + ";" + parts[2]);
		if (resolved == null) resolved = LEGACY_ITEM_ALIASES.get(head);

		ResourceLocation id = ResourceLocation.tryParse(resolved != null ? resolved : head);
		if (id == null) return null;
		Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		if (item != null && parts.length > 3 && spec.contains("Aspects:")) {
			thaumcraft.api.aspects.AspectList aspects = new thaumcraft.api.aspects.AspectList();
			Matcher m = ASPECT_NBT_ENTRY.matcher(spec);
			while (m.find()) {
				thaumcraft.api.aspects.Aspect aspect = thaumcraft.api.aspects.Aspect.getAspect(m.group(2));
				if (aspect != null) aspects.add(aspect, Integer.parseInt(m.group(1)));
			}
			if (aspects.size() > 0) return new AspectItemSpec(item, aspects);
		}
		return item;
	}

	/** One stack standing for a resolved requirement target, for the Thaumonomicon's requirement row: the item itself,
	 * the {@code index}-th... cycling member of a tag (the original's {@code InventoryUtils#cycleItemStack}, one step a
	 * second), the tagged crystal/phial, or an Enchanted Placeholder carrying the wanted enchantments. */
	public static ItemStack displayStack(Object target, int index, net.minecraft.core.HolderLookup.Provider registries) {
		if (target instanceof Item item) return new ItemStack(item);
		if (target instanceof TagKey<?> tag) {
			@SuppressWarnings("unchecked")
			List<Holder<Item>> items = new ArrayList<>();
			for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty((TagKey<Item>) tag)) items.add(h);
			if (items.isEmpty()) return ItemStack.EMPTY;
			int i = (int) ((System.currentTimeMillis() / 1000L + index) % items.size());
			return new ItemStack(items.get(i));
		}
		if (target instanceof AspectItemSpec spec) {
			ItemStack stack = new ItemStack(spec.item());
			if (spec.item() instanceof thaumcraft.api.aspects.IEssentiaContainerItem container) container.setAspects(stack, spec.aspects().copy());
			return stack;
		}
		if (target instanceof EnchantSpec spec) {
			ItemStack stack = new ItemStack(thaumcraft.api.items.ItemsTC.enchantedPlaceholder.get());
			if (registries != null) {
				var lookup = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
				for (Map.Entry<ResourceKey<Enchantment>, Integer> e : spec.required().entrySet()) {
					lookup.get(e.getKey()).ifPresent(h -> stack.enchant(h, e.getValue()));
				}
			}
			return stack;
		}
		return ItemStack.EMPTY;
	}

	/** The real code a resolved target's virtual {@code "[#]"} research key is built from - see this class's
	 * own doc comment for why it's the registry id's hash rather than the original's own stack hash. */
	public static int codeFor(Object target) {
		if (target instanceof Item item) return BuiltInRegistries.ITEM.getKey(item).toString().hashCode();
		if (target instanceof TagKey<?> tag) return ("tag:" + tag.location()).hashCode();
		return 0;
	}

	public static String researchKeyFor(int code) {
		return "[#]" + code;
	}

	/** Records a real resolved {@code required_craft} target so {@link #onItemCrafted} can recognise it.
	 * @return the real code for this target, for {@link ResearchStage#setCraftReference}. */
	@SuppressWarnings("unchecked")
	public static int registerCraftReference(Object target) {
		int code = codeFor(target);
		if (target instanceof Item item) {
			CRAFT_REFERENCES.add(new Reference(stack -> stack.is(item), code));
		} else if (target instanceof TagKey<?> tag) {
			CRAFT_REFERENCES.add(new Reference(stack -> stack.is((TagKey<Item>) tag), code));
		}
		return code;
	}

	/** Grants the real virtual research key for every registered reference the freshly-crafted stack really
	 * matches. One crafted item can satisfy several (its own id plus any tag it's in). */
	public static void onItemCrafted(ServerPlayer player, ItemStack crafted) {
		if (crafted.isEmpty() || CRAFT_REFERENCES.isEmpty()) return;
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		boolean granted = false;
		for (Reference reference : CRAFT_REFERENCES) {
			if (!reference.matches().test(crafted)) continue;
			String key = researchKeyFor(reference.code());
			if (knowledge.isResearchKnown(key)) continue;
			knowledge.addResearch(key);
			granted = true;
		}
		if (granted) knowledge.sync(player);
	}

	/**
	 * Whether a real research stage's own {@code required_craft}/{@code required_item} gates are really
	 * satisfied for this player right now - crafting gates via the virtual keys recorded above, possession
	 * gates by really looking in the player's own inventory. A stage with neither is trivially satisfied.
	 *
	 * <p>AND semantics for both, matching the true original exactly (see this class's own doc comment for the
	 * 2026-09-27 correction against an earlier, wrong "any one" assumption): every listed craft reference must
	 * already be known, and every listed item must be carried - not just one of each.
	 */
	public static boolean isStageSatisfied(Player player, ResearchStage stage) {
		if (stage == null) return true;
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);

		int[] craftReferences = stage.getCraftReference();
		if (craftReferences != null) {
			for (int code : craftReferences) {
				if (!knowledge.isResearchKnown(researchKeyFor(code))) return false;
			}
		}

		Object[] obtain = stage.getObtain();
		if (obtain != null) {
			for (Object target : obtain) {
				if (!isCarrying(player, target)) return false;
			}
		}

		// required_research and required_knowledge - the other two of the original's four stage gates, checked in
		// the same pass there (PacketSyncProgressToServer#checkRequisites). Every listed research key must be known
		// (strictly - a bare key needs completion, "KEY@n" only stage n), and every knowledge requirement met in
		// whole points (the same getKnowledge the original compared against).
		String[] research = stage.getResearch();
		if (research != null) {
			for (String key : research) {
				if (!ThaumcraftCapabilities.knowsResearchStrict(player, key)) return false;
			}
		}
		ResearchStage.Knowledge[] know = stage.getKnow();
		if (know != null) {
			for (ResearchStage.Knowledge k : know) {
				if (knowledge.getKnowledge(k.type, k.category) < k.amount) return false;
			}
		}
		return true;
	}

	/** Consumes a satisfied stage's own {@code required_item} entries and deducts its {@code required_knowledge}
	 * cost - the original's own {@code checkRequisites} consumption pass, split out so the caller can check
	 * {@link #isStageSatisfied} first and only spend real, real-inventory items once every gate has already
	 * passed. Only call this once {@link #isStageSatisfied} has already returned true for the same stage. */
	public static void consumeStage(ServerPlayer player, ResearchStage stage) {
		Object[] obtain = stage.getObtain();
		if (obtain != null) {
			for (Object target : obtain) {
				consumeOne(player, target);
			}
		}
		ResearchStage.Knowledge[] know = stage.getKnow();
		if (know != null) {
			for (ResearchStage.Knowledge k : know) {
				ThaumcraftCapabilities.getKnowledge(player).addKnowledge(k.type, k.category, -k.amount * k.type.getProgression());
			}
		}
	}

	/** Whether the player carries one of this resolved {@code required_item} target - the per-item tick of the book's
	 * requirement row, and the check {@link #isStageSatisfied} runs for every entry. */
	@SuppressWarnings("unchecked")
	public static boolean isCarrying(Player player, Object target) {
		if (target instanceof EnchantSpec spec) return findEnchanted(player, spec) != null;
		Predicate<ItemStack> matches;
		if (target instanceof Item item) matches = stack -> stack.is(item);
		else if (target instanceof TagKey<?> tag) matches = stack -> stack.is((TagKey<Item>) tag);
		else if (target instanceof AspectItemSpec spec) matches = stack -> matchesAspects(stack, spec);
		else return false;
		// maxCount 0 means "count only, don't remove" - the same real accessor ScanSky/ResearchTableMenu
		// already use for this port's own inventory checks.
		return player.getInventory().clearOrCountMatchingItems(matches, 0, player.getInventory()) > 0;
	}

	@SuppressWarnings("unchecked")
	private static void consumeOne(Player player, Object target) {
		if (target instanceof EnchantSpec spec) {
			ItemStack match = findEnchanted(player, spec);
			if (match != null) match.shrink(1);
			return;
		}
		Predicate<ItemStack> matches;
		if (target instanceof Item item) matches = stack -> stack.is(item);
		else if (target instanceof TagKey<?> tag) matches = stack -> stack.is((TagKey<Item>) tag);
		else if (target instanceof AspectItemSpec spec) matches = stack -> matchesAspects(stack, spec);
		else return;
		player.getInventory().clearOrCountMatchingItems(matches, 1, player.getInventory());
	}

	private static boolean matchesAspects(ItemStack stack, AspectItemSpec spec) {
		if (!stack.is(spec.item()) || !(stack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem container)) return false;
		thaumcraft.api.aspects.AspectList have = container.getAspects(stack);
		if (have == null) return false;
		for (thaumcraft.api.aspects.Aspect aspect : spec.aspects().getAspects()) {
			if (have.getAmount(aspect) < spec.aspects().getAmount(aspect)) return false;
		}
		return true;
	}

	/** The original's own {@code InventoryUtils#checkEnchantedPlaceholder}: the first main-inventory stack (hotbar
	 * included, armor/offhand excluded, matching the original's own {@code mainInventory} scan) carrying every one
	 * of the spec's enchantments at least at its required level. */
	private static ItemStack findEnchanted(Player player, EnchantSpec spec) {
		for (ItemStack stack : player.getInventory().items) {
			if (stack.isEmpty()) continue;
			ItemEnchantments enchantments = stack.getTagEnchantments();
			boolean all = true;
			for (Map.Entry<ResourceKey<Enchantment>, Integer> req : spec.required().entrySet()) {
				int level = 0;
				for (Map.Entry<Holder<Enchantment>, Integer> entry : enchantments.entrySet()) {
					if (entry.getKey().is(req.getKey())) {
						level = entry.getValue();
						break;
					}
				}
				if (level < req.getValue()) {
					all = false;
					break;
				}
			}
			if (all) return stack;
		}
		return null;
	}

	private ResearchRequirements() {
	}
}

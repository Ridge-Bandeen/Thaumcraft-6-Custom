package thaumcraft.common.lib.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * <p><b>Real, important scoping note: this deliberately does NOT gate stage advancement.</b> That's not an
 * oversight, it's blocked on a real architectural decision this port already shipped and built its
 * Thaumonomicon around: {@code ThaumcraftInternalMethodHandler#completeResearch} advances an entry through
 * every stage atomically in one click, and {@code ThaumonomiconScreen#buildReaderPages}' own doc comment
 * already explains that it concatenates every stage's text precisely because a partially-progressed entry
 * can never exist, and that it dropped the original's own per-stage requirements checklist as real dead
 * code under that model. Turning gating on means restoring that whole per-stage reader flow (checklist,
 * "complete stage" button, a client→server progress packet) - a real, separate arc. On top of that, the
 * shipped research JSON is still authored in 1.12.2 item-spec form ({@code thaumcraft:ingot;1;2} metadata
 * variants, old-format NBT, {@code oredict:} names, even one malformed double-namespace id), so a
 * meaningful fraction of requirements can't resolve to a real modern item at all - gating on those would
 * permanently soft-lock those entries. {@link #isStageSatisfied} is real and correct today; it's the
 * consumer that's deliberately deferred.
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
			Map.entry("thaumcraft:smelter_basic", "thaumcraft:smelter"),
			Map.entry("thaumcraft:wand_workbench", "thaumcraft:focal_manipulator"),
			Map.entry("thaumcraft:arcane_stone", "thaumcraft:stone_arcane"),
			Map.entry("minecraft:web", "minecraft:cobweb"),
			Map.entry("minecraft:noteblock", "minecraft:note_block"),
			Map.entry("minecraft:dye", "minecraft:ink_sac"),
			Map.entry("minecraft:dye;15", "minecraft:bone_meal"));

	/** Called by {@code ThaumcraftResearchLoader} at the start of every real datapack reload. */
	public static void clear() {
		CRAFT_REFERENCES.clear();
	}

	/**
	 * Resolves one real legacy research item spec into either an {@link Item} or a {@link TagKey}.
	 *
	 * <p>Metadata is honoured wherever the original's own subtype arrays give it a real meaning (see
	 * {@link #LEGACY_ITEM_ALIASES}); the legacy NBT blob never is. That last one is real, documented
	 * lossiness with real gameplay consequences worth naming: 21 of the shipped {@code required_item} specs
	 * are aspect-tagged ({@code crystal_essence} with {@code {Aspects:[{key:'aer'}]}}, {@code phial} with
	 * {@code {Aspects:[{amount:10,key:'vitium'}]}}), and dropping the tag widens those from "an Aer essence
	 * crystal" to "any essence crystal". The aspect data itself lives in a real modern data component here,
	 * so honouring it is genuinely possible - it just needs a real legacy-NBT→component translator, which is
	 * its own chunk and is only worth writing once something actually consumes these gates.
	 *
	 * <p>Real finding worth recording, from reading the original's own registrations rather than assuming:
	 * a handful of the shipped specs are <b>already dead in the true original</b> - no {@code thaumcraft:metal},
	 * {@code thaumcraft:nitor} or {@code thaumcraft:leather} item was ever registered by Thaumcraft 6 (its
	 * metal blocks are {@code metal_brass}/{@code metal_thaumium}/…, its nitor is {@code nitor_<dye>}, and
	 * there is no tallow-adjacent "leather" item at all). Those specs resolve to nothing here for exactly the
	 * reason they resolved to nothing upstream, so skipping them is the faithful behaviour, not a porting gap.
	 * The same goes for {@code focus_2}/{@code focus_3} and {@code enchanted_placeholder}, which are real
	 * items in the original but genuinely aren't ported yet, and for the two {@code required_item} entries
	 * whose id is the malformed double-namespace {@code minecraft:thaumcraft:enchanted_placeholder}.
	 *
	 * @return an {@code Item}, a {@code TagKey<Item>}, or {@code null} if this spec has no real modern form.
	 */
	public static Object resolveSpec(String spec) {
		if (spec == null || spec.isEmpty()) return null;
		String[] parts = spec.split(";");
		String head = parts[0];

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
		return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
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
	 * <p>Real and correct today, but deliberately not wired to stage advancement yet - see this class's own
	 * doc comment for exactly what that's blocked on.
	 */
	public static boolean isStageSatisfied(Player player, ResearchStage stage) {
		if (stage == null) return true;
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);

		int[] craftReferences = stage.getCraftReference();
		if (craftReferences != null) {
			// Matching the original exactly: the gate is "any ONE of the listed items", not all of them -
			// several stages list real alternatives (any one focus, any one primal crystal).
			boolean any = craftReferences.length == 0;
			for (int code : craftReferences) {
				if (knowledge.isResearchKnown(researchKeyFor(code))) {
					any = true;
					break;
				}
			}
			if (!any) return false;
		}

		Object[] obtain = stage.getObtain();
		if (obtain != null && obtain.length > 0) {
			boolean any = false;
			for (Object target : obtain) {
				if (isCarrying(player, target)) {
					any = true;
					break;
				}
			}
			if (!any) return false;
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private static boolean isCarrying(Player player, Object target) {
		Predicate<ItemStack> matches;
		if (target instanceof Item item) matches = stack -> stack.is(item);
		else if (target instanceof TagKey<?> tag) matches = stack -> stack.is((TagKey<Item>) tag);
		else return false;
		// maxCount 0 means "count only, don't remove" - the same real accessor ScanSky/ResearchTableMenu
		// already use for this port's own inventory checks.
		return player.getInventory().clearOrCountMatchingItems(matches, 0, player.getInventory()) > 0;
	}

	private ResearchRequirements() {
	}
}

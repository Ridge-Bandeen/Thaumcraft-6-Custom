package thaumcraft.common.lib.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.api.research.ResearchStage.Knowledge;

/**
 * Real, data-driven replacement for the old (1.12.2) {@code ResearchManager.parseAllResearch}/{@code
 * parseResearchJson} - same real JSON schema (see {@code thaumcraft/api/research/_example.json.txt}) and the
 * same 8 real, complete content files this port carries forward verbatim from {@code master}'s {@code
 * assets/thaumcraft/research/*.json} (moved to the modern data-pack path {@code data/thaumcraft/research/}
 * since research is gated server-side too), just re-homed onto {@link SimpleJsonResourceReloadListener} -
 * the modern equivalent of the original's manual classpath resource scan - and re-populating the already-real
 * {@link ResearchCategories}/{@link ResearchCategory#research} registered by {@code ThaumcraftResearchCategories}.
 *
 * Deliberately narrower than the original parser in one respect: <b>reward_item</b>/<b>reward_knowledge</b>
 * (unused by any of the 8 real content files - verified directly) are skipped. Everything else the real
 * hex-grid browser needs - key/name/category/parents/siblings/meta/location, icons, stage text, stage
 * recipes, required_knowledge, required_research, warp, and addenda - is parsed for real.
 *
 * <b>required_item</b>/<b>required_craft</b> are parsed for real too now. They used to be skipped here, on
 * the grounds that they were display-only without the crafting/possession-triggered auto-progression hook -
 * that hook is real as of {@link thaumcraft.common.lib.events.ResearchCraftingEvents}, so the reason no
 * longer holds and the gates are resolved into {@link ResearchStage#setObtain}/{@link ResearchStage#setCraft}/
 * {@link ResearchStage#setCraftReference} through {@link ResearchRequirements}. What those gates still don't
 * do is <i>gate</i> anything - that part is blocked on the Thaumonomicon reader's atomic-completion
 * architecture, which {@link ResearchRequirements}' own doc comment lays out in full.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class ThaumcraftResearchLoader extends SimpleJsonResourceReloadListener {

	private static final Gson GSON = new Gson();

	public ThaumcraftResearchLoader() {
		super(GSON, "research");
	}

	@SubscribeEvent
	public static void onAddReloadListeners(AddReloadListenerEvent event) {
		event.addListener(new ThaumcraftResearchLoader());
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
		ResearchRequirements.clear();
		for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
			category.research.clear();
			category.minDisplayColumn = 0;
			category.minDisplayRow = 0;
			category.maxDisplayColumn = 0;
			category.maxDisplayRow = 0;
		}

		int loaded = 0;
		for (Map.Entry<ResourceLocation, JsonElement> file : resources.entrySet()) {
			try {
				JsonArray entries = file.getValue().getAsJsonObject().getAsJsonArray("entries");
				if (entries == null) continue;
				for (JsonElement el : entries) {
					try {
						addToCategory(parseEntry(el.getAsJsonObject()));
						loaded++;
					} catch (Exception ex) {
						Thaumcraft.LOGGER.warn("Invalid research entry in {}: {}", file.getKey(), ex.toString());
					}
				}
			} catch (Exception ex) {
				Thaumcraft.LOGGER.warn("Invalid research file {}: {}", file.getKey(), ex.toString());
			}
		}
		Thaumcraft.LOGGER.info("Loaded {} Thaumonomicon research entries", loaded);
	}

	private static ResearchEntry parseEntry(JsonObject obj) throws Exception {
		ResearchEntry entry = new ResearchEntry();
		entry.setKey(obj.getAsJsonPrimitive("key").getAsString());
		entry.setName(obj.getAsJsonPrimitive("name").getAsString());
		entry.setCategory(obj.getAsJsonPrimitive("category").getAsString());
		if (entry.getKey() == null || entry.getName() == null || entry.getCategory() == null) {
			throw new Exception("research entry missing key/name/category");
		}

		if (obj.has("parents")) entry.setParents(stringArray(obj.getAsJsonArray("parents")));
		if (obj.has("siblings")) entry.setSiblings(stringArray(obj.getAsJsonArray("siblings")));
		if (obj.has("icons")) entry.setIcons(parseIcons(obj.getAsJsonArray("icons")));

		if (obj.has("meta")) {
			String[] metaStrings = stringArray(obj.getAsJsonArray("meta"));
			if (metaStrings != null) {
				EnumResearchMeta[] metas = new EnumResearchMeta[metaStrings.length];
				for (int i = 0; i < metaStrings.length; i++) metas[i] = EnumResearchMeta.valueOf(metaStrings[i].toUpperCase());
				entry.setMeta(metas);
			}
		}

		if (obj.has("location")) {
			JsonArray loc = obj.getAsJsonArray("location");
			entry.setDisplayColumn(loc.get(0).getAsInt());
			entry.setDisplayRow(loc.get(1).getAsInt());
		}

		JsonArray stagesJson = obj.getAsJsonArray("stages");
		if (stagesJson == null || stagesJson.isEmpty()) throw new Exception("research entry has no stages");
		List<ResearchStage> stages = new ArrayList<>();
		for (JsonElement el : stagesJson) stages.add(parseStage(el.getAsJsonObject()));
		entry.setStages(stages.toArray(new ResearchStage[0]));

		if (obj.has("addenda")) {
			JsonArray addendaJson = obj.getAsJsonArray("addenda");
			List<ResearchAddendum> addenda = new ArrayList<>();
			for (JsonElement el : addendaJson) {
				JsonObject a = el.getAsJsonObject();
				ResearchAddendum addendum = new ResearchAddendum();
				addendum.setText(a.getAsJsonPrimitive("text").getAsString());
				if (addendum.getText() == null) throw new Exception("addendum missing text");
				if (a.has("recipes")) addendum.setRecipes(resourceLocationArray(a.getAsJsonArray("recipes")));
				if (a.has("required_research")) addendum.setResearch(stringArray(a.getAsJsonArray("required_research")));
				addenda.add(addendum);
			}
			entry.setAddenda(addenda.toArray(new ResearchAddendum[0]));
		}

		return entry;
	}

	private static ResearchStage parseStage(JsonObject obj) throws Exception {
		ResearchStage stage = new ResearchStage();
		stage.setText(obj.getAsJsonPrimitive("text").getAsString());
		if (stage.getText() == null) throw new Exception("research stage missing text");

		if (obj.has("recipes")) stage.setRecipes(resourceLocationArray(obj.getAsJsonArray("recipes")));

		if (obj.has("required_knowledge")) {
			String[] raw = stringArray(obj.getAsJsonArray("required_knowledge"));
			if (raw != null) {
				List<Knowledge> know = new ArrayList<>();
				for (String s : raw) {
					Knowledge k = Knowledge.parse(s);
					if (k != null) know.add(k);
				}
				if (!know.isEmpty()) stage.setKnow(know.toArray(new Knowledge[0]));
			}
		}

		if (obj.has("required_research")) {
			// same "KEY" or "KEY;customicon" convention as the original - only the key half matters until
			// the "?"/map/flask/chest research-icon rendering is ported, so the icon half is kept but unused.
			String[] raw = stringArray(obj.getAsJsonArray("required_research"));
			if (raw != null) {
				String[] keys = new String[raw.length];
				String[] icons = new String[raw.length];
				for (int i = 0; i < raw.length; i++) {
					String[] split = raw[i].split(";", 2);
					keys[i] = split[0];
					icons[i] = split.length > 1 ? split[1] : null;
				}
				stage.setResearch(keys);
				stage.setResearchIcon(icons);
			}
		}

		if (obj.has("required_item")) {
			Object[] obtain = resolveSpecs(obj.getAsJsonArray("required_item"));
			if (obtain.length > 0) stage.setObtain(obtain);
		}

		if (obj.has("required_craft")) {
			Object[] craft = resolveSpecs(obj.getAsJsonArray("required_craft"));
			if (craft.length > 0) {
				stage.setCraft(craft);
				int[] references = new int[craft.length];
				for (int i = 0; i < craft.length; i++) {
					references[i] = ResearchRequirements.registerCraftReference(craft[i]);
				}
				stage.setCraftReference(references);
			}
		}

		if (obj.has("warp")) stage.setWarp(obj.getAsJsonPrimitive("warp").getAsInt());

		return stage;
	}

	/**
	 * Resolves a real {@code required_item}/{@code required_craft} array, dropping the entries that have no
	 * real modern form. See {@link ResearchRequirements#resolveSpec} for exactly which those are and why -
	 * notably, several are specs that were already dead in shipped Thaumcraft 6 itself, so dropping them is
	 * faithful rather than lossy.
	 */
	private static Object[] resolveSpecs(JsonArray array) {
		if (array == null) return new Object[0];
		List<Object> resolved = new ArrayList<>();
		for (JsonElement el : array) {
			if (!el.isJsonPrimitive()) continue;
			Object target = ResearchRequirements.resolveSpec(el.getAsString());
			if (target != null) resolved.add(target);
		}
		return resolved.toArray();
	}

	private static void addToCategory(ResearchEntry entry) {
		ResearchCategory category = ResearchCategories.getResearchCategory(entry.getCategory());
		if (category == null) {
			Thaumcraft.LOGGER.warn("Research [{}] not added - unknown category [{}]", entry.getKey(), entry.getCategory());
			return;
		}
		if (category.research.containsKey(entry.getKey())) {
			Thaumcraft.LOGGER.warn("Research [{}] not added - duplicate key", entry.getKey());
			return;
		}
		for (ResearchEntry existing : category.research.values()) {
			if (existing.getDisplayColumn() == entry.getDisplayColumn() && existing.getDisplayRow() == entry.getDisplayRow()) {
				Thaumcraft.LOGGER.warn("Research [{}] not added - overlaps [{}] at {},{}", entry.getKey(), existing.getKey(), entry.getDisplayColumn(), entry.getDisplayRow());
				return;
			}
		}
		category.research.put(entry.getKey(), entry);
		category.minDisplayColumn = Math.min(category.minDisplayColumn, entry.getDisplayColumn());
		category.minDisplayRow = Math.min(category.minDisplayRow, entry.getDisplayRow());
		category.maxDisplayColumn = Math.max(category.maxDisplayColumn, entry.getDisplayColumn());
		category.maxDisplayRow = Math.max(category.maxDisplayRow, entry.getDisplayRow());
	}

	/**
	 * Real entry icons, needed by the real hex-grid browser: each real element is either a texture path
	 * ({@code ResourceLocation}, e.g. {@code "thaumcraft:textures/items/thaumonomicon.png"}), a real item
	 * ({@code ItemStack}, from a plain modern item id or the original's legacy {@code "id;count;damage"}
	 * itemstack-command syntax - only the id half is kept, since damage-value item variants aren't a modern
	 * concept), or a {@code "focus:key"} string (a {@link thaumcraft.api.casters.FocusEngine} element -
	 * rendered as a generic placeholder for now, since no focus-part icon renderer exists outside the Focal
	 * Manipulator's own simplified plain-text UI). Multiple icons cycle in the browser, matching the original.
	 */
	private static Object[] parseIcons(JsonArray array) {
		if (array == null || array.isEmpty()) return null;
		List<Object> out = new ArrayList<>();
		for (JsonElement el : array) {
			String s = el.getAsString();
			if (s.startsWith("focus")) {
				out.add(s);
				continue;
			}
			String id = s.contains(";") ? s.substring(0, s.indexOf(';')) : s;
			// Legacy metadata ids ("thaumcraft:plate;1;1", "thaumcraft:mind", "thaumcraft:smelter_basic") go through
			// the same 1.12 -> modern alias table the stage requirements use, so they show the right item rather than
			// falling through to a (missing) texture path.
			Object resolved = ResearchRequirements.resolveSpec(s);
			ItemStack stack = resolved instanceof net.minecraft.world.item.Item item ? new ItemStack(item) : parseItemStack(id);
			if (stack != null && !stack.isEmpty()) {
				out.add(stack);
			} else {
				try {
					out.add(ResourceLocation.parse(id));
				} catch (Exception ex) {
					Thaumcraft.LOGGER.debug("Skipping unparseable research icon [{}]: {}", s, ex.toString());
				}
			}
		}
		return out.isEmpty() ? null : out.toArray();
	}

	@Nullable
	private static ItemStack parseItemStack(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		if (rl == null) return null;
		return BuiltInRegistries.ITEM.getOptional(rl).map(ItemStack::new).orElse(null);
	}

	private static String[] stringArray(JsonArray array) {
		if (array == null || array.isEmpty()) return null;
		String[] out = new String[array.size()];
		for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsString();
		return out;
	}

	/** Skips (rather than throwing on) individual references that don't parse as a real modern {@link
	 * ResourceLocation} - a real, common case here: several {@code "recipes"} entries in the shipped content
	 * are still old (pre-1.13) CamelCase recipe-registry names (e.g. {@code "thaumcraft:StoneArcane"}) with
	 * no modern ID they map onto. Since this field isn't rendered by the v1 Thaumonomicon screen anyway (see
	 * that class's own doc comment on deferred recipe-visualization page types), dropping just the
	 * unparseable references - rather than letting one bad name take down the whole containing entry, which
	 * is what a thrown exception here would do - is the correct, low-risk behavior. */
	private static ResourceLocation[] resourceLocationArray(JsonArray array) {
		if (array == null || array.isEmpty()) return null;
		List<ResourceLocation> out = new ArrayList<>();
		for (JsonElement el : array) {
			String s = el.getAsString();
			try {
				// The original's catalog keys are CamelCase ("thaumcraft:Bellows"), which a modern ResourceLocation refuses;
				// they're matched case-insensitively (see ResearchRecipeCatalog), so carry them lower-cased.
				out.add(ResourceLocation.parse(s.toLowerCase(java.util.Locale.ROOT)));
			} catch (Exception ex) {
				Thaumcraft.LOGGER.debug("Skipping unparseable research recipe reference [{}]: {}", s, ex.toString());
			}
		}
		return out.isEmpty() ? null : out.toArray(new ResourceLocation[0]);
	}
}

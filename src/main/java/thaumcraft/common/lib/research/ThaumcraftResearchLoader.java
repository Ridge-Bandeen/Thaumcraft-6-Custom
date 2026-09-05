package thaumcraft.common.lib.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
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
 * Deliberately narrower than the original parser for this pass, matching the same "simplified Thaumonomicon"
 * scope cut documented on the new research {@code Screen}: <b>icons</b>, <b>reward_item</b>/<b>reward_knowledge</b>
 * (unused by any of the 8 real content files - verified directly), and the <b>required_item</b>/<b>required_craft</b>
 * stage gates (display-only without the crafting/possession-triggered auto-progression hook, itself a real,
 * separate, not-yet-ported system) are all skipped. Everything that drives the v1 GUI - key/name/category/
 * parents/siblings/meta/location, stage text, stage recipes, required_knowledge, required_research, warp, and
 * addenda - is parsed for real.
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

		if (obj.has("warp")) stage.setWarp(obj.getAsJsonPrimitive("warp").getAsInt());

		return stage;
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
				out.add(ResourceLocation.parse(s));
			} catch (Exception ex) {
				Thaumcraft.LOGGER.debug("Skipping unparseable research recipe reference [{}]: {}", s, ex.toString());
			}
		}
		return out.isEmpty() ? null : out.toArray(new ResourceLocation[0]);
	}
}

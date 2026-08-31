package thaumcraft.common.lib.research;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.ResearchCategory;

/**
 * The concrete {@link IPlayerKnowledge} implementation (this class didn't exist in the ported API - the
 * 1.12.2 original lived entirely in {@code thaumcraft.common}, same as here). Attached to players via
 * {@link thaumcraft.api.capabilities.ThaumcraftCapabilities}'s Data Attachment.
 *
 * Simplified versus the original for this pass: there's no loaded {@code ResearchEntry} data yet (the
 * research/Thaumonomicon JSON system isn't ported), so there's no "total stage count" to compare against
 * to distinguish "known" from "truly complete" multi-stage research. A research key is simply "known" (and
 * therefore also "complete") once it's been added at all; {@code isResearchKnown("key@N")} still does real
 * stage-number comparison against whatever stage was recorded, since that syntax is already used by
 * recipes ported so far (e.g. "FIRSTSTEPS@2"). Revisit once research entries are loaded for real.
 *
 * {@link #sync} is a no-op - needs the not-yet-ported network layer to actually push state to the client;
 * server-side logic (research gates, etc.) works fine without it in the meantime.
 */
public class PlayerKnowledge implements IPlayerKnowledge {

	private final Map<String, Integer> researchProgress = new HashMap<>();
	private final Map<String, Set<EnumResearchFlag>> researchFlags = new HashMap<>();
	private final Map<EnumKnowledgeType, Map<String, Integer>> knowledge = new HashMap<>();

	private static final String NO_CATEGORY = "NONE";

	public PlayerKnowledge() {
		// TODO: the real progression is a starting Thaumonomicon gift + scanning-based research
		// (ScanningManager, not ported - see the port-tracking memory notes). Without any way yet to
		// grant research in game, defaulting every new player to knowing FIRSTSTEPS keeps the recipes
		// ported so far (which all gate on it) actually reachable now that research-gating is enforced
		// for real. Remove once a real granting path exists.
		researchProgress.put("FIRSTSTEPS", 2);
	}

	@Override
	public void clear() {
		researchProgress.clear();
		researchFlags.clear();
		knowledge.clear();
	}

	@Override
	public EnumResearchStatus getResearchStatus(@Nonnull String research) {
		Integer stage = researchProgress.get(stripQualifiers(research));
		return stage == null ? EnumResearchStatus.UNKNOWN : EnumResearchStatus.COMPLETE;
	}

	@Override
	public boolean isResearchComplete(String research) {
		if (research == null || research.isEmpty()) return true;
		return researchProgress.containsKey(stripQualifiers(research));
	}

	@Override
	public boolean isResearchKnown(String research) {
		if (research == null || research.isEmpty()) return true;
		int at = research.indexOf('@');
		if (at < 0) return researchProgress.containsKey(research);
		String key = research.substring(0, at);
		int wantStage;
		try {
			wantStage = Integer.parseInt(research.substring(at + 1));
		} catch (NumberFormatException e) {
			return researchProgress.containsKey(key);
		}
		Integer haveStage = researchProgress.get(key);
		return haveStage != null && haveStage >= wantStage;
	}

	private static String stripQualifiers(String research) {
		int at = research.indexOf('@');
		return at < 0 ? research : research.substring(0, at);
	}

	@Override
	public int getResearchStage(@Nonnull String research) {
		Integer stage = researchProgress.get(stripQualifiers(research));
		return stage == null ? -1 : stage;
	}

	@Override
	public boolean addResearch(@Nonnull String research) {
		researchProgress.putIfAbsent(research, 0);
		return true;
	}

	@Override
	public boolean setResearchStage(@Nonnull String research, int stage) {
		researchProgress.put(research, stage);
		return true;
	}

	@Override
	public boolean removeResearch(@Nonnull String research) {
		researchProgress.remove(research);
		researchFlags.remove(research);
		return true;
	}

	@Nonnull
	@Override
	public Set<String> getResearchList() {
		return researchProgress.keySet();
	}

	@Override
	public boolean setResearchFlag(@Nonnull String research, @Nonnull EnumResearchFlag flag) {
		researchFlags.computeIfAbsent(research, k -> new HashSet<>()).add(flag);
		return true;
	}

	@Override
	public boolean clearResearchFlag(@Nonnull String research, @Nonnull EnumResearchFlag flag) {
		Set<EnumResearchFlag> flags = researchFlags.get(research);
		return flags != null && flags.remove(flag);
	}

	@Override
	public boolean hasResearchFlag(@Nonnull String research, @Nonnull EnumResearchFlag flag) {
		Set<EnumResearchFlag> flags = researchFlags.get(research);
		return flags != null && flags.contains(flag);
	}

	private static String categoryKey(ResearchCategory category) {
		return category == null ? NO_CATEGORY : category.key;
	}

	@Override
	public boolean addKnowledge(@Nonnull EnumKnowledgeType type, ResearchCategory category, int amount) {
		Map<String, Integer> byCategory = knowledge.computeIfAbsent(type, k -> new HashMap<>());
		String key = categoryKey(category);
		int now = byCategory.getOrDefault(key, 0) + amount;
		byCategory.put(key, Math.max(0, now));
		return true;
	}

	@Override
	public int getKnowledge(@Nonnull EnumKnowledgeType type, ResearchCategory category) {
		return getKnowledgeRaw(type, category) / type.getProgression();
	}

	@Override
	public int getKnowledgeRaw(@Nonnull EnumKnowledgeType type, ResearchCategory category) {
		Map<String, Integer> byCategory = knowledge.get(type);
		if (byCategory == null) return 0;
		return byCategory.getOrDefault(categoryKey(category), 0);
	}

	@Override
	public void sync(ServerPlayer player) {
		// TODO: needs the not-yet-ported network layer to push this to the client.
	}

	@Nonnull
	@Override
	public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();

		ListTag researchList = new ListTag();
		for (Map.Entry<String, Integer> e : researchProgress.entrySet()) {
			CompoundTag entry = new CompoundTag();
			entry.putString("key", e.getKey());
			entry.putInt("stage", e.getValue());
			Set<EnumResearchFlag> flags = researchFlags.get(e.getKey());
			if (flags != null && !flags.isEmpty()) {
				ListTag flagList = new ListTag();
				for (EnumResearchFlag f : flags) flagList.add(net.minecraft.nbt.StringTag.valueOf(f.name()));
				entry.put("flags", flagList);
			}
			researchList.add(entry);
		}
		tag.put("research", researchList);

		ListTag knowledgeList = new ListTag();
		for (Map.Entry<EnumKnowledgeType, Map<String, Integer>> typeEntry : knowledge.entrySet()) {
			for (Map.Entry<String, Integer> catEntry : typeEntry.getValue().entrySet()) {
				CompoundTag entry = new CompoundTag();
				entry.putString("type", typeEntry.getKey().name());
				entry.putString("category", catEntry.getKey());
				entry.putInt("amount", catEntry.getValue());
				knowledgeList.add(entry);
			}
		}
		tag.put("knowledge", knowledgeList);

		return tag;
	}

	@Override
	public void deserializeNBT(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
		clear();

		ListTag researchList = tag.getList("research", Tag.TAG_COMPOUND);
		for (int i = 0; i < researchList.size(); i++) {
			CompoundTag entry = researchList.getCompound(i);
			String key = entry.getString("key");
			researchProgress.put(key, entry.getInt("stage"));
			if (entry.contains("flags")) {
				Set<EnumResearchFlag> flags = new LinkedHashSet<>();
				ListTag flagList = entry.getList("flags", Tag.TAG_STRING);
				for (int j = 0; j < flagList.size(); j++) {
					try {
						flags.add(EnumResearchFlag.valueOf(flagList.getString(j)));
					} catch (IllegalArgumentException ignored) {}
				}
				researchFlags.put(key, flags);
			}
		}

		ListTag knowledgeList = tag.getList("knowledge", Tag.TAG_COMPOUND);
		for (int i = 0; i < knowledgeList.size(); i++) {
			CompoundTag entry = knowledgeList.getCompound(i);
			try {
				EnumKnowledgeType type = EnumKnowledgeType.valueOf(entry.getString("type"));
				knowledge.computeIfAbsent(type, k -> new HashMap<>()).put(entry.getString("category"), entry.getInt("amount"));
			} catch (IllegalArgumentException ignored) {}
		}
	}
}

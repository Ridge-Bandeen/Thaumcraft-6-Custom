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
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.common.lib.network.ClientboundPlayerKnowledgePacket;

/**
 * The concrete {@link IPlayerKnowledge} implementation (this class didn't exist in the ported API - the
 * 1.12.2 original lived entirely in {@code thaumcraft.common}, same as here). Attached to players via
 * {@link thaumcraft.api.capabilities.ThaumcraftCapabilities}'s Data Attachment.
 *
 * {@code getResearchStatus}/{@code isResearchComplete} are now the real stage-count-aware check the
 * interface documents ("complete" means the stored stage exceeds the real {@link ResearchEntry}'s stage
 * count, looked up live via {@link ResearchCategories} now that {@code ThaumcraftResearchLoader} actually
 * populates it) rather than the old flat "known == complete" placeholder this class shipped with before the
 * research JSON system was loaded for real.
 *
 * {@link #sync} now really pushes state to the client via {@link ClientboundPlayerKnowledgePacket} -
 * {@link thaumcraft.common.container.ThaumonomiconMenu} is the first real caller.
 */
public class PlayerKnowledge implements IPlayerKnowledge {

	private final Map<String, Integer> researchProgress = new HashMap<>();
	private final Map<String, Set<EnumResearchFlag>> researchFlags = new HashMap<>();
	private final Map<EnumKnowledgeType, Map<String, Integer>> knowledge = new HashMap<>();

	private static final String NO_CATEGORY = "NONE";

	public PlayerKnowledge() {
		// Every player starts able to craft the basics, matching the original's practical "always known"
		// status for this entry (its own first stage requires nothing but opening the Thaumonomicon in the
		// first place). Granted as real full completion now that real stage counts are loadable; falls back
		// to a flat "known" stage of 1 if the real entry hasn't loaded yet (e.g. very early in datapack init).
		ResearchEntry firstSteps = ResearchCategories.getResearch("FIRSTSTEPS");
		int stageCount = (firstSteps != null && firstSteps.getStages() != null) ? firstSteps.getStages().length : 0;
		researchProgress.put("FIRSTSTEPS", stageCount + 1);
	}

	@Override
	public void clear() {
		researchProgress.clear();
		researchFlags.clear();
		knowledge.clear();
	}

	@Override
	public EnumResearchStatus getResearchStatus(@Nonnull String research) {
		String key = stripQualifiers(research);
		Integer stage = researchProgress.get(key);
		if (stage == null) return EnumResearchStatus.UNKNOWN;
		ResearchEntry entry = ResearchCategories.getResearch(key);
		// -1 for keys with no real ResearchEntry (the "!virtualFlag" convention, or an ad-hoc
		// "required_research" reference that isn't a full entry in its own right) - there's no real stage
		// concept to track there, so simply being known (stage 0, the default addResearch sets) already
		// counts as complete, matching the original's flat "known == complete" treatment for those keys.
		int stageCount = (entry != null && entry.getStages() != null) ? entry.getStages().length : -1;
		return stage > stageCount ? EnumResearchStatus.COMPLETE : EnumResearchStatus.IN_PROGRESS;
	}

	@Override
	public boolean isResearchComplete(String research) {
		if (research == null || research.isEmpty()) return true;
		return getResearchStatus(research) == EnumResearchStatus.COMPLETE;
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
		try {
			CompoundTag tag = serializeNBT(player.level().registryAccess());
			// The original's own PacketSyncKnowledge: the POPUP flags go out once (the client toasts them) and are then
			// cleared server-side, so a later sync doesn't pop the same "Research Completed!" toast again.
			for (String key : getResearchList().toArray(new String[0])) {
				clearResearchFlag(key, EnumResearchFlag.POPUP);
			}
			PacketDistributor.sendToPlayer(player, new ClientboundPlayerKnowledgePacket(tag));
		} catch (Exception e) {
			// Same defensive catch ResearchTableMenu/ThaumonomiconMenu's own sync already uses - a genuinely
			// disconnected/mock player makes PacketDistributor refuse the send.
		}
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

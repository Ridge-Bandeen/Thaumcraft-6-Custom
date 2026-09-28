package thaumcraft.common.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.lib.network.ClientboundPlayerKnowledgePacket;
import thaumcraft.common.lib.research.ResearchRequirements;
import thaumcraft.common.registry.ThaumcraftMenus;

/**
 * The menu behind the Thaumonomicon ({@link thaumcraft.client.gui.ThaumonomiconScreen}). No slots - it exists to give
 * the book a server round-trip for its research clicks and to push the viewer's {@link IPlayerKnowledge} to the client
 * on open ({@link ClientboundPlayerKnowledgePacket}; attachments aren't auto-synced).
 *
 * <p>The original sent {@code PacketSyncProgressToServer(key, first, checks, noFlags)} for three different clicks; here
 * each is a button id carrying an action and the entry (its index in {@link #researchKeys()}, identical on both sides
 * since both load the same research data):
 * <ul>
 * <li>{@link #ACTION_OPEN} - clicking a node in the browser. An entry not yet known is started, ungated
 * ({@code first=true, checks=false, noFlags=true}). A known one has its "new" badges cleared, and if it stands on its
 * last stage ({@code stage > 1 && stage >= stages}) its requirements are checked and it's finished if met
 * ({@code first=false, checks=true, noFlags=false}).</li>
 * <li>{@link #ACTION_COMPLETE_STAGE} - the "Complete stage" hotspot under the requirement checklist in the open book:
 * check and consume the current stage's requirements, then advance one stage ({@code first=false, checks=true,
 * noFlags=true}). A failed check does nothing, as in the original; the checklist already shows what's missing.</li>
 * </ul>
 * One deliberate difference from the original's {@code checkRequisites}: it consumed the carried items before looking at
 * the craft/research/knowledge gates, so a request whose later gates failed still ate the items. Here nothing is spent
 * unless every gate passes. The client only offers the click once everything is met, so this only matters for a stale
 * click, where eating items for nothing would be a plain loss.
 */
public class ThaumonomiconMenu extends AbstractContainerMenu {

	public static final int ACTION_OPEN = 0;
	public static final int ACTION_COMPLETE_STAGE = 1;
	private static final int ACTIONS = 2;

	public ThaumonomiconMenu(int containerId, Inventory playerInventory) {
		super(ThaumcraftMenus.THAUMONOMICON.get(), containerId);
		if (playerInventory.player instanceof ServerPlayer serverPlayer) {
			syncToPlayer(serverPlayer);
		}
	}

	/** Every research key, sorted - the shared index a button id refers to. */
	public static List<String> researchKeys() {
		List<String> keys = new ArrayList<>();
		for (ResearchCategory category : ResearchCategories.researchCategories.values()) keys.addAll(category.research.keySet());
		Collections.sort(keys);
		return keys;
	}

	/** The button id for {@code action} on {@code key}, or -1 if the key isn't a research entry. */
	public static int buttonId(int action, String key) {
		int index = researchKeys().indexOf(key);
		return index < 0 ? -1 : index * ACTIONS + action;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		List<String> keys = researchKeys();
		if (id < 0 || id / ACTIONS >= keys.size()) return false;
		ResearchEntry target = ResearchCategories.getResearch(keys.get(id / ACTIONS));
		if (target == null) return false;
		boolean changed = id % ACTIONS == ACTION_COMPLETE_STAGE ? completeStage(player, target) : open(player, target);
		if (changed && player instanceof ServerPlayer serverPlayer) {
			syncToPlayer(serverPlayer);
		}
		return changed;
	}

	private boolean open(Player player, ResearchEntry target) {
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		String key = target.getKey();
		if (!knowledge.isResearchKnown(key)) {
			if (!ThaumcraftApi.internalMethods.doesPlayerHaveRequisites(player, key)) return false;
			thaumcraft.common.ThaumcraftInternalMethodHandler.noFlags = true;
			return ThaumcraftApi.internalMethods.progressResearch(player, key);
		}
		boolean changed = knowledge.clearResearchFlag(key, IPlayerKnowledge.EnumResearchFlag.RESEARCH);
		changed |= knowledge.clearResearchFlag(key, IPlayerKnowledge.EnumResearchFlag.PAGE);
		int stage = knowledge.getResearchStage(key);
		if (target.getStages() != null && stage > 1 && stage >= target.getStages().length) {
			changed |= checkAndProgress(player, target, false);
		}
		return changed;
	}

	private boolean completeStage(Player player, ResearchEntry target) {
		if (!ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(target.getKey())) return false;
		return checkAndProgress(player, target, true);
	}

	/** The original's {@code checkRequisites} followed by {@code progressResearch}. */
	private boolean checkAndProgress(Player player, ResearchEntry entry, boolean noFlags) {
		ResearchStage[] stages = entry.getStages();
		if (stages == null) return false;
		int stageIndex = ThaumcraftCapabilities.getKnowledge(player).getResearchStage(entry.getKey()) - 1;
		if (stageIndex < 0) return false;
		if (stageIndex < stages.length) {
			ResearchStage stage = stages[stageIndex];
			if (!ResearchRequirements.isStageSatisfied(player, stage)) return false;
			if (player instanceof ServerPlayer serverPlayer) ResearchRequirements.consumeStage(serverPlayer, stage);
		}
		if (noFlags) thaumcraft.common.ThaumcraftInternalMethodHandler.noFlags = true;
		return ThaumcraftApi.internalMethods.progressResearch(player, entry.getKey());
	}

	/** Same defensive try/catch {@code ResearchTableMenu#syncToPlayer} already established - a genuinely
	 * disconnected/mock player (no negotiated payload channel, e.g. a GameTest mock player) makes {@code
	 * PacketDistributor} refuse the send; that's fine to just skip rather than let it blow up menu
	 * construction or a button click entirely. */
	private void syncToPlayer(ServerPlayer player) {
		try {
			IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
			CompoundTag tag = knowledge.serializeNBT(player.level().registryAccess());
			PacketDistributor.sendToPlayer(player, new ClientboundPlayerKnowledgePacket(tag));
		} catch (Exception e) {
			thaumcraft.Thaumcraft.LOGGER.debug("Couldn't sync player knowledge to {}", player.getGameProfile().getName(), e);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}
}

package thaumcraft.common.container;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * The real menu behind the new (simplified - see {@link thaumcraft.client.gui.ThaumonomiconScreen}'s own doc
 * comment) Thaumonomicon research browser. No slots at all - like {@code TurretConfigMenu}, this only exists
 * to (a) give the screen a real server round-trip for its one real action, matching the {@code clickMenuButton}
 * pattern {@code ResearchTableMenu} already established, and (b) push the viewing player's own
 * {@link IPlayerKnowledge} to the client via {@link ClientboundPlayerKnowledgePacket} - attachments aren't
 * auto-synced, and there's no other trigger for this yet (see that packet's own doc comment).
 *
 * <p>Restored 2026-09-27, at the user's own explicit request ("match the user experience perfectly with the
 * original") once a look at the {@code enchanted_placeholder} tooltip gap turned up that this class had never
 * actually consulted {@link ResearchRequirements} at all: the ONE button id now does two different real things
 * depending on the entry's own state, matching the true original's own two distinct network messages
 * ({@code PacketSyncProgressToServer}'s {@code first}/{@code checks} flags) that this port's single
 * {@code clickMenuButton} pattern collapses into one call:
 * <ul>
 * <li><b>Unknown entry</b> - "open" it: a real, genuinely ungated {@link ThaumcraftApi#internalMethods}
 * {@code progressResearch} call (matching {@code GuiResearchBrowser}'s own node-click, which never checked
 * requirements either - only the SECOND, in-book click does).</li>
 * <li><b>Known, not complete</b> - "study" its next stage: real per-stage gating via
 * {@link ResearchRequirements#isStageSatisfied}, real consumption via {@link ResearchRequirements#consumeStage}
 * on success, and a real chat message naming what's still missing on failure - a deliberate, honest stand-in
 * for the original's own graphical per-requirement checklist (green/red icons in the open book), which is a
 * real, separate client-rendering project of its own not undertaken here. See
 * {@link thaumcraft.client.gui.ThaumonomiconScreen}'s own "Study" button for the client side of this.</li>
 * <li><b>Already complete</b> - unchanged: just clears the "new" badges, matching
 * {@code GuiResearchBrowser}'s own real re-click handler.</li>
 * </ul>
 *
 * <p>The button id is the target {@link ResearchEntry#getKey()}'s own {@code hashCode()} rather than a stable
 * index - since {@link ResearchCategory#research} is a plain (unordered) map and the screen and this menu
 * only ever need to agree with <i>each other</i> at the moment of a single click, not across sessions, hashing
 * the key is simpler than threading a stable ordering through both sides.
 */
public class ThaumonomiconMenu extends AbstractContainerMenu {

	public ThaumonomiconMenu(int containerId, Inventory playerInventory) {
		super(ThaumcraftMenus.THAUMONOMICON.get(), containerId);
		if (playerInventory.player instanceof ServerPlayer serverPlayer) {
			syncToPlayer(serverPlayer);
		}
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		boolean result = startResearch(player, id);
		if (result && player instanceof ServerPlayer serverPlayer) {
			syncToPlayer(serverPlayer);
		}
		return result;
	}

	private boolean startResearch(Player player, int keyHash) {
		ResearchEntry target = null;
		for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
			for (ResearchEntry entry : category.research.values()) {
				if (entry.getKey().hashCode() == keyHash) {
					target = entry;
					break;
				}
			}
			if (target != null) break;
		}
		if (target == null) return false;

		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		if (knowledge.isResearchComplete(target.getKey())) {
			// Real original behavior: opening an already-known entry's page just clears its "new" badges,
			// same as GuiResearchBrowser's own real mouseClicked handler.
			boolean clearedResearch = knowledge.clearResearchFlag(target.getKey(), IPlayerKnowledge.EnumResearchFlag.RESEARCH);
			boolean clearedPage = knowledge.clearResearchFlag(target.getKey(), IPlayerKnowledge.EnumResearchFlag.PAGE);
			return clearedResearch || clearedPage;
		}

		if (knowledge.isResearchKnown(target.getKey())) {
			return studyNextStage(player, target, knowledge);
		}

		// Not known at all yet: the original's own real ungated node-click - see this class's own doc comment.
		if (!ThaumcraftApi.internalMethods.doesPlayerHaveRequisites(player, target.getKey())) {
			return false;
		}
		return ThaumcraftApi.internalMethods.progressResearch(player, target.getKey());
	}

	/** The original's own real in-book "study this stage" click: gate, consume, and advance exactly one stage -
	 * see this class's own doc comment. */
	private boolean studyNextStage(Player player, ResearchEntry entry, IPlayerKnowledge knowledge) {
		ResearchStage[] stages = entry.getStages();
		if (stages == null) return false;
		// Matches the original's own currentStage = getResearchStage(key) - 1 exactly.
		int stageIndex = knowledge.getResearchStage(entry.getKey()) - 1;
		if (stageIndex < 0 || stageIndex >= stages.length) return false;
		ResearchStage stage = stages[stageIndex];

		if (!ResearchRequirements.isStageSatisfied(player, stage)) {
			if (player instanceof ServerPlayer serverPlayer) reportMissing(serverPlayer, stage);
			return false;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			ResearchRequirements.consumeStage(serverPlayer, stage);
		}
		return ThaumcraftApi.internalMethods.progressResearch(player, entry.getKey());
	}

	/** A plain chat report of what a failed {@link #studyNextStage} is still missing - see this class's own
	 * doc comment for why this stands in for the original's own graphical checklist. */
	private void reportMissing(ServerPlayer player, ResearchStage stage) {
		StringBuilder missing = new StringBuilder();
		int[] craftReferences = stage.getCraftReference();
		if (craftReferences != null && craftReferences.length > 0) {
			missing.append(craftReferences.length == 1 ? "an item you must craft" : "items you must craft");
		}
		Object[] obtain = stage.getObtain();
		if (obtain != null && obtain.length > 0) {
			if (!missing.isEmpty()) missing.append(", ");
			missing.append(obtain.length == 1 ? "an item you must be carrying" : "items you must be carrying");
		}
		String[] research = stage.getResearch();
		if (research != null) {
			for (String key : research) {
				if (ThaumcraftCapabilities.knowsResearchStrict(player, key)) continue;
				if (!missing.isEmpty()) missing.append(", ");
				missing.append("more research or observation first");
				break;
			}
		}
		ResearchStage.Knowledge[] know = stage.getKnow();
		if (know != null) {
			IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
			for (ResearchStage.Knowledge k : know) {
				int have = knowledge.getKnowledge(k.type, k.category);
				if (have >= k.amount) continue;
				if (!missing.isEmpty()) missing.append(", ");
				String kind = k.type.name().charAt(0) + k.type.name().substring(1).toLowerCase();
				missing.append(k.amount - have).append(" more ").append(kind);
				if (k.category != null) missing.append(" (").append(ResearchCategories.getCategoryName(k.category.key).getString()).append(")");
			}
		}
		if (missing.isEmpty()) missing.append("its requirements");
		player.sendSystemMessage(Component.literal("You still need " + missing + " for this.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
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

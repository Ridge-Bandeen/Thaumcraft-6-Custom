package thaumcraft.client;

import javax.annotation.Nullable;

import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.common.lib.network.ClientboundPlayerKnowledgePacket;
import thaumcraft.common.lib.research.PlayerKnowledge;

/**
 * Client-side cache of the last {@link ClientboundPlayerKnowledgePacket} received - see that class's own doc
 * comment for why this exists (attachments aren't synced automatically, and the client's own {@code
 * LocalPlayer} is a different object from the server's {@code ServerPlayer} even in singleplayer). Reuses the
 * real {@link PlayerKnowledge} implementation itself as the deserialized snapshot - it already has the
 * exact real {@code isResearchKnown}/{@code isResearchComplete}/{@code getResearchStage} logic the
 * Thaumonomicon screen needs, so there's no separate client-side data class to keep in sync with it.
 */
public final class ClientPlayerKnowledgeCache {

	@Nullable
	private static IPlayerKnowledge data;

	private ClientPlayerKnowledgeCache() {
	}

	public static void handle(ClientboundPlayerKnowledgePacket payload) {
		PlayerKnowledge snapshot = new PlayerKnowledge();
		// PlayerKnowledge#serializeNBT/deserializeNBT never actually touch the registries parameter (no
		// registry-referencing data in a player's research/knowledge state) - null is safe here the same way
		// it would be on the server, just without a real HolderLookup.Provider on hand client-side to pass.
		snapshot.deserializeNBT(null, payload.data());
		data = snapshot;
		// Also make it the local player's own knowledge: ThaumcraftCapabilities.getKnowledge(player) reads the attachment, and on the client that
		// stayed at its empty default - so client-side checks (the Thaumometer glow on unscanned things) never saw a scan's result.
		// The original's own PacketSyncKnowledge#onMessage: every research entry flagged POPUP gets a "Research Completed!"
		// toast, then the flag is cleared (the server cleared its own copy when it sent this).
		for (String key : snapshot.getResearchList().toArray(new String[0])) {
			if (snapshot.hasResearchFlag(key, IPlayerKnowledge.EnumResearchFlag.POPUP)) {
				thaumcraft.api.research.ResearchEntry entry = thaumcraft.api.research.ResearchCategories.getResearch(key);
				if (entry != null) {
					net.minecraft.client.Minecraft.getInstance().getToasts().addToast(new thaumcraft.client.gui.ResearchToast(entry));
				}
			}
			snapshot.clearResearchFlag(key, IPlayerKnowledge.EnumResearchFlag.POPUP);
		}
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		if (player != null) {
			player.setData(thaumcraft.api.capabilities.ThaumcraftCapabilities.KNOWLEDGE, snapshot);
		}
	}

	/** The most recently synced knowledge for the local player, or an empty default if none has arrived yet
	 * (e.g. the Thaumonomicon screen opened before the server's sync packet landed). */
	public static IPlayerKnowledge getData() {
		return data != null ? data : new PlayerKnowledge();
	}

	public static void clear() {
		data = null;
	}
}

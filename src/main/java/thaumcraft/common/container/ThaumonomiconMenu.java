package thaumcraft.common.container;

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
import thaumcraft.common.lib.network.ClientboundPlayerKnowledgePacket;
import thaumcraft.common.registry.ThaumcraftMenus;

/**
 * The real menu behind the new (simplified - see {@link thaumcraft.client.gui.ThaumonomiconScreen}'s own doc
 * comment) Thaumonomicon research browser. No slots at all - like {@code TurretConfigMenu}, this only exists
 * to (a) give the screen a real server round-trip for its one real action ("start/complete this research"),
 * matching the {@code clickMenuButton} pattern {@code ResearchTableMenu} already established, and (b) push
 * the viewing player's own {@link IPlayerKnowledge} to the client via {@link ClientboundPlayerKnowledgePacket}
 * - attachments aren't auto-synced, and there's no other trigger for this yet (see that packet's own doc
 * comment).
 *
 * <p>The "start research" button id is the target {@link ResearchEntry#getKey()}'s own {@code hashCode()}
 * rather than a stable index - since {@link ResearchCategory#research} is a plain (unordered) map and the
 * screen and this menu only ever need to agree with <i>each other</i> at the moment of a single click, not
 * across sessions, hashing the key is simpler than threading a stable ordering through both sides.
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

		if (!ThaumcraftApi.internalMethods.doesPlayerHaveRequisites(player, target.getKey())) {
			return false;
		}
		return ThaumcraftApi.internalMethods.completeResearch(player, target.getKey());
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

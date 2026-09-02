package thaumcraft.common.lib.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.Thaumcraft;
import thaumcraft.common.container.FocalManipulatorMenu;
import thaumcraft.common.tiles.crafting.FocusElementNode;
import thaumcraft.common.tiles.crafting.TileFocalManipulator;

/**
 * Server-side handler for {@link ServerboundSaveFocusGraphPacket} - see that class's own doc comment for
 * why this only ever touches whatever {@link FocalManipulatorMenu} the sending player currently has open,
 * never a position taken from the packet itself.
 */
public final class ServerFocalManipulatorHandlers {

	private ServerFocalManipulatorHandlers() {
	}

	public static void handleSaveFocusGraph(ServerboundSaveFocusGraphPacket payload, ServerPlayer player) {
		if (!(player.containerMenu instanceof FocalManipulatorMenu menu)) {
			return;
		}
		TileFocalManipulator tile = menu.getTile();
		if (tile == null) {
			return;
		}
		try {
			tile.nodes.clear();
			for (CompoundTag nodeTag : payload.nodes()) {
				FocusElementNode node = new FocusElementNode();
				node.deserialize(nodeTag);
				tile.nodes.put(node.id, node);
			}
			tile.focusName = payload.focusName();
			tile.setChanged();
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Couldn't apply a saved focus graph from {}", player.getGameProfile().getName(), e);
		}
	}
}

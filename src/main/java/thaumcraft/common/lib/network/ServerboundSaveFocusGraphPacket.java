package thaumcraft.common.lib.network;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Sent on every change at the Focal Manipulator screen, as the original's PacketFocusNodesToServer and
 * PacketFocusNameToServer were. The first client-bound-the-other-way (client->server) packet this port has needed - every packet shipped
 * so far ({@code PacketFXSlash}/{@code PacketFXScanSource}/{@code PacketFXBlockBamf}) only ever flows
 * server->client. Saves a player's assembled node graph (built at a
 * {@link thaumcraft.common.tiles.crafting.TileFocalManipulator}'s future canvas screen - see that class's
 * own doc comment) back to the tile, plus the focus's chosen display name.
 *
 * Carries each node as a plain {@link CompoundTag} - the exact same shape
 * {@link thaumcraft.common.tiles.crafting.FocusElementNode#serialize()}/{@code deserialize} already use for
 * the tile's own NBT persistence, reused here rather than inventing a second graph encoding just for the
 * network. {@link net.neoforged.neoforge.network.handling.IPayloadContext#player()} is trusted for which
 * player sent this, but {@link ServerFocalManipulatorHandlers#handleSaveFocusGraph} still only ever applies
 * it to whatever {@link thaumcraft.common.container.FocalManipulatorMenu} that specific player currently
 * has open server-side - never a raw position taken from the packet - so a client can only ever edit the
 * graph of a manipulator it has legitimately opened.
 */
public record ServerboundSaveFocusGraphPacket(List<CompoundTag> nodes, String focusName) implements CustomPacketPayload {

	public static final Type<ServerboundSaveFocusGraphPacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "save_focus_graph"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSaveFocusGraphPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list()), ServerboundSaveFocusGraphPacket::nodes,
			ByteBufCodecs.STRING_UTF8, ServerboundSaveFocusGraphPacket::focusName,
			ServerboundSaveFocusGraphPacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

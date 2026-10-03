package thaumcraft.common.lib.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * The original's {@code PacketMiscEvent}: a bare event code for the client. {@link #HEARTBEAT} (0) on every warp
 * event plays the heartbeat unless the player's {@code nostress} option is on; {@link #MIST} (1, the warp mist) and
 * {@link #GUARDIAN} (2, an Eldritch Guardian nearby) thicken the fog
 * ({@link thaumcraft.client.lib.WarpScreenEffects#thickenFog}).
 */
public record ClientboundMiscEventPacket(byte kind) implements CustomPacketPayload {

	public static final byte HEARTBEAT = 0;
	public static final byte MIST = 1;
	public static final byte GUARDIAN = 2;

	public static void send(net.minecraft.server.level.ServerPlayer player, byte kind) {
		try {
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ClientboundMiscEventPacket(kind));
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Misc event packet failed to send (no real client connection?)", e);
		}
	}

	public static final Type<ClientboundMiscEventPacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "misc_event"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMiscEventPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, ClientboundMiscEventPacket::kind,
			ClientboundMiscEventPacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

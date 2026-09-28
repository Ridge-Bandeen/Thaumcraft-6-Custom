package thaumcraft.common.lib.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * The original's {@code PacketMiscEvent}: a bare event code for the client. Only {@link #HEARTBEAT} (0) is sent - on
 * every warp event, playing the heartbeat unless the player's {@code nostress} option is on. The original's codes 1
 * and 2 (fog thickening) have no client effect in this version.
 */
public record ClientboundMiscEventPacket(byte kind) implements CustomPacketPayload {

	public static final byte HEARTBEAT = 0;

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

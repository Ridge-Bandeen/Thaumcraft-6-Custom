package thaumcraft.common.lib.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.Thaumcraft;

/** The old (1.12.2) {@code PacketPlayerFlagToServer}: flag 1 - a Cloud Ring jump - clears the player's fall distance. */
public record ServerboundPlayerFlagPacket(int flag) implements CustomPacketPayload {

	public static final Type<ServerboundPlayerFlagPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "player_flag"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlayerFlagPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ServerboundPlayerFlagPacket::flag, ServerboundPlayerFlagPacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(ServerPlayer player) {
		if (flag == 1) player.resetFallDistance();
	}
}

package thaumcraft.common.lib.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * The original's {@code PacketSyncWarp} and {@code PacketWarpMessage} in one: the player's warp (for the Sanity
 * Checker and anything else client-side that reads it) and, when warp was just added or removed, which kind and by how
 * much, for the status message and whispers. {@code kind} is -1 for a plain sync, else 0 permanent, 1 normal, 2
 * temporary (the original's message types).
 */
public record ClientboundWarpPacket(CompoundTag data, byte kind, int amount) implements CustomPacketPayload {

	public static final Type<ClientboundWarpPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "warp"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarpPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.COMPOUND_TAG, ClientboundWarpPacket::data,
			ByteBufCodecs.BYTE, ClientboundWarpPacket::kind,
			ByteBufCodecs.VAR_INT, ClientboundWarpPacket::amount,
			ClientboundWarpPacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package thaumcraft.common.lib.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * The original's {@code PacketAuraToClient}: the aura (base, vis, flux) around the player, for the caster's vis dial
 * and the Thaumometer's gauge. Sent every 10 ticks while a caster is held and every 20 while a Thaumometer is.
 */
public record ClientboundAuraPacket(short base, float vis, float flux) implements CustomPacketPayload {

	public static final Type<ClientboundAuraPacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "aura"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAuraPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.SHORT, ClientboundAuraPacket::base,
			ByteBufCodecs.FLOAT, ClientboundAuraPacket::vis,
			ByteBufCodecs.FLOAT, ClientboundAuraPacket::flux,
			ClientboundAuraPacket::new);

	/** Sends the aura of the chunk the player stands in, if it has one. */
	public static void sendLocal(ServerPlayer player) {
		AuraChunk ac = AuraHandler.getAuraChunk(player.level(), player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
		if (ac != null) {
			send(player, new ClientboundAuraPacket(ac.getBase(), ac.getVis(), ac.getFlux()));
		}
	}

	/** Sends, tolerating a player with no real client connection (GameTest's mock players). */
	public static void send(ServerPlayer player, ClientboundAuraPacket packet) {
		try {
			PacketDistributor.sendToPlayer(player, packet);
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Aura packet failed to send (no real client connection?)", e);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

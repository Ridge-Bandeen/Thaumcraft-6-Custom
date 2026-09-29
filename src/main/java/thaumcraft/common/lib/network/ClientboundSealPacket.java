package thaumcraft.common.lib.network;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.seals.ISealConfigArea;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.SealPos;

/**
 * The original's {@code PacketSealToClient}: what the client needs to draw a placed seal while the player holds a seal
 * or Golem Bell - its icon, colour, work area and redstone setting. An empty {@code icon} removes the seal at that
 * spot; {@code clearAll} (sent first on login / changing dimension) forgets every seal the client knew.
 */
public record ClientboundSealPacket(BlockPos pos, Direction face, Optional<ResourceLocation> icon, byte color, BlockPos area,
		boolean hasArea, boolean redstone, boolean clearAll) implements CustomPacketPayload {

	public static final Type<ClientboundSealPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "seal"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSealPacket> STREAM_CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBlockPos(p.pos);
				buf.writeEnum(p.face);
				ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, p.icon);
				buf.writeByte(p.color);
				buf.writeBlockPos(p.area);
				buf.writeBoolean(p.hasArea);
				buf.writeBoolean(p.redstone);
				buf.writeBoolean(p.clearAll);
			},
			buf -> new ClientboundSealPacket(buf.readBlockPos(), buf.readEnum(Direction.class), ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf),
					buf.readByte(), buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

	public static ClientboundSealPacket of(ISealEntity seal) {
		return new ClientboundSealPacket(seal.getSealPos().pos, seal.getSealPos().face,
				Optional.ofNullable(seal.getSeal() != null ? seal.getSeal().getSealIcon() : null), seal.getColor(),
				seal.getArea() != null ? seal.getArea() : BlockPos.ZERO, seal.getSeal() instanceof ISealConfigArea, seal.isRedstoneSensitive(), false);
	}

	public static ClientboundSealPacket removed(SealPos pos) {
		return new ClientboundSealPacket(pos.pos, pos.face, Optional.empty(), (byte) 0, BlockPos.ZERO, false, false, false);
	}

	/** To everyone in the seal's dimension. */
	public static void broadcast(ServerLevel level, ClientboundSealPacket packet) {
		try {
			PacketDistributor.sendToPlayersInDimension(level, packet);
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Seal packet failed to send", e);
		}
	}

	/** Every seal in the player's current dimension, after telling the client to forget what it had. */
	public static void sendAll(ServerPlayer player) {
		try {
			PacketDistributor.sendToPlayer(player, new ClientboundSealPacket(BlockPos.ZERO, Direction.UP, Optional.empty(), (byte) 0, BlockPos.ZERO, false, false, true));
			for (ISealEntity seal : GolemHelper.getSealEntities(player.level().dimension()).values()) {
				PacketDistributor.sendToPlayer(player, of(seal));
			}
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Seal packets failed to send (no real client connection?)", e);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

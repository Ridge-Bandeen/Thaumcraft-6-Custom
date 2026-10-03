package thaumcraft.common.lib.network.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * The original's PacketFXBlockMist: a soft coloured mist rising over block {@code pos} (the Growth Lamp's green when a
 * plant it is tending changes). Drawn by {@code ClientFxHandlers#drawBlockMist}.
 */
public record PacketFXBlockMist(BlockPos pos, int color) implements CustomPacketPayload {

	public static final Type<PacketFXBlockMist> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_block_mist"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXBlockMist> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, PacketFXBlockMist::pos,
			ByteBufCodecs.INT, PacketFXBlockMist::color,
			PacketFXBlockMist::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void send(net.minecraft.server.level.ServerLevel level, BlockPos pos, int color) {
		try {
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(level, null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 32.0,
					new PacketFXBlockMist(pos, color));
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Block mist FX packet failed to send", e);
		}
	}
}

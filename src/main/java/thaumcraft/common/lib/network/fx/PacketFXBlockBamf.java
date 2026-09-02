package thaumcraft.common.lib.network.fx;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXBlockBamf} - a client-bound "poof" effect at a world position,
 * sent for {@link thaumcraft.common.entities.EntityFluxRift}'s own collapse (its only real caller in this
 * port, same as the original). Drops the original's optional directional {@code EnumFacing face} parameter -
 * every real call site in this port (like the original's own) always passes no direction - and its
 * sentinel "-9999 means random colour" convention, since the one real caller here always supplies a real
 * colour. Same shape as {@link PacketFXSlash}/{@link PacketFXScanSource} otherwise - see
 * {@code Thaumcraft#registerPayloads} for registration and
 * {@link thaumcraft.client.fx.ClientFxHandlers#handleBlockBamf} for the actual (plain vanilla particle/sound)
 * visual.
 */
public record PacketFXBlockBamf(double x, double y, double z, int color, boolean sound, boolean flair) implements CustomPacketPayload {

	public static final Type<PacketFXBlockBamf> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_block_bamf"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXBlockBamf> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, PacketFXBlockBamf::x,
			ByteBufCodecs.DOUBLE, PacketFXBlockBamf::y,
			ByteBufCodecs.DOUBLE, PacketFXBlockBamf::z,
			ByteBufCodecs.VAR_INT, PacketFXBlockBamf::color,
			ByteBufCodecs.BOOL, PacketFXBlockBamf::sound,
			ByteBufCodecs.BOOL, PacketFXBlockBamf::flair,
			PacketFXBlockBamf::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

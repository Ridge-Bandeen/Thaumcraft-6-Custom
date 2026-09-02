package thaumcraft.common.lib.network.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXScanSource} - a client-bound "scan for nearby ore veins and mark
 * them" notification, sent for {@link thaumcraft.common.lib.enchantment.EnumInfusionEnchantment#SOUNDING}'s
 * right-click effect. Same shape as {@link PacketFXSlash} (a plain data record; see that class's own doc
 * comment and {@code Thaumcraft#registerPayloads} for the registration side) - the actual scan (reading
 * nearby block state, grouping connected ore blocks, picking a colour per group) all happens client-side in
 * {@link thaumcraft.client.fx.ClientFxHandlers#handleScanSource}, exactly like the original: this packet only
 * carries where to look and how far.
 */
public record PacketFXScanSource(long pos, int size) implements CustomPacketPayload {

	public PacketFXScanSource(BlockPos pos, int size) {
		this(pos.asLong(), size);
	}

	public BlockPos blockPos() {
		return BlockPos.of(pos);
	}

	public static final Type<PacketFXScanSource> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_scan_source"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXScanSource> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, PacketFXScanSource::pos,
			ByteBufCodecs.VAR_INT, PacketFXScanSource::size,
			PacketFXScanSource::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

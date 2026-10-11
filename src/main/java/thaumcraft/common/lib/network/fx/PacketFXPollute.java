package thaumcraft.common.lib.network.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * The original's {@code PacketFXPollute}: flux smoke rising where the aura was polluted with its effect shown (a spilled
 * jar, a broken essentia device, a venting furnace...), one puff per point of flux up to 40. As the original, the amount
 * travels as a byte, so very large amounts wrap round and show nothing - see
 * {@link thaumcraft.client.fx.ClientFxHandlers#handlePollute}.
 */
public record PacketFXPollute(BlockPos pos, int amount) implements CustomPacketPayload {

	public static final Type<PacketFXPollute> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_pollute"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXPollute> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, PacketFXPollute::pos,
			ByteBufCodecs.BYTE.map(Byte::intValue, Integer::byteValue), PacketFXPollute::amount,
			PacketFXPollute::new);

	/** As the original's constructor: anything between 0 and 1 counts as 1, then it's cut to a byte. */
	public static PacketFXPollute of(BlockPos pos, float amount) {
		if (amount < 1.0f && amount > 0.0f) amount = 1.0f;
		return new PacketFXPollute(pos.immutable(), (byte) amount);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

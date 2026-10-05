package thaumcraft.common.lib.network.fx;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXSlash} - a client-bound "draw a slash effect between two
 * entities" notification, sent for {@link thaumcraft.common.lib.enchantment.EnumInfusionEnchantment#ARCING}'s
 * cleave hits. First real packet this port has needed - see {@code Thaumcraft#registerPayloads} for the
 * registration side and {@link thaumcraft.client.fx.ClientFxHandlers#handleSlash} for the client visual, the
 * original's slash sprite ({@link thaumcraft.client.fx.particles.FXPlane}, {@code FXDispatcher.drawSlash}).
 *
 * Carries entity ids (not positions) like the original, resolved back to live entities client-side - the
 * source/target may have moved between the server tick that sent this and the client tick that receives it,
 * and resolving fresh each time (rather than baking in a position) keeps the effect anchored to where the
 * entities actually are on the client.
 */
public record PacketFXSlash(int source, int target) implements CustomPacketPayload {

	public static final Type<PacketFXSlash> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_slash"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXSlash> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PacketFXSlash::source,
			ByteBufCodecs.VAR_INT, PacketFXSlash::target,
			PacketFXSlash::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

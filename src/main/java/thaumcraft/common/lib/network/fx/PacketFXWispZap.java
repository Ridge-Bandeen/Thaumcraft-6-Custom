package thaumcraft.common.lib.network.fx;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXWispZap} - a client-bound arc-lightning zap between {@link
 * thaumcraft.common.entities.monster.EntityWisp} and its current target, restored 2026-09-15 now that
 * {@link thaumcraft.client.fx.beams.FXBolt} is real (this port had previously reused the more generic
 * {@link PacketFXSlash}/{@code ClientFxHandlers#handleSlash} pair as a documented stand-in - see
 * {@code EntityWisp}'s own doc comment history - since standing up a whole second packet class wasn't worth
 * it while there was no real bolt renderer to show anyway).
 *
 * Carries entity ids (not positions), same real convention {@link PacketFXSlash} already established -
 * resolved back to live entities client-side so the effect stays anchored to wherever they've actually moved
 * to by the time the packet arrives, matching the true original's own real shape exactly. The wisp's own
 * real per-aspect colour (read off the source entity client-side, matching the true original's own real
 * {@code Aspect.getAspect(wisp.getType()).getColor()} lookup) is resolved in {@link
 * thaumcraft.client.fx.ClientFxHandlers#handleWispZap}, not carried in this payload.
 */
public record PacketFXWispZap(int source, int target) implements CustomPacketPayload {

	public static final Type<PacketFXWispZap> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_wisp_zap"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXWispZap> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PacketFXWispZap::source,
			ByteBufCodecs.VAR_INT, PacketFXWispZap::target,
			PacketFXWispZap::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

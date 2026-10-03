package thaumcraft.common.lib.network.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXInfusionSource}: a component on {@code source} has started flowing into the
 * matrix at {@code target}. The client registers a stream in the matrix's {@code sourceFX} map
 * ({@link thaumcraft.client.fx.ClientFxHandlers#handleInfusionSource}), which trickles particles for 60 ticks while
 * the matrix's 5 cycles draw the item off. (The original's XP-cost variant, a player-to-altar stream, was dead code:
 * its recipe type was always 0.)
 *
 * <p>Carries the real consumed {@link ItemStack} directly (not just its registry id) so the client can tell a
 * real {@code BlockItem} (draws the real block's own particle icon, {@link
 * thaumcraft.client.fx.particles.FXBoreParticles}'s own already-real block constructor) from a plain item
 * (draws the real item's own particle icon instead, a new constructor on that same class).
 */
public record PacketFXInfusionSource(BlockPos source, BlockPos target, ItemStack stack) implements CustomPacketPayload {

	public static final Type<PacketFXInfusionSource> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_infusion_source"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXInfusionSource> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, PacketFXInfusionSource::source,
			BlockPos.STREAM_CODEC, PacketFXInfusionSource::target,
			ItemStack.STREAM_CODEC, PacketFXInfusionSource::stack,
			PacketFXInfusionSource::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package thaumcraft.common.lib.network.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXInfusionSource} - a client-bound "this pedestal's own item is
 * being consumed toward the infusion altar's own current craft" notification, restored 2026-09-17 alongside
 * {@link thaumcraft.client.fx.ClientFxHandlers#handleInfusionSource}. Real, disclosed simplification: the
 * true original's own real client-side tracking used a persistent, per-tile {@code SourceFX} map with its own
 * real countdown (particles trickled from the pedestal for several real ticks BEFORE the item actually
 * vanished) - this port's own already-simplified {@code TileInfusionMatrix#craftCycle} consumes a matching
 * pedestal's own item in one real step, not a multi-tick countdown, so this fires one real short burst of the
 * same real particles at the moment of consumption instead of a real persistent multi-tick stream - same real
 * visual language (something flies from the pedestal toward the altar), simpler real trigger shape to match
 * this port's own already-simpler real crafting flow. The true original's own real XP-cost recipe branch
 * ({@code drawInfusionParticles4}, a player→altar variant) isn't ported either - {@code recipeXP}/
 * {@code recipeType} don't exist in this port's own real crafting model at all (a real, separate,
 * already-existing simplification, not new here).
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

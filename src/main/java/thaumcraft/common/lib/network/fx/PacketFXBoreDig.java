package thaumcraft.common.lib.network.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXBoreDig} - a client-bound "spawn digging FX at this block, homing
 * toward this entity" notification, restored 2026-09-15 for {@link
 * thaumcraft.common.entities.construct.EntityArcaneBore}'s real digging (see {@code
 * ClientFxHandlers#handleBoreDig} for the actual visual: real {@link
 * thaumcraft.client.fx.particles.FXBoreSparkle}/{@link thaumcraft.client.fx.particles.FXBoreParticles}
 * bursts, staggered over {@link #delay} real ticks via {@code ClientFxHandlers#addDelayed}).
 *
 * Carries the bore's own entity id (not a snapshotted position) so the spawned particles can home toward
 * wherever it's actually moved to by the time each staggered burst fires, matching the true original's own
 * real shape (and {@link PacketFXSlash}/{@link PacketFXWispZap}'s own already-established convention).
 * Drops the true original's own real block-metadata bitfield param entirely - that was only ever consumed by
 * {@code FXBoreParticles}' second, item-icon-based constructor, which isn't ported (see that class's own doc
 * comment); the real block-based constructor this port uses instead reads the block state directly off the
 * real client world at the given position, no metadata needed.
 */
public record PacketFXBoreDig(BlockPos pos, int boreId, int delay) implements CustomPacketPayload {

	public static final Type<PacketFXBoreDig> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_bore_dig"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXBoreDig> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, PacketFXBoreDig::pos,
			ByteBufCodecs.VAR_INT, PacketFXBoreDig::boreId,
			ByteBufCodecs.VAR_INT, PacketFXBoreDig::delay,
			PacketFXBoreDig::new);

	public static PacketFXBoreDig of(BlockPos pos, Entity bore, int delay) {
		return new PacketFXBoreDig(pos, bore.getId(), delay);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

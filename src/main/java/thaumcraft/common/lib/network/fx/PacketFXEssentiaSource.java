package thaumcraft.common.lib.network.fx;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXEssentiaSource} - a client-bound "essentia just moved from source
 * to target" notification, restored 2026-09-17 alongside {@link thaumcraft.client.fx.particles.FXEssentiaStream}
 * (see that class's own doc comment for the real modern rendering). Both endpoints are plain snapshotted
 * positions (same real shape {@link PacketFXBlockArc} already established, and the same real reason - packs
 * colour into a single RGB {@code int} since {@link StreamCodec#composite} only supports up to 6 real fields
 * and source + target + colour + extend would have blown that limit as raw floats).
 *
 * <p>The true original's own real constructor packed the target as a source-relative byte delta (a real
 * 1.12.2-era bandwidth trick, safe only because every real call site is a single adjacent-block hop) - not
 * reproduced here, since sending 2 real full positions costs nothing meaningful on a modern connection and
 * removes that real range restriction for good.
 */
public record PacketFXEssentiaSource(Vector3f source, Vector3f target, int color, int ext) implements CustomPacketPayload {

	public static final Type<PacketFXEssentiaSource> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_essentia_source"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXEssentiaSource> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VECTOR3F, PacketFXEssentiaSource::source,
			ByteBufCodecs.VECTOR3F, PacketFXEssentiaSource::target,
			ByteBufCodecs.VAR_INT, PacketFXEssentiaSource::color,
			ByteBufCodecs.VAR_INT, PacketFXEssentiaSource::ext,
			PacketFXEssentiaSource::new);

	/** Matches the true original's own real {@code PacketFXEssentiaSource(BlockPos, byte dx, dy, dz, color, ext)} -
	 * {@code from}/{@code to} are the 2 real endpoints directly, no delta-byte packing needed. */
	public static PacketFXEssentiaSource of(BlockPos from, BlockPos to, int color, int ext) {
		return new PacketFXEssentiaSource(
				new Vector3f(from.getX() + 0.5f, from.getY() + 0.5f, from.getZ() + 0.5f),
				new Vector3f(to.getX() + 0.5f, to.getY() + 0.5f, to.getZ() + 0.5f),
				color, ext);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

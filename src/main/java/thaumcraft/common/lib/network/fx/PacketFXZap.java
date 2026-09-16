package thaumcraft.common.lib.network.fx;

import org.joml.Vector3f;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXZap} - a client-bound arc-lightning zap between two real world
 * points with an explicit colour/width, restored 2026-09-15 alongside {@link thaumcraft.client.fx.beams.FXBolt}.
 * Real caller: {@link thaumcraft.common.items.casters.foci.FocusMediumBolt}'s own cast-trail broadcast (the
 * Bolt wand focus). Positions are plain snapshotted {@link Vector3f}s (matching the true original's own real
 * shape - unlike {@link PacketFXWispZap}, this one never needs to resolve a live entity, both endpoints are
 * always plain world points already at cast time).
 */
public record PacketFXZap(Vector3f source, Vector3f target, int color, float width) implements CustomPacketPayload {

	public static final Type<PacketFXZap> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_zap"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXZap> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VECTOR3F, PacketFXZap::source,
			ByteBufCodecs.VECTOR3F, PacketFXZap::target,
			ByteBufCodecs.VAR_INT, PacketFXZap::color,
			ByteBufCodecs.FLOAT, PacketFXZap::width,
			PacketFXZap::new);

	public static PacketFXZap of(Vec3 source, Vec3 target, int color, float width) {
		return new PacketFXZap(new Vector3f((float) source.x, (float) source.y, (float) source.z),
				new Vector3f((float) target.x, (float) target.y, (float) target.z), color, width);
	}

	public float red() {
		return ((color >> 16) & 0xFF) / 255.0f;
	}

	public float green() {
		return ((color >> 8) & 0xFF) / 255.0f;
	}

	public float blue() {
		return (color & 0xFF) / 255.0f;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

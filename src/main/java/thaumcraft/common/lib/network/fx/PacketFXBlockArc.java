package thaumcraft.common.lib.network.fx;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXBlockArc} - a client-bound arc-lightning flash between two real
 * points, restored 2026-09-15 alongside {@link thaumcraft.client.fx.beams.FXArc} (see that class's own doc
 * comment for the real modern rendering). Both endpoints are plain snapshotted positions (matching the
 * original's own real shape exactly) - never a live entity reference, so no id-resolution/despawn race is
 * possible client-side. Packs colour into a single RGB {@code int} (same convention
 * {@link PacketFXBlockBamf} already established) since {@link StreamCodec#composite} only supports up to 6
 * real fields and this needs source + target + colour - 9 raw floats would have blown that limit.
 *
 * <p>The two real convenience factories below match the original's own two real constructors (entity-
 * anchored source, matching {@link thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater}/
 * {@link thaumcraft.common.entities.monster.boss.EntityEldritchWarden}'s own real call sites; block-anchored
 * source/target, matching {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix}'s own real instability-
 * event redirect feedback) - see {@code Thaumcraft#registerPayloads} for registration and
 * {@link thaumcraft.client.fx.ClientFxHandlers#handleBlockArc} for the actual visual.
 */
public record PacketFXBlockArc(Vector3f source, Vector3f target, int color) implements CustomPacketPayload {

	public static final Type<PacketFXBlockArc> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_block_arc"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXBlockArc> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VECTOR3F, PacketFXBlockArc::source,
			ByteBufCodecs.VECTOR3F, PacketFXBlockArc::target,
			ByteBufCodecs.VAR_INT, PacketFXBlockArc::color,
			PacketFXBlockArc::new);

	/** Matches the true original's own {@code PacketFXBlockArc(BlockPos pos, Entity source, r, g, b)} -
	 * arcs from {@code pos + 0.5} to the source entity's own real centre. */
	public static PacketFXBlockArc of(BlockPos pos, Entity source, float r, float g, float b) {
		return new PacketFXBlockArc(
				new Vector3f(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f),
				new Vector3f((float) source.getX(), (float) (source.getBoundingBox().minY + source.getBbHeight() / 2.0f), (float) source.getZ()),
				packColor(r, g, b));
	}

	/** Matches the true original's own {@code PacketFXBlockArc(BlockPos pos, BlockPos pos2, r, g, b)} -
	 * arcs between the real centres of two blocks. */
	public static PacketFXBlockArc of(BlockPos pos, BlockPos pos2, float r, float g, float b) {
		return new PacketFXBlockArc(
				new Vector3f(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f),
				new Vector3f(pos2.getX() + 0.5f, pos2.getY() + 0.5f, pos2.getZ() + 0.5f),
				packColor(r, g, b));
	}

	private static int packColor(float r, float g, float b) {
		return ((int) (r * 255.0f) << 16) | ((int) (g * 255.0f) << 8) | (int) (b * 255.0f);
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

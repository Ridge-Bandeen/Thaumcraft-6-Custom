package thaumcraft.common.lib.network.fx;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Real client-bound "a falling taint block just landed" notification, restored 2026-09-24 alongside {@link
 * thaumcraft.common.blocks.world.taint.BlockTaint#onLand}. Replaces the true original's own real
 * {@code EntityFallingTaint}'s own real client-only {@code taintLandFX} burst - this port already replaced
 * that whole custom-entity mechanic with vanilla's own real {@link net.minecraft.world.entity.item.FallingBlockEntity},
 * so the real landing moment now comes from vanilla's own real {@link net.minecraft.world.level.block.Fallable#onLand}
 * hook (fired directly from {@code FallingBlockEntity#tick()} the instant it successfully places itself back
 * down) instead of a custom entity's own client-side {@code onUpdate} branch - a real, genuine architectural
 * fix per the user's own explicit direction (2026-09-24), not a custom {@code FallingBlockEntity} subclass.
 *
 * <p>Carries the landed position as doubles (not a plain {@code BlockPos}) to match the true original's own
 * real continuous-position particle scatter (jittered around the entity's own real bounding-box center, not
 * snapped to the block grid).
 */
public record PacketFXTaintLand(double x, double y, double z) implements CustomPacketPayload {

	public static final Type<PacketFXTaintLand> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_taint_land"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXTaintLand> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, PacketFXTaintLand::x,
			ByteBufCodecs.DOUBLE, PacketFXTaintLand::y,
			ByteBufCodecs.DOUBLE, PacketFXTaintLand::z,
			PacketFXTaintLand::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

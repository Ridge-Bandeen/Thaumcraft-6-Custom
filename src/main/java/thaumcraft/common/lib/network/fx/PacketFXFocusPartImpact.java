package thaumcraft.common.lib.network.fx;

import java.util.List;

import org.joml.Vector3f;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketFXFocusPartImpact} - a client-bound "this one real focus effect
 * just hit something" broadcast, restored 2026-09-16, sent directly by each real single-target-damage
 * {@link thaumcraft.api.casters.FocusEffect} subclass's own {@code execute()} (Air/Break/Earth/Fire/Flux/
 * Frost/Heal - see {@code ClientFxHandlers#handleFocusPartImpact}), distinct from {@link PacketFXFocusEffect}
 * (which renders every real effect in the whole cast package at once, sent once per medium-level cast by
 * {@link thaumcraft.common.items.casters.foci.FocusMediumTouch} itself). Real, faithfully-reproduced true
 * original quirk: real random motion jitter is generated fresh client-side on receipt (scale 0.15), not
 * carried in the payload - matches the true original's own real shape exactly.
 *
 * <p>Real, documented cut: the true original's own sibling {@code PacketFXFocusPartImpactBurst} (same real
 * shape, wider 0.4 motion jitter) is confirmed dead code - zero real call sites anywhere in the true
 * original itself - so it isn't ported.
 */
public record PacketFXFocusPartImpact(Vector3f pos, List<String> parts) implements CustomPacketPayload {

	public static final Type<PacketFXFocusPartImpact> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_focus_part_impact"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXFocusPartImpact> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VECTOR3F, PacketFXFocusPartImpact::pos,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PacketFXFocusPartImpact::parts,
			PacketFXFocusPartImpact::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

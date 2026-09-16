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
 * Replaces the old (1.12.2) {@code PacketFXFocusEffect} - a client-bound "render every real focus effect's
 * own particle FX at this point" broadcast, restored 2026-09-16 for {@link
 * thaumcraft.common.items.casters.foci.FocusMediumTouch}'s own real cast-impact dispatch (every real {@link
 * thaumcraft.api.casters.FocusEffect} node in the cast package gets its own real {@code renderParticleFX}
 * called, not just one - see {@code ClientFxHandlers#handleFocusEffect}). Distinct from {@link
 * PacketFXFocusPartImpact}, which each individual real effect subclass sends for its own real single-effect
 * impact instead - see that class's own doc comment for why both are real and needed.
 *
 * Carries real element keys (not resolved {@code FocusEffect} instances - the real client-side {@link
 * thaumcraft.api.casters.FocusEngine#getElement} registry lookup resolves them) joined the same real way the
 * true original did.
 */
public record PacketFXFocusEffect(Vector3f pos, Vector3f motion, List<String> parts) implements CustomPacketPayload {

	public static final Type<PacketFXFocusEffect> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_focus_effect"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXFocusEffect> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VECTOR3F, PacketFXFocusEffect::pos,
			ByteBufCodecs.VECTOR3F, PacketFXFocusEffect::motion,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PacketFXFocusEffect::parts,
			PacketFXFocusEffect::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

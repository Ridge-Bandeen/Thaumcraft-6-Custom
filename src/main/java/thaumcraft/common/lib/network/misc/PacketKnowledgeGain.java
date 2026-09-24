package thaumcraft.common.lib.network.misc;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code PacketKnowledgeGain} - a client-bound "you gained a point of knowledge"
 * notification, restored 2026-09-24 alongside {@link thaumcraft.client.gui.KnowledgeGainOverlay}. The true
 * original's own real dispatch fires one of these PER POINT actually gained (see {@code
 * ThaumcraftInternalMethodHandler#addKnowledge}'s own doc comment for why - the true original's own real
 * {@code ResearchManager#addKnowledge} sends one packet per point of real post-clamp delta, not one packet per
 * call), so a single {@code addKnowledge(..., 5)} call can - and should - fire 5 of these, each becoming its
 * own real independently-animated flying icon.
 *
 * <p>{@code category} is the empty string for a knowledge type with no real per-category breakdown (matches
 * the true original's own real {@code EnumKnowledgeType#hasFields()} gate and its own real "" sentinel for
 * "no category" - {@link net.minecraft.world.item.ItemStack#STREAM_CODEC}-style optional wrapping wasn't used
 * here specifically to mirror that real sentinel convention).
 */
public record PacketKnowledgeGain(byte knowledgeType, String category) implements CustomPacketPayload {

	public static final Type<PacketKnowledgeGain> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "knowledge_gain"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketKnowledgeGain> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, PacketKnowledgeGain::knowledgeType,
			ByteBufCodecs.STRING_UTF8, PacketKnowledgeGain::category,
			PacketKnowledgeGain::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

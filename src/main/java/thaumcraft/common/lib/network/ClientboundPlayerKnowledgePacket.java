package thaumcraft.common.lib.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Server->client sync for the viewing player's own {@link thaumcraft.api.capabilities.IPlayerKnowledge} -
 * the real counterpart {@link thaumcraft.common.lib.research.PlayerKnowledge#sync}'s own doc comment flagged
 * as still missing. Same "reuse the existing NBT shape rather than inventing a parallel one" precedent
 * {@link ClientboundResearchDataPacket} already used: carries the raw {@code CompoundTag} from {@code
 * PlayerKnowledge#serializeNBT} unchanged. Sent by {@link thaumcraft.common.container.ThaumonomiconMenu} on
 * open and after every research-progressing button click, mirroring that class's own real per-action sync.
 */
public record ClientboundPlayerKnowledgePacket(CompoundTag data) implements CustomPacketPayload {

	public static final Type<ClientboundPlayerKnowledgePacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "player_knowledge"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlayerKnowledgePacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.COMPOUND_TAG, ClientboundPlayerKnowledgePacket::data,
			ClientboundPlayerKnowledgePacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

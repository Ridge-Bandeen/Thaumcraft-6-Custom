package thaumcraft.common.lib.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Server->client sync for {@link thaumcraft.api.research.theorycraft.ResearchTableData} - the real
 * counterpart {@code ResearchTableMenu}'s own doc comment flagged as still missing. {@code data} (drawn
 * card choices, category progress, remaining inspiration) has no {@code BlockEntity}-level sync of its own
 * (see {@code TileResearchTable}'s doc comment for why - this is "keep an open GUI in sync with every
 * action", not generic chunk sync), so {@link thaumcraft.common.container.ResearchTableMenu} sends this
 * directly to the viewing player after every action that changes it (including the initial menu open, so a
 * re-opened table shows its real current state). Carries the raw {@code CompoundTag} from
 * {@code ResearchTableData#serialize()}/{@code deserialize} unchanged - same "reuse the existing NBT shape
 * rather than inventing a parallel one" precedent {@code ServerboundSaveFocusGraphPacket} already used.
 *
 * {@code pos} identifies which table this is for - not strictly needed today (only one research table menu
 * can ever be open on a given client at a time) but costs nothing and keeps the payload self-describing.
 */
public record ClientboundResearchDataPacket(BlockPos pos, CompoundTag data) implements CustomPacketPayload {

	public static final Type<ClientboundResearchDataPacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "research_data"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundResearchDataPacket> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ClientboundResearchDataPacket::pos,
			ByteBufCodecs.COMPOUND_TAG, ClientboundResearchDataPacket::data,
			ClientboundResearchDataPacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

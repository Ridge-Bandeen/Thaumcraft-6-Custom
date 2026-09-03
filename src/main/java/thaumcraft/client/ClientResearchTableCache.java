package thaumcraft.client;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.common.lib.network.ClientboundResearchDataPacket;

/**
 * Client-side cache of the last {@link ClientboundResearchDataPacket} received - see that class's own doc
 * comment for why this exists. Only one research table menu can ever be open on a given client at a time,
 * so a single latest-value cache (not a per-position map) is all a real {@code GuiResearchTable} needs.
 */
public final class ClientResearchTableCache {

	@Nullable
	private static BlockPos pos;
	@Nullable
	private static ResearchTableData data;

	private ClientResearchTableCache() {
	}

	public static void handle(ClientboundResearchDataPacket payload) {
		pos = payload.pos();
		CompoundTag tag = payload.data();
		if (tag.isEmpty()) {
			data = null;
			return;
		}
		ResearchTableData d = new ResearchTableData((net.minecraft.world.level.block.entity.BlockEntity) null);
		d.deserialize(tag);
		data = d;
	}

	/** The most recently synced theory, or {@code null} if none is in progress - see this class's own doc
	 * comment for why no position matching is needed (only one research table menu can ever be open on a
	 * given client at a time). */
	@Nullable
	public static ResearchTableData getData() {
		return data;
	}

	public static void clear() {
		pos = null;
		data = null;
	}
}

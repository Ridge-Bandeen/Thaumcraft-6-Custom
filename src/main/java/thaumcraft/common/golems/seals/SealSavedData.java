package thaumcraft.common.golems.seals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.seals.ISealEntity;

/**
 * Real persistence for placed seals - the original never had this problem (its own
 * {@code ConcurrentHashMap} field was, in the actual shipped 1.12.2 mod, backed by a separate save/load
 * path this port never had visibility into as source; {@code SealHandler}'s chunk-5 commit shipped without
 * it as a documented gap). Same real {@link SavedData} approach {@link thaumcraft.common.world.aura.AuraWorld}
 * already established for aura data: one instance per dimension, registered with that level's
 * {@code DimensionDataStorage} from {@link SealHandler#loadForLevel} (called from
 * {@code Thaumcraft.onLevelLoad}, same hook {@code AuraHandler.loadForLevel} uses).
 *
 * Unlike {@code AuraWorld} (which IS the live mutable state), {@link thaumcraft.api.golems.GolemHelper}'s
 * own per-dimension seal map stays the single live source of truth - this class only round-trips it to/from
 * disk: {@link #load} deserializes into a plain loaded-list ({@link SealHandler#loadForLevel} copies those
 * into {@code GolemHelper}'s real map once the dimension is known), and {@link #save} reads straight out of
 * {@code GolemHelper.getSealEntities(dimension)} at save time - the {@link #dimension} field is set once,
 * right after this instance is first created/loaded, specifically so {@code save()} (which vanilla calls
 * with no dimension context of its own) knows which live map to read.
 */
public class SealSavedData extends SavedData {

	private static final String ID = "thaumcraft_seals";

	private final List<ISealEntity> loaded = new ArrayList<>();
	private ResourceKey<Level> dimension;

	public static final Factory<SealSavedData> FACTORY = new Factory<>(SealSavedData::new, SealSavedData::load, null);

	public static String id() {
		return ID;
	}

	void setDimension(ResourceKey<Level> dimension) {
		this.dimension = dimension;
	}

	/** The seals deserialized from disk, not yet merged into {@link GolemHelper}'s live map - real,
	 * inspectable state (used by {@link SealHandler#loadForLevel}, and directly by tests exercising the
	 * real save/load round trip without simulating a full server restart). */
	public List<ISealEntity> getLoaded() {
		return loaded;
	}

	public static SealSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
		SealSavedData data = new SealSavedData();
		ListTag list = tag.getList("seals", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			SealEntity entity = new SealEntity();
			entity.readNBT(list.getCompound(i));
			if (entity.getSeal() != null) {
				data.loaded.add(entity);
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		if (dimension != null) {
			for (ISealEntity entity : GolemHelper.getSealEntities(dimension).values()) {
				list.add(entity.writeNBT());
			}
		}
		tag.put("seals", list);
		return tag;
	}
}

package thaumcraft.common.world.aura;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Replaces the old (1.12.2) {@code AuraWorld}. Chunks are keyed by the packed {@code ChunkPos.asLong()}
 * value instead of the original's small {@code PosXY} helper class - the same idea, just using the
 * standard modern way to key a chunk-position map instead of porting a one-off utility for it.
 *
 * Now a real {@link SavedData} (see {@link AuraHandler}'s class doc for the persistence approach) - this
 * is the same in-memory object the background {@link AuraThread} mutates directly; the only
 * {@code DimensionDataStorage} interaction happens on the main thread, in response to
 * {@code LevelEvent.Load}/{@code Save} (see {@code Thaumcraft.onLevelLoad}/{@code onLevelSave}), so the
 * diffusion thread itself never touches Minecraft's save infrastructure.
 */
public class AuraWorld extends SavedData
{
    private static final String ID = "thaumcraft_aura";

    ConcurrentHashMap<Long, AuraChunk> auraChunks = new ConcurrentHashMap<>();

    public static final Factory<AuraWorld> FACTORY = new Factory<>(AuraWorld::new, AuraWorld::load, null);

    public static String id() {
        return ID;
    }

    public ConcurrentHashMap<Long, AuraChunk> getAuraChunks() {
        return auraChunks;
    }

    public AuraChunk getAuraChunkAt(int x, int z) {
        return auraChunks.get(ChunkPos.asLong(x, z));
    }

    public static AuraWorld load(CompoundTag tag, HolderLookup.Provider registries) {
        AuraWorld world = new AuraWorld();
        ListTag list = tag.getList("chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ChunkPos pos = new ChunkPos(c.getInt("x"), c.getInt("z"));
            AuraChunk chunk = new AuraChunk(pos, (short) c.getInt("base"), c.getFloat("vis"), c.getFloat("flux"));
            world.auraChunks.put(ChunkPos.asLong(pos.x, pos.z), chunk);
        }
        return world;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (AuraChunk chunk : auraChunks.values()) {
            CompoundTag c = new CompoundTag();
            c.putInt("x", chunk.getLoc().x);
            c.putInt("z", chunk.getLoc().z);
            c.putInt("base", chunk.getBase());
            c.putFloat("vis", chunk.getVis());
            c.putFloat("flux", chunk.getFlux());
            list.add(c);
        }
        tag.put("chunks", list);
        return tag;
    }
}

package thaumcraft.common.world.aura;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Replaces the old (1.12.2) {@code AuraWorld}. Chunks are keyed by the packed {@code ChunkPos.asLong()}
 * value instead of the original's small {@code PosXY} helper class - the same idea, just using the
 * standard modern way to key a chunk-position map instead of porting a one-off utility for it.
 */
public class AuraWorld
{
    ResourceKey<Level> dim;
    ConcurrentHashMap<Long, AuraChunk> auraChunks;

    public AuraWorld(ResourceKey<Level> dim) {
        auraChunks = new ConcurrentHashMap<>();
        this.dim = dim;
    }

    public ConcurrentHashMap<Long, AuraChunk> getAuraChunks() {
        return auraChunks;
    }

    public AuraChunk getAuraChunkAt(int x, int z) {
        return auraChunks.get(net.minecraft.world.level.ChunkPos.asLong(x, z));
    }
}

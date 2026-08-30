package thaumcraft.common.world.aura;
import net.minecraft.world.level.ChunkPos;

/**
 * Replaces the old (1.12.2) {@code AuraChunk}. Dropped the {@code WeakReference<Chunk>}/{@code isModified()}
 * bookkeeping that supported NBT persistence - this version doesn't persist aura state across a server
 * restart yet (see {@link AuraHandler}'s class doc).
 */
public class AuraChunk
{
    ChunkPos loc;
    short base;
    float vis;
    float flux;

    public AuraChunk(ChunkPos loc, short base, float vis, float flux) {
        this.loc = loc;
        this.base = base;
        this.vis = vis;
        this.flux = flux;
    }

    public short getBase() {
        return base;
    }

    public void setBase(short base) {
        this.base = base;
    }

    public float getVis() {
        return vis;
    }

    public void setVis(float vis) {
        this.vis = Math.min(32766.0f, Math.max(0.0f, vis));
    }

    public float getFlux() {
        return flux;
    }

    public void setFlux(float flux) {
        this.flux = Math.min(32766.0f, Math.max(0.0f, flux));
    }

    public ChunkPos getLoc() {
        return loc;
    }
}

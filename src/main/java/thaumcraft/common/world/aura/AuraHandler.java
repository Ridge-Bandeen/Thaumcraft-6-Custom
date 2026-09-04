package thaumcraft.common.world.aura;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code AuraHandler}. This is the real vis/flux simulation the rest of the port
 * had been treating as a black box (see {@code thaumcraft.api.aura.AuraHelper}'s TODO, and
 * {@code GolemHelper}'s equivalent one) - {@link thaumcraft.common.items.casters.ItemCaster} and
 * {@link thaumcraft.common.container.ArcaneWorkbenchMenu} now draw real vis from here instead of a fixed
 * pool / no check at all.
 *
 * Persistence: each dimension's {@link AuraWorld} is a real {@code SavedData} now, registered with that
 * level's {@code DimensionDataStorage} via {@code Thaumcraft.onLevelLoad} (main thread). From there,
 * vanilla's own per-level save routine already calls {@code DimensionDataStorage#save()} for every
 * registered {@code SavedData} that's dirty - no extra save-side hook needed, just marking the object
 * dirty (a plain boolean flip) at each real mutation point (see {@link #markDirty}), including from the
 * background {@link AuraThread}. That thread otherwise never touches
 * {@code DimensionDataStorage}/file I/O directly, only the in-memory {@link AuraChunk} objects (same as
 * before). {@link #getAuraChunk} still falls back to creating a bare {@link AuraWorld} if a dimension is
 * touched before its {@code LevelEvent.Load} fires (e.g. very first tick) - {@code computeIfAbsent} means
 * that fallback is never allowed to clobber one already loaded from disk.
 *
 * High flux really does spawn a real {@code EntityFluxRift} now, via {@link #onHighFlux} - {@link
 * AuraThread} calls it from the background simulation thread, which hands the actual spawn off to the main
 * server thread via {@code MinecraftServer#execute} rather than touching the level off-thread.
 *
 * Still deliberately simplified versus the original, worth revisiting later:
 * <ul>
 * <li>No per-biome variation on generation - every chunk seeds with the same flat baseline (+ noise). The
 * original weighted this by {@code BiomeDictionary} tags (a whole extra file, {@code BiomeHandler}) that
 * isn't ported.</li>
 * <li>Chunks are created lazily on first read/write instead of via a world-gen hook, which sidesteps
 * needing to port that wiring for now - functionally equivalent from a "does this block have vis"
 * standpoint, just not primed ahead of time.</li>
 * </ul>
 */
public class AuraHandler
{
    public static final int AURA_CEILING = 500;
    private static final float BASELINE = 1.0f;

    private static final ConcurrentHashMap<ResourceKey<Level>, AuraWorld> auras = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceKey<Level>, AuraThread> auraThreads = new ConcurrentHashMap<>();
    private static final Random rand = new Random();

    /** Set from Thaumcraft's ServerStartingEvent/ServerStoppingEvent handlers. */
    public static MinecraftServer server;

    public static AuraWorld getAuraWorld(ResourceKey<Level> dim) {
        return auras.get(dim);
    }

    /** Called from Thaumcraft.onLevelLoad (main thread) - loads/creates this dimension's saved aura data. */
    public static void loadForLevel(ServerLevel level) {
        AuraWorld world = level.getDataStorage().computeIfAbsent(AuraWorld.FACTORY, AuraWorld.id());
        auras.put(level.dimension(), world);
        ensureThreadStarted(level.dimension());
    }

    public static AuraChunk getAuraChunk(ResourceKey<Level> dim, int x, int z) {
        boolean isNewDimension = !auras.containsKey(dim);
        AuraWorld world = auras.computeIfAbsent(dim, key -> new AuraWorld());
        if (isNewDimension) ensureThreadStarted(dim);
        return world.getAuraChunks().computeIfAbsent(ChunkPos.asLong(x, z), key -> {
            world.setDirty();
            return generateAuraChunk(new ChunkPos(x, z));
        });
    }

    private static AuraChunk generateAuraChunk(ChunkPos pos) {
        float noise = (float) (1.0 + rand.nextGaussian() * 0.1);
        short base = (short) Math.max(0, Math.min(AURA_CEILING, (int) (BASELINE * AURA_CEILING * noise)));
        return new AuraChunk(pos, base, base, 0.0f);
    }

    public static void ensureThreadStarted(ResourceKey<Level> dim) {
        auraThreads.computeIfAbsent(dim, key -> {
            AuraThread thread = new AuraThread(dim);
            Thread t = new Thread(thread, "Thaumcraft Aura " + dim.location());
            t.setDaemon(true);
            t.start();
            return thread;
        });
    }

    public static void stopAllThreads() {
        for (AuraThread thread : auraThreads.values()) {
            thread.stop();
        }
        auraThreads.clear();
        auras.clear();
    }

    public static float getTotalAura(Level level, BlockPos pos) {
        AuraChunk ac = getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        return ac.getVis() + ac.getFlux();
    }

    public static float getVis(Level level, BlockPos pos) {
        return getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4).getVis();
    }

    public static float getFlux(Level level, BlockPos pos) {
        return getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4).getFlux();
    }

    public static int getAuraBase(Level level, BlockPos pos) {
        return getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4).getBase();
    }

    /** How flux-saturated this chunk currently is (flux / base), matching the original's own real formula
     * - used by {@link thaumcraft.common.blocks.world.taint.TaintHelper#spreadFibres} to scale taint's own
     * spread rate up in already-corrupted areas. */
    public static float getFluxSaturation(Level level, BlockPos pos) {
        AuraChunk ac = getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        return ac.getBase() > 0 ? ac.getFlux() / ac.getBase() : 0.0f;
    }

    public static boolean shouldPreserveAura(Level level, Player player, BlockPos pos) {
        int base = getAuraBase(level, pos);
        return base > 0
                && (player == null || thaumcraft.api.capabilities.ThaumcraftCapabilities.knowsResearchStrict(player, "AURAPRESERVE"))
                && getVis(level, pos) / base < 0.1;
    }

    public static void addVis(Level level, BlockPos pos, float amount) {
        if (amount < 0.0f) return;
        AuraChunk ac = getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        ac.setVis(ac.getVis() + amount);
        markDirty(level.dimension());
    }

    public static void addFlux(Level level, BlockPos pos, float amount) {
        if (amount < 0.0f) return;
        AuraChunk ac = getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        ac.setFlux(ac.getFlux() + amount);
        markDirty(level.dimension());
    }

    public static float drainVis(Level level, BlockPos pos, float amount, boolean simulate) {
        AuraChunk ac = getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        amount = Math.min(amount, ac.getVis());
        if (!simulate) {
            ac.setVis(ac.getVis() - amount);
            markDirty(level.dimension());
        }
        return amount;
    }

    public static float drainFlux(Level level, BlockPos pos, float amount, boolean simulate) {
        AuraChunk ac = getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        amount = Math.min(amount, ac.getFlux());
        if (!simulate) {
            ac.setFlux(ac.getFlux() - amount);
            markDirty(level.dimension());
        }
        return amount;
    }

    /** Total vis across the 3x3 chunk block centered on {@code pos} - what an Arcane Workbench Charger
     * (see {@code BlockArcaneWorkbenchCharger}) widens a workbench's vis draw to, in place of just its own
     * chunk. */
    public static float getWideVis(Level level, BlockPos pos) {
        float t = 0.0f;
        int sx = pos.getX() >> 4;
        int sz = pos.getZ() >> 4;
        for (int xx = -1; xx <= 1; xx++) {
            for (int zz = -1; zz <= 1; zz++) {
                AuraChunk ac = getAuraChunk(level.dimension(), sx + xx, sz + zz);
                if (ac != null) t += ac.getVis();
            }
        }
        return t;
    }

    /** Drains {@code amount} vis spread across the same 3x3 chunk block as {@link #getWideVis} - roughly a
     * ninth from each chunk per pass, cycling until satisfied or a chunk actually has nothing left,
     * matching the original's own round-robin drain loop (capped at 1000 attempts, same safety valve). */
    public static void drainWideVis(Level level, BlockPos pos, float amount) {
        float perChunk = Math.max(1.0f, amount / 9.0f);
        int attempts = 0;
        while (amount > 0.0f && attempts < 1000) {
            attempts++;
            for (int xx = -1; xx <= 1; xx++) {
                for (int zz = -1; zz <= 1; zz++) {
                    float take = Math.min(perChunk, amount);
                    amount -= drainVis(level, pos.offset(xx * 16, 0, zz * 16), take, false);
                    if (amount <= 0.0f) return;
                }
            }
        }
    }

    /** Cheap - just flips a boolean on the (already in-memory) AuraWorld, safe to call from any thread. */
    static void markDirty(ResourceKey<Level> dim) {
        AuraWorld world = auras.get(dim);
        if (world != null) world.setDirty();
    }

    /** {@link AuraThread}'s own real spawn trigger for {@code EntityFluxRift} (see that class's own doc
     * comment) - called from the background aura-simulation thread, so the actual spawn is handed off to
     * the main server thread via {@link MinecraftServer#execute} rather than touching the level here. */
    static void onHighFlux(ResourceKey<Level> dim, BlockPos pos) {
        MinecraftServer srv = server;
        if (srv == null) {
            return;
        }
        srv.execute(() -> {
            ServerLevel level = srv.getLevel(dim);
            if (level != null) {
                thaumcraft.common.entities.EntityFluxRift.createRift(level, pos);
            }
        });
    }
}

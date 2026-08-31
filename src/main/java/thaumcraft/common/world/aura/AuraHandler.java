package thaumcraft.common.world.aura;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
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
 * Deliberately simplified versus the original for now, all worth revisiting later:
 * <ul>
 * <li>No persistence - aura state lives only in memory and resets on server restart. The original stored
 * it in each chunk's own save data; that needs a proper {@code SavedData}/chunk-capability hookup this
 * pass didn't build.</li>
 * <li>No per-biome variation on generation - every chunk seeds with the same flat baseline (+ noise). The
 * original weighted this by {@code BiomeDictionary} tags (a whole extra file, {@code BiomeHandler}) that
 * isn't ported.</li>
 * <li>High flux no longer spawns a flux rift (that needs {@code EntityFluxRift}, not ported) - just logged.</li>
 * <li>Chunks are created lazily on first read/write instead of via a world-gen/chunk-load hook, which
 * sidesteps needing to port that wiring for now - functionally equivalent from a "does this block have
 * vis" standpoint, just not primed ahead of time.</li>
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

    public static AuraChunk getAuraChunk(ResourceKey<Level> dim, int x, int z) {
        boolean isNewDimension = !auras.containsKey(dim);
        AuraWorld world = auras.computeIfAbsent(dim, AuraWorld::new);
        if (isNewDimension) ensureThreadStarted(dim);
        return world.getAuraChunks().computeIfAbsent(ChunkPos.asLong(x, z), key -> generateAuraChunk(new ChunkPos(x, z)));
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
    }

    public static void addFlux(Level level, BlockPos pos, float amount) {
        if (amount < 0.0f) return;
        AuraChunk ac = getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        ac.setFlux(ac.getFlux() + amount);
    }

    public static float drainVis(Level level, BlockPos pos, float amount, boolean simulate) {
        AuraChunk ac = getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        amount = Math.min(amount, ac.getVis());
        if (!simulate) ac.setVis(ac.getVis() - amount);
        return amount;
    }

    public static float drainFlux(Level level, BlockPos pos, float amount, boolean simulate) {
        AuraChunk ac = getAuraChunk(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
        amount = Math.min(amount, ac.getFlux());
        if (!simulate) ac.setFlux(ac.getFlux() - amount);
        return amount;
    }

    static void onHighFlux(ResourceKey<Level> dim, BlockPos pos) {
        Thaumcraft.LOGGER.debug("Aura flux is critical at {} in {} (flux rift spawning not ported yet)", pos, dim.location());
    }
}

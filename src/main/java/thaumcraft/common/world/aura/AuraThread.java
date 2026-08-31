package thaumcraft.common.world.aura;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code AuraThread}. Same overall shape - a background thread per dimension,
 * ticking roughly once a second, diffusing vis/flux between neighbouring chunks and slowly regenerating
 * vis toward each chunk's base value. See {@link AuraHandler}'s class doc for what's simplified.
 */
public class AuraThread implements Runnable
{
    private final ResourceKey<Level> dim;
    private volatile boolean stop;
    private final Random rand = new Random();
    private float phaseVis;
    private float phaseFlux;
    private float phaseMax;
    private long lastWorldTime = -1;
    private final float[] phaseTable = { 0.25f, 0.15f, 0.1f, 0.05f, 0.0f, 0.05f, 0.1f, 0.15f };
    private final float[] maxTable = { 0.15f, 0.05f, 0.0f, -0.05f, -0.15f, -0.05f, 0.0f, 0.05f };

    public AuraThread(ResourceKey<Level> dim) {
        this.dim = dim;
    }

    @Override
    public void run() {
        Thaumcraft.LOGGER.info("Starting aura thread for {}", dim.location());
        while (!stop) {
            long startTime = System.currentTimeMillis();
            AuraWorld auraWorld = AuraHandler.getAuraWorld(dim);
            if (auraWorld == null) {
                break;
            }

            ServerLevel level = AuraHandler.server != null ? AuraHandler.server.getLevel(dim) : null;
            if (level != null && lastWorldTime != level.getDayTime()) {
                lastWorldTime = level.getDayTime();
                phaseVis = phaseTable[level.getMoonPhase()];
                phaseMax = 1.0f + maxTable[level.getMoonPhase()];
                phaseFlux = 0.25f - phaseVis;

                for (AuraChunk chunk : auraWorld.getAuraChunks().values()) {
                    processAuraChunk(auraWorld, chunk);
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;
            try {
                Thread.sleep(Math.max(1L, 1000L - executionTime));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        Thaumcraft.LOGGER.info("Stopping aura thread for {}", dim.location());
    }

    private void processAuraChunk(AuraWorld auraWorld, AuraChunk auraChunk) {
        List<Integer> directions = Arrays.asList(0, 1, 2, 3);
        Collections.shuffle(directions, rand);
        int x = auraChunk.getLoc().x;
        int z = auraChunk.getLoc().z;
        float base = auraChunk.getBase() * phaseMax;
        boolean dirty = false;
        float currentVis = auraChunk.getVis();
        float currentFlux = auraChunk.getFlux();
        AuraChunk neighbourVisChunk = null;
        AuraChunk neighbourFluxChunk = null;
        float lowestVis = Float.MAX_VALUE;
        float lowestFlux = Float.MAX_VALUE;

        for (int a : directions) {
            Direction dir = Direction.from2DDataValue(a);
            AuraChunk n = auraWorld.getAuraChunkAt(x + dir.getStepX(), z + dir.getStepZ());
            if (n != null) {
                if ((neighbourVisChunk == null || lowestVis > n.getVis()) && n.getVis() + n.getFlux() < n.getBase() * phaseMax) {
                    neighbourVisChunk = n;
                    lowestVis = n.getVis();
                }
                if (neighbourFluxChunk == null || lowestFlux > n.getFlux()) {
                    neighbourFluxChunk = n;
                    lowestFlux = n.getFlux();
                }
            }
        }

        if (neighbourVisChunk != null && lowestVis < currentVis && lowestVis / currentVis < 0.75) {
            float inc = Math.min(currentVis - lowestVis, 1.0f);
            currentVis -= inc;
            neighbourVisChunk.setVis(lowestVis + inc);
            dirty = true;
        }
        if (neighbourFluxChunk != null && currentFlux > Math.max(5.0f, auraChunk.getBase() / 10.0f) && lowestFlux < currentFlux / 1.75) {
            float inc = Math.min(currentFlux - lowestFlux, 1.0f);
            currentFlux -= inc;
            neighbourFluxChunk.setFlux(lowestFlux + inc);
            dirty = true;
        }
        if (currentVis + currentFlux < base) {
            currentVis += Math.min(base - (currentVis + currentFlux), phaseVis);
            dirty = true;
        } else if (currentVis > base * 1.25 && rand.nextFloat() < 0.1) {
            currentFlux += phaseFlux;
            currentVis -= phaseFlux;
            dirty = true;
        } else if (currentVis <= base * 0.1 && currentVis >= currentFlux && rand.nextFloat() < 0.1) {
            currentFlux += phaseFlux;
            dirty = true;
        }

        if (dirty) {
            auraChunk.setVis(currentVis);
            auraChunk.setFlux(currentFlux);
            AuraHandler.markDirty(dim);
        }

        if (currentFlux > base * 0.75 && rand.nextFloat() < currentFlux / 500.0f / 10.0f) {
            AuraHandler.onHighFlux(dim, auraChunk.getLoc().getWorldPosition());
        }
    }

    public void stop() {
        stop = true;
    }
}

package thaumcraft.common.world;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.world.biomes.BiomeHandler;

/**
 * Replaces the true original's own real primal-crystal-cluster placement - one real branch of {@code
 * ThaumcraftWorldGenerator#generateOres} (the {@code generateCrystals} block) never ported alongside the
 * cinnabar/quartz/amber ores it sat next to. Real, deliberate simplification: modern worldgen's own
 * {@code Feature}/placement-modifier split already covers "how many times per chunk, at what height, in
 * which biomes" (see {@code data/thaumcraft/worldgen/placed_feature/crystal_cluster.json}) - this class is
 * only the true original's own real per-attempt logic: pick one real {@link Aspect} for this whole vein
 * (matching the original's own real "one dice roll per vein, not per crystal" design, re-derived directly
 * from its own source rather than assumed), then scatter up to 9 real {@link BlockCrystal} seeds through a
 * real 3x3x3 volume around the chosen origin, each needing a real touching sturdy face to actually place
 * (reusing {@link BlockCrystal#findSturdyFacing}, the same real check natural in-game spreading already
 * uses - no separate/duplicated "is this rock" logic).
 *
 * <p>Real aspect selection, matching the true original's own real {@code random.nextInt(3) == 0} gate
 * exactly: two-thirds of the time this vein's aspect is a real uniformly-random pick among the 6 real
 * primal aspects (deliberately excluding {@link Aspect#FLUX} - the true original's own real
 * {@code random.nextInt(6)} bound already excluded it, since nothing in {@link BiomeHandler}'s own real
 * table ever leans Flux either); the other third of the time it's {@link BiomeHandler#getRandomAspect} for
 * the real biome at the origin, falling back to that same uniform 6-way pick if the biome matches nothing.
 */
public class CrystalClusterFeature extends Feature<NoneFeatureConfiguration> {

	/** The true original's own real {@code random.nextInt(6)} bound - the 6 real primal aspects a crystal
	 * vein can naturally lean toward, deliberately excluding {@link Aspect#FLUX}. */
	private static final Aspect[] PRIMAL_ASPECTS = { Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY };

	public CrystalClusterFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();

		Aspect aspect = random.nextInt(3) == 0 ? thaumcraft.api.world.WorldFlavour.get().randomAspect(level, origin, random) : null;
		if (aspect == null) aspect = PRIMAL_ASPECTS[random.nextInt(PRIMAL_ASPECTS.length)];

		var crystalBlock = thaumcraft.api.blocks.BlocksTC.crystals.get(aspect);
		if (crystalBlock == null) return false;

		boolean placedAny = false;
		for (int xx = -1; xx <= 1; xx++) {
			for (int yy = -1; yy <= 1; yy++) {
				for (int zz = -1; zz <= 1; zz++) {
					if (random.nextInt(3) == 0) continue;
					BlockPos target = origin.offset(xx, yy, zz);
					BlockState existing = level.getBlockState(target);
					if (!existing.isAir() && !existing.canBeReplaced()) continue;
					if (!existing.getFluidState().isEmpty()) continue;

					Direction facing = BlockCrystal.findSturdyFacing(level, target);
					if (facing == null) continue;

					int size = 1 + random.nextInt(3);
					level.setBlock(target, crystalBlock.get().defaultBlockState()
							.setValue(BlockCrystal.SIZE, size)
							.setValue(BlockCrystal.FACING, facing), 2);
					placedAny = true;
				}
			}
		}
		return placedAny;
	}
}

package thaumcraft.common.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import thaumcraft.api.blocks.BlocksTC;

/**
 * The original BiomeGenMagicalForest#decorate's first step: up to three tries at a random column 4-11 blocks into the
 * chunk, walking down from the surface (no lower than y 30) to the first grass block and turning it into ambient grass;
 * the first success ends it.
 */
public class AmbientGrassFeature extends Feature<NoneFeatureConfiguration> {

	public AmbientGrassFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos chunk = new BlockPos(context.origin().getX() & ~15, 0, context.origin().getZ() & ~15);
		for (int a = 0; a < 3; a++) {
			BlockPos column = chunk.offset(4 + random.nextInt(8), 0, 4 + random.nextInt(8));
			BlockPos p = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, column);
			while (p.getY() > 30 && !level.getBlockState(p).is(Blocks.GRASS_BLOCK)) {
				p = p.below();
			}
			if (level.getBlockState(p).is(Blocks.GRASS_BLOCK)) {
				level.setBlock(p, BlocksTC.grassAmbient.get().defaultBlockState(), 2);
				return true;
			}
		}
		return false;
	}
}

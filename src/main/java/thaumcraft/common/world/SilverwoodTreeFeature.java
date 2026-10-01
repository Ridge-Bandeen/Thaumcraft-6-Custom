package thaumcraft.common.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import thaumcraft.api.blocks.BlocksTC;

/**
 * The original's WorldGenSilverwoodTrees: a plus-shaped trunk five logs across, root logs flaring out at the base and
 * a second flare four below the top, and a round crown. Replaces a vanilla fancy-oak configuration the port used
 * before (a 1x1 trunk). Wild trees ({@code flowers}) scatter Shimmerleaf around their base, as the original's did.
 */
public class SilverwoodTreeFeature extends Feature<SilverwoodTreeFeature.Config> {

	public record Config(int minHeight, int randomHeight, boolean flowers) implements FeatureConfiguration {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.intRange(1, 64).fieldOf("min_height").forGetter(Config::minHeight),
				Codec.intRange(1, 64).fieldOf("random_height").forGetter(Config::randomHeight),
				Codec.BOOL.optionalFieldOf("flowers", false).forGetter(Config::flowers)
		).apply(i, Config::new));
	}

	public SilverwoodTreeFeature(Codec<Config> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<Config> context) {
		WorldGenLevel world = context.level();
		RandomSource random = context.random();
		BlockPos pos = context.origin();
		Config config = context.config();
		TreeGenHelper out = new TreeGenHelper(world);

		int height = random.nextInt(config.randomHeight()) + config.minHeight();
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		if (y < world.getMinBuildHeight() + 1 || y + height + 1 > world.getMaxBuildHeight()) return false;
		for (int i1 = y; i1 <= y + 1 + height; ++i1) {
			int spread = 1;
			if (i1 == y) spread = 0;
			if (i1 >= y + 1 + height - 2) spread = 3;
			for (int j1 = x - spread; j1 <= x + spread; ++j1) {
				for (int k1 = z - spread; k1 <= z + spread; ++k1) {
					BlockState state = out.get(new BlockPos(j1, i1, k1));
					if (state == null) return false;
					if (!isOpen(state) && i1 > y) return false;
				}
			}
		}
		BlockPos soil = pos.below();
		if (!isGrassOrDirt(world, soil)) return false;
		// 1.12's Block#onPlantGrow: grass (and farmland) under a growing tree becomes dirt
		BlockState soilState = world.getBlockState(soil);
		if (soilState.is(Blocks.GRASS_BLOCK) || soilState.is(Blocks.FARMLAND)) world.setBlock(soil, Blocks.DIRT.defaultBlockState(), 2);

		BlockState leaves = BlocksTC.leavesSilverwood.get().defaultBlockState();
		int start = y + height - 5;
		for (int end = y + height + 3 + random.nextInt(3), k2 = start; k2 <= end; ++k2) {
			int cty = Mth.clamp(k2, y + height - 3, y + height);
			for (int xx = x - 5; xx <= x + 5; ++xx) {
				for (int zz = z - 5; zz <= z + 5; ++zz) {
					double d3 = xx - x;
					double d4 = k2 - cty;
					double d5 = zz - z;
					double dist = d3 * d3 + d4 * d4 + d5 * d5;
					BlockPos p = new BlockPos(xx, k2, zz);
					if (dist < 10 + random.nextInt(8)) {
						BlockState s2 = out.get(p);
						if (s2 != null && (s2.isAir() || s2.is(BlockTags.LEAVES) || s2.is(BlockTags.REPLACEABLE_BY_TREES))) out.set(p, leaves);
					}
				}
			}
		}

		int k2;
		for (k2 = 0; k2 < height; ++k2) {
			BlockState s3 = out.get(new BlockPos(x, y + k2, z));
			if (s3 != null && isOpen(s3)) {
				log(out, x, y + k2, z, Direction.Axis.Y);
				log(out, x - 1, y + k2, z, Direction.Axis.Y);
				log(out, x + 1, y + k2, z, Direction.Axis.Y);
				log(out, x, y + k2, z - 1, Direction.Axis.Y);
				log(out, x, y + k2, z + 1, Direction.Axis.Y);
			}
		}
		log(out, x, y + k2, z, Direction.Axis.Y);
		log(out, x - 1, y, z - 1, Direction.Axis.Y);
		log(out, x + 1, y, z + 1, Direction.Axis.Y);
		log(out, x - 1, y, z + 1, Direction.Axis.Y);
		log(out, x + 1, y, z - 1, Direction.Axis.Y);
		if (random.nextInt(3) != 0) log(out, x - 1, y + 1, z - 1, Direction.Axis.Y);
		if (random.nextInt(3) != 0) log(out, x + 1, y + 1, z + 1, Direction.Axis.Y);
		if (random.nextInt(3) != 0) log(out, x - 1, y + 1, z + 1, Direction.Axis.Y);
		if (random.nextInt(3) != 0) log(out, x + 1, y + 1, z - 1, Direction.Axis.Y);
		log(out, x - 2, y, z, Direction.Axis.X);
		log(out, x + 2, y, z, Direction.Axis.X);
		log(out, x, y, z - 2, Direction.Axis.Z);
		log(out, x, y, z + 2, Direction.Axis.Z);
		log(out, x - 2, y - 1, z, Direction.Axis.Y);
		log(out, x + 2, y - 1, z, Direction.Axis.Y);
		log(out, x, y - 1, z - 2, Direction.Axis.Y);
		log(out, x, y - 1, z + 2, Direction.Axis.Y);
		log(out, x - 1, y + (height - 4), z - 1, Direction.Axis.Y);
		log(out, x + 1, y + (height - 4), z + 1, Direction.Axis.Y);
		log(out, x - 1, y + (height - 4), z + 1, Direction.Axis.Y);
		log(out, x + 1, y + (height - 4), z - 1, Direction.Axis.Y);
		if (random.nextInt(3) == 0) log(out, x - 1, y + (height - 5), z - 1, Direction.Axis.Y);
		if (random.nextInt(3) == 0) log(out, x + 1, y + (height - 5), z + 1, Direction.Axis.Y);
		if (random.nextInt(3) == 0) log(out, x - 1, y + (height - 5), z + 1, Direction.Axis.Y);
		if (random.nextInt(3) == 0) log(out, x + 1, y + (height - 5), z - 1, Direction.Axis.Y);
		log(out, x - 2, y + (height - 4), z, Direction.Axis.X);
		log(out, x + 2, y + (height - 4), z, Direction.Axis.X);
		log(out, x, y + (height - 4), z - 2, Direction.Axis.Z);
		log(out, x, y + (height - 4), z + 2, Direction.Axis.Z);
		out.finish();

		if (config.flowers()) {
			// the original's WorldGenCustomFlowers(shimmerleaf): 18 tries within 7 across and 3 up or down, on grass or sand
			BlockState shimmerleaf = BlocksTC.shimmerleaf.get().defaultBlockState();
			for (int i = 0; i < 18; ++i) {
				BlockPos p = new BlockPos(x + random.nextInt(8) - random.nextInt(8), y + random.nextInt(4) - random.nextInt(4),
						z + random.nextInt(8) - random.nextInt(8));
				BlockState at = out.get(p);
				BlockState under = out.get(p.below());
				if (at != null && under != null && at.isAir() && (under.is(Blocks.GRASS_BLOCK) || under.is(BlockTags.SAND))) {
					world.setBlock(p, shimmerleaf, 2);
				}
			}
		}
		return true;
	}

	/** The original's setBlockAndNotifyAdequately override: only into air, leaves, or something replaceable. */
	private static void log(TreeGenHelper out, int x, int y, int z, Direction.Axis axis) {
		BlockPos p = new BlockPos(x, y, z);
		BlockState state = out.get(p);
		if (state != null && isOpen(state)) {
			out.set(p, BlocksTC.logSilverwood.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
		}
	}

	private static boolean isOpen(BlockState state) {
		return state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced() || state.is(BlockTags.REPLACEABLE_BY_TREES)
				|| state.is(BlockTags.SAPLINGS);
	}
}

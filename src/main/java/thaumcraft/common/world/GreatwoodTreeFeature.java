package thaumcraft.common.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import thaumcraft.api.blocks.BlocksTC;

/**
 * The original's WorldGenGreatwoodTrees: vanilla 1.12's big-oak algorithm with a 2x2 trunk, run twice - a second,
 * wider crown is built on top of the first, starting where the first trunk ends. Replaces a vanilla fancy-oak
 * configuration the port used before, which gave a 1x1 trunk. {@code spider_chance} is the original's 1-in-8 wild
 * "spider tree" (a cave spider spawner under the trunk, cobwebs in the crown, a dungeon chest below); saplings use 0.
 * Grown from a 2x2 of saplings only (ThaumcraftTreeGrowers: a mega tree), placed at the square's north-west corner.
 */
public class GreatwoodTreeFeature extends Feature<GreatwoodTreeFeature.Config> {

	public record Config(float spiderChance) implements FeatureConfiguration {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.floatRange(0f, 1f).optionalFieldOf("spider_chance", 0f).forGetter(Config::spiderChance)
		).apply(i, Config::new));
	}

	private static final byte[] OTHER_COORD_PAIRS = { 2, 0, 0, 1, 2, 1 };

	public GreatwoodTreeFeature(Codec<Config> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<Config> context) {
		return new Builder(context.level(), context.random(), context.origin()).generate(context.config().spiderChance());
	}

	/** One tree's worth of the original's instance state (it reused one generator object per tree). */
	private static final class Builder {
		private final WorldGenLevel world;
		private final TreeGenHelper out;
		private final RandomSource par2Random;
		private final RandomSource rand;
		private final BlockPos origin;
		private final int[] basePos = { 0, 0, 0 };
		private int heightLimit;
		private int height;
		private final double heightAttenuation = 0.618;
		private final double branchSlope = 0.38;
		private double scaleWidth = 1.2;
		private final double leafDensity = 0.9;
		private final int trunkSize = 2;
		private final int heightLimitLimit = 11;
		private final int leafDistanceLimit = 4;
		private int[][] leafNodes;

		Builder(WorldGenLevel world, RandomSource random, BlockPos origin) {
			this.world = world;
			this.out = new TreeGenHelper(world);
			this.par2Random = random;
			this.rand = RandomSource.create(random.nextLong());
			this.origin = origin;
		}

		boolean generate(float spiderChance) {
			BlockPos pos = origin;
			basePos[0] = pos.getX();
			basePos[1] = pos.getY();
			basePos[2] = pos.getZ();
			heightLimit = heightLimitLimit + rand.nextInt(heightLimitLimit);
			for (int x = 0; x < trunkSize; ++x) {
				for (int z = 0; z < trunkSize; ++z) {
					if (!validTreeLocation(x, z)) return false;
				}
			}
			boolean spiders = spiderChance > 0f && par2Random.nextFloat() < spiderChance;
			if (out.canAccess(pos)) world.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			generateLeafNodeList();
			generateLeaves();
			generateLeafNodeBases();
			generateTrunk();
			scaleWidth = 1.66;
			basePos[0] = pos.getX();
			basePos[1] = pos.getY() + height;
			basePos[2] = pos.getZ();
			generateLeafNodeList();
			generateLeaves();
			generateLeafNodeBases();
			generateTrunk();
			out.finish();
			if (spiders) placeSpiderNest(pos);
			return true;
		}

		private void placeSpiderNest(BlockPos pos) {
			BlockPos spawnerPos = pos.below();
			if (out.canAccess(spawnerPos)) {
				world.setBlock(spawnerPos, Blocks.SPAWNER.defaultBlockState(), 2);
				// never the live ServerLevel here - see MoundFeature#placeSpawner
				if (world.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity spawner) {
					spawner.setEntityId(EntityType.CAVE_SPIDER, par2Random);
				}
			}
			for (int a = 0; a < 50; ++a) {
				BlockPos web = new BlockPos(pos.getX() - 7 + par2Random.nextInt(14), pos.getY() + par2Random.nextInt(10),
						pos.getZ() - 7 + par2Random.nextInt(14));
				BlockState state = out.get(web);
				if (state != null && state.isAir() && (isTouching(web, BlocksTC.leavesGreatwood.get().defaultBlockState())
						|| isTouching(web, BlocksTC.logGreatwood.get().defaultBlockState()))) {
					world.setBlock(web, Blocks.COBWEB.defaultBlockState(), 2);
				}
			}
			BlockPos chestPos = pos.below(2);
			if (out.canAccess(chestPos)) {
				world.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
				if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
					chest.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "chests/greatwood")), par2Random.nextLong());
				}
			}
		}

		private boolean isTouching(BlockPos pos, BlockState kind) {
			for (Direction dir : Direction.values()) {
				BlockState state = out.get(pos.relative(dir));
				if (state != null && state.is(kind.getBlock())) return true;
			}
			return false;
		}

		void generateLeafNodeList() {
			height = (int) (heightLimit * heightAttenuation);
			if (height >= heightLimit) height = heightLimit - 1;
			int var1 = (int) (1.382 + Math.pow(leafDensity * heightLimit / 13.0, 2.0));
			if (var1 < 1) var1 = 1;
			int[][] var2 = new int[var1 * heightLimit][4];
			int var3 = basePos[1] + heightLimit - leafDistanceLimit;
			int var4 = 1;
			int var5 = basePos[1] + height;
			int var6 = var3 - basePos[1];
			var2[0][0] = basePos[0];
			var2[0][1] = var3;
			var2[0][2] = basePos[2];
			var2[0][3] = var5;
			--var3;
			while (var6 >= 0) {
				int var7 = 0;
				float var8 = layerSize(var6);
				if (var8 < 0.0f) {
					--var3;
					--var6;
				} else {
					double var9 = 0.5;
					while (var7 < var1) {
						double var10 = scaleWidth * var8 * (rand.nextFloat() + 0.328);
						double var11 = rand.nextFloat() * 2.0 * Math.PI;
						int var12 = Mth.floor(var10 * Math.sin(var11) + basePos[0] + var9);
						int var13 = Mth.floor(var10 * Math.cos(var11) + basePos[2] + var9);
						int[] var14 = { var12, var3, var13 };
						int[] var15 = { var12, var3 + leafDistanceLimit, var13 };
						if (checkBlockLine(var14, var15) == -1) {
							int[] var16 = { basePos[0], basePos[1], basePos[2] };
							double var17 = Math.sqrt(Math.pow(Math.abs(basePos[0] - var14[0]), 2.0) + Math.pow(Math.abs(basePos[2] - var14[2]), 2.0));
							double var18 = var17 * branchSlope;
							if (var14[1] - var18 > var5) {
								var16[1] = var5;
							} else {
								var16[1] = (int) (var14[1] - var18);
							}
							if (checkBlockLine(var16, var14) == -1) {
								var2[var4][0] = var12;
								var2[var4][1] = var3;
								var2[var4][2] = var13;
								var2[var4][3] = var16[1];
								++var4;
							}
						}
						++var7;
					}
					--var3;
					--var6;
				}
			}
			leafNodes = new int[var4][4];
			System.arraycopy(var2, 0, leafNodes, 0, var4);
		}

		void genTreeLayer(int par1, int par2, int par3, float par4, byte par5) {
			int var7 = (int) (par4 + 0.618);
			byte var8 = OTHER_COORD_PAIRS[par5];
			byte var9 = OTHER_COORD_PAIRS[par5 + 3];
			int[] var10 = { par1, par2, par3 };
			int[] var11 = { 0, 0, 0 };
			var11[par5] = var10[par5];
			for (int var12 = -var7; var12 <= var7; ++var12) {
				var11[var8] = var10[var8] + var12;
				for (int var13 = -var7; var13 <= var7; ++var13) {
					double var14 = Math.pow(Math.abs(var12) + 0.5, 2.0) + Math.pow(Math.abs(var13) + 0.5, 2.0);
					if (var14 <= par4 * par4) {
						var11[var9] = var10[var9] + var13;
						BlockPos p = new BlockPos(var11[0], var11[1], var11[2]);
						BlockState state = out.get(p);
						// the original: only into air or its own leaves (1.21: also grass and other tree-replaceable plants)
						if (state != null && (state.isAir() || state.is(BlocksTC.leavesGreatwood.get()) || state.is(BlockTags.REPLACEABLE_BY_TREES))) {
							out.set(p, BlocksTC.leavesGreatwood.get().defaultBlockState());
						}
					}
				}
			}
		}

		float layerSize(int par1) {
			if (par1 < heightLimit * 0.3) return -1.618f;
			float var2 = heightLimit / 2.0f;
			float var3 = heightLimit / 2.0f - par1;
			float var4;
			if (var3 == 0.0f) {
				var4 = var2;
			} else if (Math.abs(var3) >= var2) {
				var4 = 0.0f;
			} else {
				var4 = (float) Math.sqrt(Math.pow(Math.abs(var2), 2.0) - Math.pow(Math.abs(var3), 2.0));
			}
			return var4 * 0.5f;
		}

		float leafSize(int par1) {
			return par1 >= 0 && par1 < leafDistanceLimit ? (par1 != 0 && par1 != leafDistanceLimit - 1 ? 3.0f : 2.0f) : -1.0f;
		}

		void generateLeafNode(int par1, int par2, int par3) {
			for (int var4 = par2, var5 = par2 + leafDistanceLimit; var4 < var5; ++var4) {
				genTreeLayer(par1, var4, par3, leafSize(var4 - par2), (byte) 1);
			}
		}

		void placeBlockLine(int[] from, int[] to) {
			int[] var4 = { 0, 0, 0 };
			byte var6 = 0;
			for (byte var5 = 0; var5 < 3; ++var5) {
				var4[var5] = to[var5] - from[var5];
				if (Math.abs(var4[var5]) > Math.abs(var4[var6])) var6 = var5;
			}
			if (var4[var6] == 0) return;
			byte var7 = OTHER_COORD_PAIRS[var6];
			byte var8 = OTHER_COORD_PAIRS[var6 + 3];
			byte var9 = (byte) (var4[var6] > 0 ? 1 : -1);
			double var10 = var4[var7] / (double) var4[var6];
			double var11 = var4[var8] / (double) var4[var6];
			int[] var12 = { 0, 0, 0 };
			for (int var13 = 0, var14 = var4[var6] + var9; var13 != var14; var13 += var9) {
				var12[var6] = Mth.floor(from[var6] + var13 + 0.5);
				var12[var7] = Mth.floor(from[var7] + var13 * var10 + 0.5);
				var12[var8] = Mth.floor(from[var8] + var13 * var11 + 0.5);
				// the original's log metadata: X when the step is mostly along x, Z when along z, else upright
				Direction.Axis axis = Direction.Axis.Y;
				int var16 = Math.abs(var12[0] - from[0]);
				int var17 = Math.abs(var12[2] - from[2]);
				int var18 = Math.max(var16, var17);
				if (var18 > 0) {
					if (var16 == var18) {
						axis = Direction.Axis.X;
					} else if (var17 == var18) {
						axis = Direction.Axis.Z;
					}
				}
				BlockPos p = new BlockPos(var12[0], var12[1], var12[2]);
				if (isReplaceable(p)) {
					out.set(p, BlocksTC.logGreatwood.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
				}
			}
		}

		/** 1.12's WorldGenAbstractTree#isReplaceable: air, leaves, wood, saplings, vines and the like. */
		private boolean isReplaceable(BlockPos p) {
			BlockState state = out.get(p);
			return state != null && (state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.LEAVES)
					|| state.is(BlockTags.LOGS) || state.is(BlockTags.SAPLINGS));
		}

		void generateLeaves() {
			for (int[] node : leafNodes) generateLeafNode(node[0], node[1], node[2]);
		}

		boolean leafNodeNeedsBase(int par1) {
			return par1 >= heightLimit * 0.2;
		}

		void generateTrunk() {
			int[] var5 = { basePos[0], basePos[1], basePos[2] };
			int[] var6 = { basePos[0], basePos[1] + height, basePos[2] };
			placeBlockLine(var5, var6);
			if (trunkSize == 2) {
				++var5[0];
				++var6[0];
				placeBlockLine(var5, var6);
				++var5[2];
				++var6[2];
				placeBlockLine(var5, var6);
				--var5[0];
				--var6[0];
				placeBlockLine(var5, var6);
			}
		}

		void generateLeafNodeBases() {
			int[] var3 = { basePos[0], basePos[1], basePos[2] };
			for (int[] var4 : leafNodes) {
				int[] var5 = { var4[0], var4[1], var4[2] };
				var3[1] = var4[3];
				if (leafNodeNeedsBase(var3[1] - basePos[1])) placeBlockLine(var3, var5);
			}
		}

		int checkBlockLine(int[] from, int[] to) {
			int[] var3 = { 0, 0, 0 };
			byte var5 = 0;
			for (byte var4 = 0; var4 < 3; ++var4) {
				var3[var4] = to[var4] - from[var4];
				if (Math.abs(var3[var4]) > Math.abs(var3[var5])) var5 = var4;
			}
			if (var3[var5] == 0) return -1;
			byte var6 = OTHER_COORD_PAIRS[var5];
			byte var7 = OTHER_COORD_PAIRS[var5 + 3];
			byte var8 = (byte) (var3[var5] > 0 ? 1 : -1);
			double var9 = var3[var6] / (double) var3[var5];
			double var10 = var3[var7] / (double) var3[var5];
			int[] var11 = { 0, 0, 0 };
			int var12;
			int var13;
			for (var12 = 0, var13 = var3[var5] + var8; var12 != var13; var12 += var8) {
				var11[var5] = from[var5] + var12;
				var11[var6] = Mth.floor(from[var6] + var12 * var9);
				var11[var7] = Mth.floor(from[var7] + var12 * var10);
				BlockState state = out.get(new BlockPos(var11[0], var11[1], var11[2]));
				// the original caught lookup failures and carried on; an unreadable spot counts as open here too
				if (state != null && !state.isAir() && !state.is(BlocksTC.leavesGreatwood.get()) && !state.is(BlockTags.REPLACEABLE_BY_TREES)
						&& !state.is(BlockTags.SAPLINGS)) {
					break;
				}
			}
			return var12 == var13 ? -1 : Math.abs(var12);
		}

		boolean validTreeLocation(int x, int z) {
			int[] var1 = { basePos[0] + x, basePos[1], basePos[2] + z };
			int[] var2 = { basePos[0] + x, basePos[1] + heightLimit - 1, basePos[2] + z };
			BlockPos soil = new BlockPos(basePos[0] + x, basePos[1] - 1, basePos[2] + z);
			BlockState below = out.get(soil);
			if (below == null || !isGrassOrDirt(world, soil)) return false;
			int var4 = checkBlockLine(var1, var2);
			if (var4 == -1) return true;
			if (var4 < 6) return false;
			heightLimit = var4;
			return true;
		}
	}
}

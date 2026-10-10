package thaumcraft.common.blocks.world;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

/**
 * The original's {@code BlockGrassAmbient}: a grass block the Magical Forest scatters (one per chunk at most). It
 * behaves as 1.12 grass - dying to dirt under cover and spreading ordinary grass, not itself - and at night, on the
 * client, it now and then sends a wispy mote up from a grass block within 8 of it: the darker it is (the sky light it
 * sees, turned by the sun's angle), the more often.
 */
public class BlockGrassAmbient extends GrassBlock {

	public static final MapCodec<BlockGrassAmbient> CODEC = simpleCodec(BlockGrassAmbient::new);

	public BlockGrassAmbient(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<GrassBlock> codec() {
		@SuppressWarnings("unchecked")
		MapCodec<GrassBlock> codec = (MapCodec<GrassBlock>) (MapCodec<?>) CODEC;
		return codec;
	}

	/** As 1.12's BlockGrass#updateTick: dies to dirt when it can't be grass, otherwise spreads ordinary grass. */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!canBeGrass(state, level, pos)) {
			if (!level.isAreaLoaded(pos, 1)) return;
			level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
			return;
		}
		if (!level.isAreaLoaded(pos, 3) || level.getMaxLocalRawBrightness(pos.above()) < 9) return;
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		for (int i = 0; i < 4; i++) {
			BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
			if (level.getBlockState(target).is(Blocks.DIRT) && canBeGrass(grass, level, target)
					&& !level.getFluidState(target.above()).is(FluidTags.WATER)) {
				level.setBlockAndUpdate(target, grass.setValue(SNOWY, level.getBlockState(target.above()).is(Blocks.SNOW)));
			}
		}
	}

	private static boolean canBeGrass(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos above = pos.above();
		BlockState over = level.getBlockState(above);
		if (over.is(Blocks.SNOW) && over.getValue(SnowLayerBlock.LAYERS) == 1) return true;
		if (over.getFluidState().getAmount() == 8) return false;
		int light = LightEngine.getLightBlockInto(level, state, pos, over, above, Direction.UP, over.getLightBlock(level, above));
		return light < level.getMaxLightLevel();
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int sky = level.getBrightness(LightLayer.SKY, pos.above()) - level.getSkyDarken();
		float angle = level.getSunAngle(1.0f);
		float target = angle < Mth.PI ? 0.0f : Mth.TWO_PI;
		angle += (target - angle) * 0.2f;
		sky = Mth.clamp(Math.round(sky * Mth.cos(angle)), 0, 15);
		if (4 + sky * 2 >= 1 + random.nextInt(13)) return;
		BlockPos p = pos.offset(Mth.nextInt(random, -8, 8), 5, Mth.nextInt(random, -8, 8));
		for (int q = 0; q < 10 && p.getY() > 50 && !level.getBlockState(p).is(Blocks.GRASS_BLOCK); q++) {
			p = p.below();
		}
		if (level.getBlockState(p).is(Blocks.GRASS_BLOCK)) {
			thaumcraft.client.fx.ClientFxHandlers.wispyMotesOnBlock(level, p.above(), 400, -0.01f);
		}
	}
}

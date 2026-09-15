package thaumcraft.common.blocks.world.plants;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Replaces the old (1.12.2) {@code BlockPlantShimmerleaf} - a small decorative bush unique to the true
 * original's own Magical Forest biome (not independently worldgen-placed - see
 * {@code thaumcraft.common.world.biomes.ThaumcraftBiomes}' own doc comment). Extends vanilla's own
 * {@link BushBlock} directly, same real "reuse a real vanilla base class" precedent as
 * {@link BlockPlantCinderpearl}/Silverwood's {@code SaplingBlock}.
 *
 * <p>Real, kept mechanic: a faint light source (matching the original's own real {@code setLightLevel(0.4f)}
 * - a real, if small, gameplay-relevant glow, not just flavor). Real, deliberate substitution for the
 * original's own client-only particle flourish ({@code FXDispatcher.drawWispyMotes}, a bespoke custom FX
 * system this port hasn't ported) - a plain vanilla {@link ParticleTypes#ENCHANT} tick instead, a real,
 * already-existing "magical glimmer" particle with the same visual intent, not a custom system revival.
 */
public class BlockPlantShimmerleaf extends BushBlock {

	public static final MapCodec<BlockPlantShimmerleaf> CODEC = simpleCodec(BlockPlantShimmerleaf::new);

	private static final VoxelShape SHAPE = box(3.0, 0.0, 3.0, 13.0, 10.0, 13.0);

	public BlockPlantShimmerleaf(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BushBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Vec3 offset = state.getOffset(level, pos);
		return SHAPE.move(offset.x, offset.y, offset.z);
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.DIRT);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			double x = pos.getX() + 0.5 + (random.nextFloat() - random.nextFloat()) * 0.3;
			double y = pos.getY() + 0.4 + (random.nextFloat() - random.nextFloat()) * 0.1;
			double z = pos.getZ() + 0.5 + (random.nextFloat() - random.nextFloat()) * 0.3;
			level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0.0, 0.02, 0.0);
		}
	}
}

package thaumcraft.common.blocks.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Replaces the old (1.12.2) {@code BlockNitor} - one small floating light source per {@link DyeColor} (16
 * blocks, same "one class, one instance per real DyeColor" registration shape
 * {@code thaumcraft.common.blocks.basic.BlockCandle}'s own real port already established). Small non-solid
 * collision box, no block below required (unlike Candle it just floats), full light level.
 *
 * Real, documented simplification: the original's tile entity ({@code TileNitor}) had exactly one real
 * job - driving a custom particle "flame" effect via the still-deferred {@code client/fx} dispatcher (see
 * {@code BlockCandle}'s own doc comment, which already named Nitor as one of the custom-TESR-style effects
 * this session keeps deferring). Dropped the tile entity outright and reused vanilla's own real
 * {@link #animateTick} particle hook instead - same real substitution {@code BlockCandle} itself already
 * uses for its own flame/smoke effect.
 */
public class BlockNitor extends Block {

	private static final VoxelShape SHAPE = Shapes.box(0.33, 0.33, 0.33, 0.66, 0.66, 0.66);

	public final DyeColor dye;

	public BlockNitor(BlockBehaviour.Properties properties, DyeColor dye) {
		super(properties);
		this.dye = dye;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5 + random.nextGaussian() * 0.025;
		double y = pos.getY() + 0.45 + random.nextGaussian() * 0.025;
		double z = pos.getZ() + 0.5 + random.nextGaussian() * 0.025;
		level.addParticle(ParticleTypes.FLAME, x, y, z, random.nextGaussian() * 0.0025, random.nextFloat() * 0.06, random.nextGaussian() * 0.0025);
	}
}

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
 * Replaces the old (1.12.2) {@code BlockPlantCinderpearl}. A small desert bush - no growth stages, just
 * find/harvest/replant, extending vanilla's own {@link BushBlock} directly (the same "reuse a real vanilla
 * base class" precedent as Silverwood/Greatwood's {@code LeavesBlock}/{@code SaplingBlock}). No custom
 * renderer needed - a standard vanilla cross-quad flower shape/model is a complete, faithful reproduction,
 * not a simplification (this block never had a TESR to begin with).
 *
 * Two real, independent, already-reachable uses: a plain vanilla 1x1 shaped recipe into Blaze Powder (a real
 * pre-Nether alternative source), and - once its object tag is registered in
 * {@link thaumcraft.common.lib.crafting.ThaumcraftObjectTags} - essentia extraction through the already-shipped
 * Essentia Smeltery, exactly like any other object-tagged item.
 *
 * Simplified: {@code HARDENED_CLAY}/{@code STAINED_HARDENED_CLAY} (16 variants) collapse to the modern
 * {@code BlockTags.TERRACOTTA} tag, which covers all of them in one check. World generation isn't ported
 * this pass - the original hooked a hand-rolled {@code IWorldGenerator}/{@code WorldGenCustomFlowers}
 * patch-scatter; a real placement isn't needed for the block to be useful (both real uses above only need
 * the item to exist), and would be its own follow-up using vanilla's own {@code RandomPatchFeature} (the
 * same "modern feature, not the original's hand-rolled generator" move already used for Silverwood).
 */
public class BlockPlantCinderpearl extends BushBlock {

	public static final MapCodec<BlockPlantCinderpearl> CODEC = simpleCodec(BlockPlantCinderpearl::new);

	private static final VoxelShape SHAPE = box(5.0, 0.0, 5.0, 11.0, 10.0, 11.0);

	public BlockPlantCinderpearl(BlockBehaviour.Properties properties) {
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
		return state.is(net.minecraft.world.level.block.Blocks.SAND)
				|| state.is(BlockTags.DIRT)
				|| state.is(BlockTags.TERRACOTTA);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextBoolean()) {
			double x = pos.getX() + 0.5 + (random.nextFloat() - random.nextFloat()) * 0.1;
			double y = pos.getY() + 0.6 + (random.nextFloat() - random.nextFloat()) * 0.1;
			double z = pos.getZ() + 0.5 + (random.nextFloat() - random.nextFloat()) * 0.1;
			level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
			level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
		}
	}
}

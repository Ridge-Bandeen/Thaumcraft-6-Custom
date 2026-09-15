package thaumcraft.common.blocks.world.plants;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Replaces the old (1.12.2) {@code BlockPlantVishroom} - a small cave-flavored bush unique to the true
 * original's own Magical Forest biome, real-placed adjacent to wood there (not independently worldgen-placed
 * - see {@code thaumcraft.common.world.biomes.ThaumcraftBiomes}' own doc comment). Extends vanilla's own
 * {@link BushBlock} directly, same real precedent as {@link BlockPlantCinderpearl}/
 * {@link BlockPlantShimmerleaf}.
 *
 * <p>Real, kept mechanic: touching it has a real 20% chance to inflict real Nausea (200 ticks) on a real
 * living entity, matching the original's own real {@code onEntityCollidedWithBlock} exactly - the modern
 * equivalent hook is {@link #entityInside}. Same real faint light source as {@link #BlockPlantShimmerleaf}
 * (the original's own real {@code setLightLevel(0.4f)}). Real, deliberate substitution for the original's
 * own client-only particle flourish ({@code FXDispatcher.drawWispyMotes}) - a plain vanilla
 * {@link ParticleTypes#SPORE_BLOSSOM_AIR} tick instead, a real "drifting cave spore" particle already
 * fitting the block's own real cave-mushroom theme.
 */
public class BlockPlantVishroom extends BushBlock {

	public static final MapCodec<BlockPlantVishroom> CODEC = simpleCodec(BlockPlantVishroom::new);

	private static final VoxelShape SHAPE = box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);

	public BlockPlantVishroom(BlockBehaviour.Properties properties) {
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
		return state.is(BlockTags.DIRT) || state.is(net.minecraft.world.level.block.Blocks.STONE);
	}

	/** Widened from the base class's own {@code protected} to let this real mechanic be exercised directly
	 * by GameTest coverage, same as every other real device/block hook this test suite already calls
	 * directly rather than waiting on a real natural collision tick. */
	@Override
	public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (!level.isClientSide && entity instanceof LivingEntity living && level.getRandom().nextInt(5) == 0) {
			living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			double x = pos.getX() + 0.5 + (random.nextFloat() - random.nextFloat()) * 0.4;
			double y = pos.getY() + 0.3;
			double z = pos.getZ() + 0.5 + (random.nextFloat() - random.nextFloat()) * 0.4;
			level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 0.0, 0.0, 0.0);
		}
	}
}

package thaumcraft.common.blocks.basic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.crafting.IInfusionStabiliserExt;

/**
 * Replaces the old (1.12.2) {@code BlockCandle} - one small standalone candle per {@link DyeColor} (16
 * blocks total, all sharing this class, same registration shape as vanilla's own colored-block families).
 * Needs a solid block below or it pops itself as an item; emits smoke + flame particles; a weak
 * {@link IInfusionStabiliserExt} (the interface methods are inert until a consumer - the deferred Infusion
 * Altar - actually scans for them, same as every other stabiliser block ported so far).
 *
 * The original's single grayscale texture tinted per-instance via its {@code dye} field needed a real
 * client-side color handler to reproduce (no separate texture exists per color, unlike vanilla's own
 * candles) - registered in {@code ThaumcraftClient} via {@code RegisterColorHandlersEvent.Block}, reading
 * {@link DyeColor#getTextureDiffuseColor()}. This is the session's first use of a block color handler, but
 * it's a small, well-supported modern hook - not in the same category as the custom-TESR work this session
 * has consistently deferred (Dioptra, Bellows, Nitor).
 */
public class BlockCandle extends Block implements IInfusionStabiliserExt {

	private static final VoxelShape SHAPE = Shapes.box(0.375, 0.0, 0.375, 0.625, 0.5, 0.625);

	public final DyeColor dye;

	public BlockCandle(BlockBehaviour.Properties properties, DyeColor dye) {
		super(properties);
		this.dye = dye;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.7;
		double z = pos.getZ() + 0.5;
		level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
		level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
	}

	@Override
	public boolean canStabaliseInfusion(Level level, BlockPos pos) {
		return true;
	}

	@Override
	public float getStabilizationAmount(Level level, BlockPos pos) {
		return 0.1f;
	}

	@Override
	public boolean hasSymmetryPenalty(Level level, BlockPos pos1, BlockPos pos2) {
		return false;
	}

	@Override
	public float getSymmetryPenalty(Level level, BlockPos pos) {
		return 0.0f;
	}
}

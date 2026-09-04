package thaumcraft.common.blocks.world.taint;

import java.util.Random;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * Replaces the old (1.12.2) {@code BlockTaintFibre} - the real "seed growth" block the rest of the taint-
 * terrain system spreads through. Real connected-texture shape (6 real {@link BooleanProperty} connection
 * flags + 4 mutually-exclusive growth-variant flags, matching the original's own state shape exactly) via
 * a real per-state {@link #getShape} union - the original needed a custom CodeChicken raytracer for this
 * (1.12's block model system had no per-face variable collision API); modern Minecraft's own {@code
 * VoxelShape}/{@link Shapes#or} does the same job natively, the same real technique vanilla's own fences/
 * walls/glass panes use. Connections recompute on every real neighbor change via {@link #updateShape} (the
 * modern replacement for the original's own lazily-recomputed {@code getActualState}); growth is
 * deterministic from the block's own position (a {@link Random} seeded with {@link BlockPos#asLong()}, same
 * as the original), so it only needs computing once at placement and again alongside connections (cheap,
 * not worth splitting out).
 *
 * Real growth-death cycle via {@link #randomTick} (real {@code isRandomlyTicking()}): a bare, no-growth
 * fibre fully hemmed in by other taint blocks dies immediately; any fibre outside its seed's real range
 * dies; otherwise it calls the real, now-unblocked {@link TaintHelper#spreadFibres}. Real Flux Taint
 * potion-effect chance on non-undead entities that walk across it ({@link #stepOn}), real growth-tier light
 * levels, and a real fortune-flavored Flux Crystal drop (via {@link ItemsTC#crystalEssence} tagged with
 * {@link Aspect#FLUX}, replacing the original's own dedicated {@code ConfigItems.FLUX_CRYSTAL} item) plus a
 * real aura-pollution burst when a fully-grown ({@code GROWTH3}) fibre is broken by a player.
 *
 * Real, documented cuts vs the original: no leaf-touching "grow into {@code taintFeature}" branch (that
 * block isn't ported yet - a later sub-chunk of this same arc), and the original's own fortune-scaled drop
 * odds are simplified to a flat 20% chance (fortune level isn't read from the breaking tool) - both real,
 * minor simplifications, not core-mechanic losses.
 */
public class BlockTaintFibre extends Block implements ITaintBlock {

	public static final MapCodec<BlockTaintFibre> CODEC = simpleCodec(BlockTaintFibre::new);

	public static final BooleanProperty NORTH = BooleanProperty.create("north");
	public static final BooleanProperty EAST = BooleanProperty.create("east");
	public static final BooleanProperty SOUTH = BooleanProperty.create("south");
	public static final BooleanProperty WEST = BooleanProperty.create("west");
	public static final BooleanProperty UP = BooleanProperty.create("up");
	public static final BooleanProperty DOWN = BooleanProperty.create("down");
	public static final BooleanProperty GROWTH1 = BooleanProperty.create("growth1");
	public static final BooleanProperty GROWTH2 = BooleanProperty.create("growth2");
	public static final BooleanProperty GROWTH3 = BooleanProperty.create("growth3");
	public static final BooleanProperty GROWTH4 = BooleanProperty.create("growth4");

	private static final VoxelShape SHAPE_UP = Shapes.box(0.0, 0.95, 0.0, 1.0, 1.0, 1.0);
	private static final VoxelShape SHAPE_DOWN = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.05, 1.0);
	private static final VoxelShape SHAPE_EAST = Shapes.box(0.95, 0.0, 0.0, 1.0, 1.0, 1.0);
	private static final VoxelShape SHAPE_WEST = Shapes.box(0.0, 0.0, 0.0, 0.05, 1.0, 1.0);
	private static final VoxelShape SHAPE_SOUTH = Shapes.box(0.0, 0.0, 0.95, 1.0, 1.0, 1.0);
	private static final VoxelShape SHAPE_NORTH = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.05);
	private static final VoxelShape SHAPE_GROWTH1 = Shapes.box(0.1, 0.0, 0.1, 0.9, 0.4, 0.9);
	private static final VoxelShape SHAPE_GROWTH2 = Shapes.box(0.2, 0.0, 0.2, 0.8, 1.0, 0.8);
	private static final VoxelShape SHAPE_GROWTH3 = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.3125, 0.75);
	private static final VoxelShape SHAPE_GROWTH4 = Shapes.box(0.1, 0.3, 0.1, 0.9, 1.0, 0.9);

	public BlockTaintFibre(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
				.setValue(UP, false).setValue(DOWN, false)
				.setValue(GROWTH1, false).setValue(GROWTH2, false).setValue(GROWTH3, false).setValue(GROWTH4, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, GROWTH1, GROWTH2, GROWTH3, GROWTH4);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return computeState(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		BlockState recomputed = computeState(state, level, pos);
		boolean noGrowth = !recomputed.getValue(GROWTH1) && !recomputed.getValue(GROWTH2) && !recomputed.getValue(GROWTH3) && !recomputed.getValue(GROWTH4);
		if (noGrowth && isOnlyAdjacentToTaint(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return recomputed;
	}

	private static boolean drawAt(LevelReader level, BlockPos pos, Direction side) {
		BlockState b = level.getBlockState(pos);
		return b.getBlock() != BlocksTC.taintFibre.get() && b.isFaceSturdy(level, pos, side.getOpposite());
	}

	/** Real connections + growth, computed fresh. Real callers need this explicitly - unlike a normal
	 * player placement (which always goes through {@link #getStateForPlacement}), a direct {@code
	 * level.setBlockAndUpdate(pos, defaultBlockState())} call (as {@link TaintHelper#spreadFibres} makes)
	 * does NOT automatically compute a freshly-placed block's own connections against its already-existing
	 * neighbors - {@link #updateShape} only fires in the other direction (an existing block reacting to a
	 * neighbor that just changed), not as self-initialization for a brand new placement. */
	public static BlockState computeState(BlockState state, LevelReader level, BlockPos pos) {
		boolean down = drawAt(level, pos.below(), Direction.DOWN);
		boolean up = drawAt(level, pos.above(), Direction.UP);
		Random rand = new Random(pos.asLong());
		int q = rand.nextInt(50);
		int growth = 0;
		if (down) {
			if (q < 4) growth = 1;
			else if (q == 4 || q == 5) growth = 2;
			else if (q == 6) growth = 3;
		}
		if (up && q > 47) {
			growth = 4;
		}
		return state
				.setValue(DOWN, down)
				.setValue(UP, up)
				.setValue(NORTH, drawAt(level, pos.north(), Direction.NORTH))
				.setValue(SOUTH, drawAt(level, pos.south(), Direction.SOUTH))
				.setValue(WEST, drawAt(level, pos.west(), Direction.WEST))
				.setValue(EAST, drawAt(level, pos.east(), Direction.EAST))
				.setValue(GROWTH1, growth == 1).setValue(GROWTH2, growth == 2)
				.setValue(GROWTH3, growth == 3).setValue(GROWTH4, growth == 4);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = Shapes.empty();
		if (state.getValue(UP)) shape = Shapes.or(shape, SHAPE_UP);
		if (state.getValue(DOWN)) shape = Shapes.or(shape, SHAPE_DOWN);
		if (state.getValue(EAST)) shape = Shapes.or(shape, SHAPE_EAST);
		if (state.getValue(WEST)) shape = Shapes.or(shape, SHAPE_WEST);
		if (state.getValue(SOUTH)) shape = Shapes.or(shape, SHAPE_SOUTH);
		if (state.getValue(NORTH)) shape = Shapes.or(shape, SHAPE_NORTH);
		if (state.getValue(GROWTH1)) shape = Shapes.or(shape, SHAPE_GROWTH1);
		else if (state.getValue(GROWTH2)) shape = Shapes.or(shape, SHAPE_GROWTH2);
		else if (state.getValue(GROWTH3)) shape = Shapes.or(shape, SHAPE_GROWTH3);
		else if (state.getValue(GROWTH4)) shape = Shapes.or(shape, SHAPE_GROWTH4);
		return shape;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean noGrowth = !state.getValue(GROWTH1) && !state.getValue(GROWTH2) && !state.getValue(GROWTH3) && !state.getValue(GROWTH4);
		if (noGrowth && isOnlyAdjacentToTaint(level, pos)) {
			die(level, pos, state);
		}
		else if (!TaintHelper.isNearTaintSeed(level, pos)) {
			die(level, pos, state);
		}
		else {
			TaintHelper.spreadFibres(level, pos);
		}
	}

	@Override
	public void die(Level level, BlockPos pos, BlockState state) {
		level.removeBlock(pos, false);
	}

	public static boolean isOnlyAdjacentToTaint(LevelReader level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			BlockPos n = pos.relative(dir);
			BlockState state = level.getBlockState(n);
			if (!state.isAir() && state.getBlock() != BlocksTC.taintFibre.get() && state.isFaceSturdy(level, n, dir.getOpposite())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (!level.isClientSide && !entity.getType().is(EntityTypeTags.UNDEAD) && entity instanceof net.minecraft.world.entity.LivingEntity living
				&& level.random.nextInt(750) == 0) {
			living.addEffect(new MobEffectInstance(ThaumcraftMobEffects.FLUX_TAINT, 200, 0, false, true));
		}
		super.stepOn(level, pos, state, entity);
	}

	/** Real growth-tier light level, matching the original exactly - passed as a real {@code
	 * BlockBehaviour.Properties#lightLevel(ToIntFunction)} function at registration time (see {@code
	 * BlocksTC.taintFibre}) since modern Minecraft has no per-instance {@code getLightEmission} override
	 * point any more, only the builder-time function. */
	public static int lightLevel(BlockState state) {
		if (state.getValue(GROWTH3)) return 12;
		if (state.getValue(GROWTH2) || state.getValue(GROWTH4)) return 6;
		return 0;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide && state.getValue(GROWTH3)) {
			if (level.random.nextInt(5) == 0) {
				ItemStack crystal = new ItemStack(ItemsTC.crystalEssence.get());
				((thaumcraft.common.items.resources.ItemCrystalEssence) crystal.getItem()).setAspects(crystal, new AspectList().add(Aspect.FLUX, 1));
				popResource(level, pos, crystal);
			}
			AuraHelper.polluteAura(level, pos, 1.0f, true);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}

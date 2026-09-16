package thaumcraft.common.blocks.world.taint;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.entities.monster.EntityThaumicSlime;
import thaumcraft.common.registry.ThaumcraftEntities;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * Replaces the old (1.12.2) {@code BlockFluxGoo} - the taint-terrain arc's deliberately-deferred finite-fluid
 * outlier, now real. The original extended Forge's own pre-1.13 {@code BlockFluidFinite}: a real, quantized,
 * CONSERVED-quantity liquid (not vanilla's own infinite-source-block model) - Forge dropped that whole old
 * fluid API when Mojang rewrote fluids for 1.13, and neither Forge/NeoForge nor vanilla has ever shipped a
 * replacement for the finite variant since, so this is a real, from-scratch reimplementation of that same
 * core idea, not a port of any still-existing framework. Deliberately built as a plain {@link Block}, not a
 * real modern {@link net.minecraft.world.level.material.Fluid}/{@code FluidType} - that modern API is itself
 * built entirely around the infinite-source-block model, so fighting it to stay finite would undermine the
 * one property that matters here; the original didn't expose a bucket either, so nothing of substance is
 * lost by skipping that whole subsystem.
 *
 * Real {@link #LEVEL} (0-7, matching the original's own 8 metadata states, {@link #MAX_LEVEL}=7=full) drives
 * a real, self-scheduling flow simulation via {@link #tick} + {@link LevelAccessor#scheduleTick}: every real
 * scheduled tick, a goo block first tries to fall straight down into whatever's below (filling a same-fluid
 * neighbor up to full, or claiming an empty/replaceable space outright), and only spreads sideways - into a
 * real random single neighbor per tick, not all four at once, the same "thick, slow-moving goo" feel the
 * original's own per-tick metadata churn already had - once blocked from falling further. {@link #tryTransfer}
 * is the one real place quanta ever move: every unit subtracted from the giver is added to the receiver,
 * never created or destroyed by the flow engine itself (the block's own decay logic below is a real,
 * deliberate sink on top of that, exactly like the original's own decay branch was).
 *
 * Real per-tick decay (ported faithfully from the original's own {@code updateTick}, still mutually
 * exclusive with flowing on any given tick, matching the original's own control flow exactly): a chance to
 * spawn a real {@link EntityThaumicSlime} (size 1 at levels 2-5, size 2 at level 6-7) when there's real open
 * air above, otherwise a real 1-in-4 chance to either shed one level (polluting the aura) or, once fully
 * drained (level 0), either evaporate into a real aura-pollution burst or condense into a real {@code
 * taint_fibre}. Real entity contact ({@link #entityInside}): a real {@link EntityThaumicSlime} touching a
 * bigger puddle than its own size has a real chance to grow, consuming a level of goo to do it; anything
 * else gets real motion resistance scaled by how full this block is, plus a real {@code VIS_EXHAUST} debuff.
 *
 * Real, documented cuts: no interaction with real vanilla fluids (water/lava) - the original's own base
 * class behavior there isn't available to reference, so goo simply can't flow into/through them (treated the
 * same as any other solid, non-taking neighbor); the original's own milk-cures-this exemption for the
 * VIS_EXHAUST debuff has no modern hook at all (modern milk clears every active effect unconditionally, no
 * per-effect curative-item list exists any more). Real custom {@code GORE} sound type restored 2026-09-15 -
 * see {@link thaumcraft.api.blocks.BlocksTC#fluxGoo}'s own registration and {@link
 * thaumcraft.common.registry.ThaumcraftSounds}'s own doc comment (was a vanilla {@code SoundType.SLIME_BLOCK}
 * stand-in until then).
 */
public class BlockFluxGoo extends Block {

	public static final MapCodec<BlockFluxGoo> CODEC = simpleCodec(BlockFluxGoo::new);

	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 7);
	public static final int MAX_LEVEL = 7;

	private static final int TICK_DELAY = 10;

	public BlockFluxGoo(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LEVEL, MAX_LEVEL));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return state.getValue(LEVEL) < 4;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(state.getBlock())) {
			level.scheduleTick(pos, this, TICK_DELAY);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighborPos, boolean movedByPiston) {
		level.scheduleTick(pos, this, TICK_DELAY);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		int lvl = state.getValue(LEVEL);
		if (entity instanceof EntityThaumicSlime slime) {
			if (!level.isClientSide && slime.getSize() < lvl && level.getRandom().nextBoolean()) {
				slime.setSize(slime.getSize() + 1, true);
				if (lvl > 1) {
					level.setBlockAndUpdate(pos, state.setValue(LEVEL, lvl - 1));
				} else {
					level.removeBlock(pos, false);
				}
			}
			return;
		}
		float resistance = lvl / (float) MAX_LEVEL;
		Vec3 motion = entity.getDeltaMovement();
		entity.setDeltaMovement(motion.x * (1.0 - resistance), motion.y, motion.z * (1.0 - resistance));
		if (!level.isClientSide && entity instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(ThaumcraftMobEffects.VIS_EXHAUST, 600, lvl / 3, true, true));
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int lvl = state.getValue(LEVEL);
		if (lvl >= 2 && level.isEmptyBlock(pos.above()) && random.nextInt(50) == 0) {
			level.removeBlock(pos, false);
			spawnSlime(level, pos, lvl >= 6 ? 2 : 1);
			return;
		}
		if (random.nextInt(4) == 0) {
			decay(state, level, pos, lvl);
			return;
		}
		flow(state, level, pos, random);
	}

	private void decay(BlockState state, ServerLevel level, BlockPos pos, int lvl) {
		if (lvl == 0) {
			if (level.random.nextBoolean()) {
				AuraHelper.polluteAura(level, pos, 1.0f, true);
				level.removeBlock(pos, false);
			} else {
				level.setBlockAndUpdate(pos, BlockTaintFibre.computeState(BlocksTC.taintFibre.get().defaultBlockState(), level, pos));
			}
		} else {
			level.setBlockAndUpdate(pos, state.setValue(LEVEL, lvl - 1));
			AuraHelper.polluteAura(level, pos, 1.0f, true);
			level.scheduleTick(pos, this, TICK_DELAY);
		}
	}

	private void flow(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int lvl = state.getValue(LEVEL);
		if (lvl <= 0) {
			return;
		}
		BlockPos below = pos.below();
		if (tryTransfer(level, pos, state, lvl, below, level.getBlockState(below), MAX_LEVEL)) {
			return;
		}
		// Blocked below - a real single random neighbor per tick, not all four at once, capped at roughly
		// half this block's own level so a puddle spreads gradually instead of instantly thinning to a
		// single-quantum film across every open direction at once.
		List<Direction> dirs = Direction.Plane.HORIZONTAL.shuffledCopy(random);
		for (Direction dir : dirs) {
			BlockPos side = pos.relative(dir);
			if (tryTransfer(level, pos, state, lvl, side, level.getBlockState(side), Math.max(1, lvl / 2))) {
				return;
			}
		}
		// Nothing eligible this tick - stays idle until a real neighbor change wakes it back up.
	}

	/** Real, conserved quanta transfer: moves as much of {@code lvl} as the target has room for (up to
	 * {@code capTargetTo}) into {@code targetPos}, if that position is either empty/replaceable or already
	 * real goo with room to spare. Every unit removed here from the giver is the exact same unit added to
	 * the receiver - this is the one place the whole simulation's real conservation lives. */
	private boolean tryTransfer(ServerLevel level, BlockPos pos, BlockState state, int lvl, BlockPos targetPos, BlockState targetState, int capTargetTo) {
		int targetLevel;
		if (targetState.is(this)) {
			targetLevel = targetState.getValue(LEVEL);
		} else if (targetState.isAir() || targetState.canBeReplaced()) {
			targetLevel = 0;
		} else {
			return false;
		}
		int room = Math.min(MAX_LEVEL, capTargetTo) - targetLevel;
		int amount = Math.min(lvl, room);
		if (amount <= 0) {
			return false;
		}
		int newSelfLevel = lvl - amount;
		if (newSelfLevel <= 0) {
			level.removeBlock(pos, false);
		} else {
			level.setBlockAndUpdate(pos, state.setValue(LEVEL, newSelfLevel));
			level.scheduleTick(pos, this, TICK_DELAY);
		}
		level.setBlockAndUpdate(targetPos, defaultBlockState().setValue(LEVEL, targetLevel + amount));
		level.scheduleTick(targetPos, this, TICK_DELAY);
		return true;
	}

	private void spawnSlime(ServerLevel level, BlockPos pos, int size) {
		EntityThaumicSlime slime = ThaumcraftEntities.THAUMIC_SLIME.get().create(level);
		if (slime == null) {
			return;
		}
		slime.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
		slime.setSize(size, true);
		level.addFreshEntity(slime);
		slime.playSound(SoundEvents.SLIME_SQUISH, 1.0f, 1.0f);
	}
}

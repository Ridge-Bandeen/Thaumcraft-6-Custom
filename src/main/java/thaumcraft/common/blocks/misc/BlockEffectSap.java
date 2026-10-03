package thaumcraft.common.blocks.misc;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.entities.IEldritchMob;

/**
 * Replaces the old (1.12.2) {@code BlockEffect}'s {@code effect_sap} registration - one of 3 real, invisible,
 * self-destroying "phantom hazard" variants the true original shared across one class ({@code effect_sap}/
 * {@code effect_shock}/{@code effect_glimmer}, distinguished only by which registered instance a state's
 * block equality check matched, not by any real per-state property). Only {@code effect_sap} gets a real
 * modern port here:
 * <ul>
 * <li>{@code effect_glimmer} is its own block, {@link BlockEffectGlimmer} (it neither hurts nor goes away).</li>
 * <li>{@code effect_shock} is real, registered, and coded (a Slowness + 1 magic damage variant of this same
 * on-touch mechanic) - but a real {@code git grep} across the ENTIRE true original codebase found it has
 * {@code ZERO} real placers anywhere, not even upstream. Same "registered but never actually wired up, not a
 * porting gap" verdict already reached for {@code EnumInfusionEnchantment}'s SWIFT/AGILE/INFESTED/
 * VISBATTERY/VISCHARGE - documented here so it isn't re-investigated as a false gap later, not ported.</li>
 * </ul>
 *
 * <p>Real mechanic ({@link #entityInside}): a real, non-{@link IEldritchMob}, not-already-Withered
 * {@link LivingEntity} that touches it takes a real short Wither I / Slowness II / Hunger II burst (same
 * real durations/amplifiers/ambient-and-visible flags as the true original's own
 * {@code PotionEffect(..., 40, 0/1, true, true)} calls), then the block consumes itself - a one-shot ground
 * trap, not a lingering hazard. {@link IEldritchMob}s are immune so the 2 real placers below don't hurt
 * their own summoner.
 *
 * <p>Real self-destruct timing, faithfully split the same way the true original split it across 2 separate
 * Forge mechanisms now unified under vanilla's own real {@link #tick}/{@link #randomTick} pair: the true
 * original's default placement relied purely on {@code setTickRandomly(true)}'s own ambient random-tick pass
 * (unpredictable timing, matching crop-growth-style randomness - real placers: {@link
 * thaumcraft.common.items.casters.foci.FocusEffectCurse}'s block-hit branch, and
 * {@link thaumcraft.common.entities.monster.boss.EntityEldritchWarden}'s own constant per-tick foot-seeding),
 * while its one real ADDITIONAL guaranteed-cleanup placer ({@code EntityEldritchWarden}'s field-frenzy
 * spiral) also explicitly scheduled a real 250-400-tick removal on top, so a whole spiral of many sap blocks
 * is guaranteed gone well before the boss becomes vulnerable again even if random-tick doesn't reach them
 * first. Both real modern hooks just remove the block - same real outcome either way it fires.
 */
public class BlockEffectSap extends Block {

	public static final MapCodec<BlockEffectSap> CODEC = simpleCodec(BlockEffectSap::new);

	public BlockEffectSap(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	/** Invisible - the original returned {@code EnumBlockRenderType.INVISIBLE} and drew only sparks (see
	 * {@link #animateTick}). The port's blockstate was an unconverted 1.12 {@code forge_marker} file pointing at a
	 * model that never resolved, so the field rendered as the missing-model cube (found in the first playtest: a
	 * dead Warden's fields littered the ground as magenta/black cubes). */
	@Override
	protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
		return net.minecraft.world.level.block.RenderShape.INVISIBLE;
	}

	/** The original's own shimmer: a purple spark drifting up from the field every display tick, plus an
	 * occasional crackle (1 in 50). Same spark as the original's (see {@link thaumcraft.client.fx.ClientFxHandlers#spawnSpark};
	 * dedicated {@code FXDispatcher#spark} has no port equivalent). */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		float h = random.nextFloat() * 0.33f;
		double x = pos.getX() + random.nextFloat();
		double y = pos.getY() + 0.1515 + h / 2.0;
		double z = pos.getZ() + random.nextFloat();
		thaumcraft.client.fx.ClientFxHandlers.spawnSpark(level, x, y, z, 3.0f + h * 6.0f, 0.3f - random.nextFloat() * 0.1f, 0.0f, 0.5f + random.nextFloat() * 0.2f, 1.0f);
		if (random.nextInt(50) == 0) {
			level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), thaumcraft.common.registry.ThaumcraftSounds.JACOBS.get(),
					net.minecraft.sounds.SoundSource.AMBIENT, 0.25f, 1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f, false);
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return true;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (level.isClientSide) return;
		if (entity instanceof IEldritchMob) return;
		if (!(entity instanceof LivingEntity living) || living.hasEffect(MobEffects.WITHER)) return;

		living.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0, true, true));
		living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, true, true));
		living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 1, true, true));
		level.removeBlock(pos, false);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.removeBlock(pos, false);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.removeBlock(pos, false);
	}
}

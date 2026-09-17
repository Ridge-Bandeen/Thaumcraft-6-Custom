package thaumcraft.common.blocks.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.damagesource.ThaumcraftDamageTypes;

/**
 * Replaces the old (1.12.2) {@code BlockFluidDeath} ("liquid_death") - a real, dangerous, player-crafted
 * fluid, NOT the finite-fluid outlier ({@link thaumcraft.common.blocks.world.taint.BlockFluxGoo}) - the true
 * original's own real base class was Forge's OLD {@code BlockFluidClassic} (an infinite-source-block
 * convenience wrapper), so this one gets the real modern vanilla {@code LiquidBlock}/{@code FlowingFluid}
 * API instead (see {@link thaumcraft.common.registry.ThaumcraftFluids}'s own doc comment for the fuller
 * story). Real, from-scratch, {@code /summon}-independent content: the true original had a real bucket
 * (Forge's {@code FluidRegistry.addBucketForFluid}) and no natural worldgen placement at all - the only real
 * way to obtain it is the real Crucible recipe (empty bucket + Death/Alchemy/Entropy aspects, see
 * {@code data/thaumcraft/recipe/liquid_death.json}), matching the true original's own real
 * {@code ConfigRecipes} entry exactly.
 *
 * <p>Real {@link #entityInside} replicates the true original's own real {@code onEntityCollidedWithBlock}:
 * server-side, a real {@link LivingEntity} touching it takes real {@link ThaumcraftDamageTypes#DISSOLVE}
 * damage (bypasses armor, matching the original's own {@code setDamageBypassesArmor}) every tick, tapering
 * with the real live {@link net.minecraft.world.level.material.FluidState#getAmount()} (8 at a full source
 * down to 1 at the weakest flowing edge) - real, deliberately approximate translation of the original's own
 * 4-step meta-based taper (5 down to 2 damage) onto this port's real 8-step {@code FluidState} amount scale,
 * same shape, not an exact number-for-number replica. Real horizontal-motion slow on every entity, same real
 * shape as the original's own {@code motionX/Z *= 1 - quantaPercentage/2}. A killing blow through this real
 * damage type already has a real, separate, already-shipped consequence -
 * {@code MobDeathDropEvents#onLivingDrops} drops the victim's own real essentia as crystal-essence stacks
 * instead of its normal loot, ported from the SAME original {@code EntityEvents} handler this block's own
 * real damage source used to trigger, in an earlier, uncaptured session.
 *
 * <p>Real client-only ambient surface bubble restored 2026-09-17 (was dropped under this port's old blanket
 * "no client FX" rule) - a real faithful port of the true original's own real {@code randomDisplayTick}
 * (1-in-20 chance per real random block tick), routed through
 * {@link thaumcraft.client.fx.ClientFxHandlers#spawnSlimyBubble} for the same real dist-crash-avoidance
 * reason every other client-only trigger in this port uses. Real, disclosed approximation: the true
 * original's own real per-bubble height offset was tied to its own 0-4 {@code meta} quanta scale - this
 * port's own real {@link net.minecraft.world.level.material.FluidState#getAmount()} uses a real 0-8 scale
 * instead, so the same real "shallower fluid bubbles higher up" shape is kept, just rescaled rather than
 * number-for-number identical (matching the same real precedent {@link #entityInside}'s own doc comment
 * already set for the damage taper).
 */
public class BlockLiquidDeath extends LiquidBlock {

	public BlockLiquidDeath(FlowingFluid fluid, BlockBehaviour.Properties properties) {
		super(fluid, properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(20) == 0) {
			int amount = state.getFluidState().getAmount();
			float h = random.nextFloat() * 0.075f;
			double y = pos.getY() + 0.1 + 0.225 * (8 - amount) / 2.0;
			thaumcraft.client.fx.ClientFxHandlers.spawnSlimyBubble(level, pos.getX() + random.nextFloat(), y, pos.getZ() + random.nextFloat(), 0.075f + h);
		}
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		int amount = state.getFluidState().getAmount();

		Vec3 motion = entity.getDeltaMovement();
		double slow = 1.0 - (amount / 8.0) / 2.0;
		entity.setDeltaMovement(motion.x * slow, motion.y, motion.z * slow);

		if (!level.isClientSide && entity instanceof LivingEntity living) {
			int damage = 2 + (amount * 3) / 8;
			living.hurt(ThaumcraftDamageTypes.dissolve(level), damage);
		}
	}
}

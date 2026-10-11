package thaumcraft.common.blocks.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * The original's {@code BlockFluidPure}: Purifying Fluid, a real flowing fluid ({@code BlockFluidClassic}, like water but
 * never making new sources), with a bucket (see {@code ThaumcraftFluids}). Moving through it slows you by its depth; a
 * player without Warp Ward stepping into a source block is given Warp Ward (200000 / sqrt(permanent warp) ticks, at most
 * 32000) and the block is used up. Now and then a pale bubble rises from it, and it pops quietly.
 */
public class BlockPurifyingFluid extends LiquidBlock {

	public BlockPurifyingFluid(FlowingFluid fluid, BlockBehaviour.Properties properties) {
		super(fluid, properties);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		float quanta = state.getFluidState().getAmount() / 8.0f;
		Vec3 motion = entity.getDeltaMovement();
		entity.setDeltaMovement(motion.x * (1.0f - quanta / 2.0f), motion.y, motion.z * (1.0f - quanta / 2.0f));
		if (level.isClientSide || !state.getFluidState().isSource() || !(entity instanceof Player player)) return;
		if (player.hasEffect(ThaumcraftMobEffects.WARP_WARD)) return;

		int warp = ThaumcraftCapabilities.getWarp(player).get(IPlayerWarp.EnumWarpType.PERMANENT);
		int div = Math.max(1, (int) Math.sqrt(warp));
		int duration = Math.min(32000, 200000 / div);
		player.addEffect(new MobEffectInstance(ThaumcraftMobEffects.WARP_WARD, duration, 0, true, true));
		// not removeBlock: for a liquid that puts the fluid's own block straight back
		level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(10) == 0) {
			int meta = Math.min(7, state.getValue(LEVEL));
			thaumcraft.client.fx.ClientFxHandlers.purifyingBubble(level, pos.getX() + random.nextFloat(), pos.getY() + 0.125f * (8 - meta),
					pos.getZ() + random.nextFloat());
		}
		if (random.nextInt(50) == 0) {
			level.playLocalSound(pos.getX() + random.nextFloat(), pos.getY() + 0.5, pos.getZ() + random.nextFloat(), SoundEvents.LAVA_POP,
					SoundSource.BLOCKS, 0.1f + random.nextFloat() * 0.1f, 0.9f + random.nextFloat() * 0.15f, false);
		}
	}
}

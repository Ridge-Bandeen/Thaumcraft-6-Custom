package thaumcraft.common.lib.potions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import thaumcraft.api.blocks.BlocksTC;

/**
 * Replaces the old (1.12.2) {@code PotionThaumarhia} - one of the real Warp-event misfortunes (see {@code
 * thaumcraft.common.lib.events.WarpEvents#checkWarpEvent}). Every real second (matching the original's own
 * {@code isReady} throttle), a 1-in-15 chance to turn the air block at the wearer's feet into real Flux Goo,
 * if it's actually air.
 */
public class MobEffectThaumarhia extends MobEffect {

	public static MobEffect instance = null; // assigned at registration time, same convention as its siblings

	public MobEffectThaumarhia() {
		super(MobEffectCategory.HARMFUL, 0x664477); // the original's colour
	}

	@Override
	public boolean applyEffectTick(LivingEntity target, int amplifier) {
		if (target.level().isClientSide) return true;
		if (target.getRandom().nextInt(15) == 0) {
			BlockPos pos = target.blockPosition();
			if (target.level().getBlockState(pos).isAir()) {
				target.level().setBlockAndUpdate(pos, BlocksTC.fluxGoo.get().defaultBlockState());
			}
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return duration % 20 == 0;
	}
}

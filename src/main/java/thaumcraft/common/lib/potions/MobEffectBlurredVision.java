package thaumcraft.common.lib.potions;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code PotionBlurredVision} - a real Warp-event misfortune (see {@code
 * thaumcraft.common.lib.events.WarpEvents#checkWarpEvent}). The original's own {@code performEffect} was
 * already empty - its real effect was purely a client-side vision-blur shader overlay, never wired up to a
 * per-tick server effect. Ported as-is: a real, inert marker effect, same as the original.
 */
public class MobEffectBlurredVision extends MobEffect {

	public static MobEffect instance = null; // assigned at registration time, same convention as its siblings

	public MobEffectBlurredVision() {
		super(MobEffectCategory.HARMFUL, 0x808080); // the original's colour
	}

	@Override
	public boolean applyEffectTick(LivingEntity target, int amplifier) {
		return true;
	}
}

package thaumcraft.common.lib.potions;

import java.util.List;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * Replaces the old (1.12.2) {@code PotionInfectiousVisExhaust} - one of {@code EntityFluxRift}'s weighted
 * rift events (weight 20 of the table - see that entity's own doc comment). Same package as the original
 * ({@code common.lib.potions}, distinct from {@link thaumcraft.api.potions.MobEffectFluxTaint}/
 * {@link thaumcraft.api.potions.MobEffectVisExhaust}, which already lived under {@code api.potions} in the
 * original too - this one never did, so it stays put rather than moving).
 *
 * Every 40 ticks (same {@code shouldApplyEffectTickThisTick} throttle idiom {@code MobEffectFluxTaint}
 * already established), infects every living entity within 4 blocks that doesn't already carry this same
 * effect with a copy one amplifier tier weaker - once the amplifier bottoms out, it hands off to plain
 * {@link thaumcraft.api.potions.MobEffectVisExhaust} instead, exactly matching the original's own decaying
 * infection chain.
 */
public class MobEffectInfectiousVisExhaust extends MobEffect {

	public static MobEffect instance = null; // assigned at registration time, same convention as its siblings

	public MobEffectInfectiousVisExhaust() {
		super(MobEffectCategory.HARMFUL, 0x665577); // the original's colour
	}

	@Override
	public boolean applyEffectTick(LivingEntity target, int amplifier) {
		List<LivingEntity> nearby = target.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4.0));
		for (LivingEntity entity : nearby) {
			if (!entity.hasEffect(ThaumcraftMobEffects.INFECTIOUS_VIS_EXHAUST)) {
				if (amplifier > 0) {
					entity.addEffect(new MobEffectInstance(ThaumcraftMobEffects.INFECTIOUS_VIS_EXHAUST, 6000, amplifier - 1, false, true));
				} else {
					entity.addEffect(new MobEffectInstance(ThaumcraftMobEffects.VIS_EXHAUST, 6000, 0, false, true));
				}
			}
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return duration % 40 == 0;
	}
}

package thaumcraft.common.lib.potions;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code PotionSunScorned} - a real Warp-event misfortune (see {@code
 * thaumcraft.common.lib.events.WarpEvents#checkWarpEvent}): a real vampiric aversion to daylight. Every 2
 * real seconds, real bright light gives a real chance to catch fire; real darkness gives a real chance to
 * heal 1 HP instead.
 *
 * The original's own daylight check also required a real clear line to the sky ({@code
 * canBlockSeeSky}) on top of brightness - dropped here in favor of the same real, already-established
 * modern vanilla precedent {@code Zombie}'s own sun-avoidance uses ({@code
 * Entity#getLightLevelDependentMagicValue()} plus {@code Level#isDay()} alone, no separate sky-visibility
 * check) - confirmed via bytecode, not guessed.
 */
public class MobEffectSunScorned extends MobEffect {

	public static MobEffect instance = null; // assigned at registration time, same convention as its siblings

	public MobEffectSunScorned() {
		super(MobEffectCategory.HARMFUL, 0xF8D86A); // the original's colour
	}

	@Override
	public boolean applyEffectTick(LivingEntity target, int amplifier) {
		if (target.level().isClientSide) return true;
		float brightness = target.getLightLevelDependentMagicValue();
		if (target.level().isDay() && brightness > 0.5f && target.getRandom().nextFloat() * 30.0f < (brightness - 0.4f) * 2.0f) {
			target.igniteForSeconds(4);
		} else if (brightness < 0.25f && target.getRandom().nextFloat() > brightness * 2.0f) {
			target.heal(1.0f);
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return duration % 40 == 0;
	}
}

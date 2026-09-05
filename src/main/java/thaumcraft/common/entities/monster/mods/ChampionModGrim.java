package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code ChampionModGrim}. Type 1 (offensive) - real, as-is: a real 40% chance to
 * Wither whoever this mob hits, for 200 ticks (matching the original's own real formula).
 */
public class ChampionModGrim implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		if (boss.getRandom().nextFloat() < 0.4f) {
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
		}
		return amount;
	}
}

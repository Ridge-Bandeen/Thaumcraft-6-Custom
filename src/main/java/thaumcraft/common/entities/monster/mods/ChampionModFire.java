package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code ChampionModFire}. Type 1 (offensive) - real, as-is: a real 40% chance to
 * set whoever it hits on fire for 4 real seconds.
 */
public class ChampionModFire implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		if (boss.getRandom().nextFloat() < 0.4f) {
			target.igniteForSeconds(4);
		}
		return amount;
	}
}

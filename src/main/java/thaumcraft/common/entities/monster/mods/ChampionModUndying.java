package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code ChampionModUndying}. Type 0 (self, dispatched once per tick) - real,
 * as-is: heals 1 real health every second.
 */
public class ChampionModUndying implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		if (boss.tickCount % 20 == 0) {
			boss.heal(1.0f);
		}
		return amount;
	}
}

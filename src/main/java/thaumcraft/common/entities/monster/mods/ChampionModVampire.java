package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code ChampionModVampire}. Type 1 (offensive) - real, as-is: heals the mob for
 * half the real damage it just dealt (minimum 2).
 */
public class ChampionModVampire implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		boss.heal(Math.max(2.0f, amount / 2.0f));
		return amount;
	}
}

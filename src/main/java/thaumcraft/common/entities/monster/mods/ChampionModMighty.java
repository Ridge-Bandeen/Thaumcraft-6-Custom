package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code ChampionModMighty}. Type -1 (passive) - see {@link ChampionModBold}'s own
 * doc comment for why {@code performEffect} is never dispatched; the real behavior is the one-time
 * attack-damage buff {@link thaumcraft.common.lib.utils.ChampionUtils#makeChampion} applies at spawn.
 */
public class ChampionModMighty implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		return amount;
	}
}

package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code ChampionModBold}. Type -1 (passive) - real behavior is entirely the
 * one-time movement-speed buff {@link thaumcraft.common.lib.utils.ChampionUtils#makeChampion} applies at
 * spawn; this class's {@code performEffect} is never actually dispatched (only type 0/1/2 mods are), same as
 * the original.
 */
public class ChampionModBold implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		return amount;
	}
}

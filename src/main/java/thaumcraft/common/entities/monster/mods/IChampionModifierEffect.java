package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code IChampionModifierEffect}: the per-mod combat/tick behaviour. The original's
 * client-only {@code showFX}/{@code preRender} (each mod's particles, tints and the tainted skin) live in
 * {@link thaumcraft.client.fx.ChampionFx}, kept out of this common interface.
 *
 * @param boss the champion mob this modifier belongs to
 * @param target for type 1 (offensive, called on a hit dealt) and type 2 (defensive, called on a hit taken)
 * mods, the other party in the exchange; {@code null} for type 0 (self) mods, which are called once per tick
 * with no target at all
 * @param source the real damage source, {@code null} for type 0 self mods
 * @param amount the real damage amount - type 1/2 mods return the (possibly modified) amount to apply;
 * type 0 mods' return value is ignored
 */
public interface IChampionModifierEffect {
	float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount);
}

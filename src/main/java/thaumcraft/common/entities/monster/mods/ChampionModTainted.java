package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code ChampionModTainted}. Type 0 (self, dispatched once per tick).
 *
 * Real, deliberate cut: the original's {@code resetAI} rewrote a NON-{@code EntityMob} critter's entire AI
 * task list into a real hostile attacker (swimming/melee/wander/watch/hurt-by/nearest-attackable-target
 * tasks) - a separate conversion path for turning passive/neutral mobs aggressive, used elsewhere (a
 * Flux-Taint-driven "convert a dying tainted mob" mechanic, itself unported). That whole branch is guarded by
 * {@code !(critter instanceof EntityMob)} in the original, and champion mods are only ever assigned to an
 * already-hostile {@code EntityMob} via {@code ChampionUtils#makeChampion} - so for the champion-mod dispatch
 * path specifically, that branch was already always dead code even in the original. The only reachable half
 * (ensuring {@code ATTACK_DAMAGE} exists with a sane floor) is also moot in modern Minecraft, where every real
 * {@code Mob} subtype already registers that attribute by default. Net effect: this mod is a legitimate no-op
 * for now - a mob "being Tainted" currently means nothing beyond the id occupying a roll slot. Revisit only if
 * the non-mob-conversion mechanic itself ever gets ported.
 */
public class ChampionModTainted implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		return amount;
	}
}

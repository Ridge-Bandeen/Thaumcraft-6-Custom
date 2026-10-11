package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code ChampionModSpined}. Type 2 (defensive) - real, as-is: reflects 1-3 real
 * thorns damage back at whoever hits this mob, guarded against re-triggering off its own reflected damage.
 */
public class ChampionModSpined implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		if (target == null || source.is(DamageTypes.THORNS)) return amount;
		target.hurt(boss.damageSources().thorns(boss), 1 + boss.getRandom().nextInt(3));
		target.playSound(net.minecraft.sounds.SoundEvents.THORNS_HIT, 0.5f, 1.0f);
		return amount;
	}
}

package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code ChampionModArmored}. Type 2 (defensive) - real, as-is: reduces incoming
 * damage to 19/25 (76%) of its real amount, unless the damage source bypasses armor.
 */
public class ChampionModArmored implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
			amount = amount * 19.0f / 25.0f;
		}
		return amount;
	}
}

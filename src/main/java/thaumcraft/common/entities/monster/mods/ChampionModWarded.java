package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Replaces the old (1.12.2) {@code ChampionModWarded}. Type 0 (self, dispatched once per tick) - real:
 * every 25 ticks, slowly regenerates absorption shield up to half its real max health. Drops the original's
 * extra "not currently hurt-resistant" pacing guard - 1.12's post-hit invincibility-frame counter has no
 * direct modern equivalent field, and the 25-tick cadence alone already keeps the regen rate reasonable.
 */
public class ChampionModWarded implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		if (boss.tickCount % 25 == 0) {
			float bh = (float) boss.getAttributeValue(Attributes.MAX_HEALTH) / 2.0f;
			if (boss.getAbsorptionAmount() < bh) {
				boss.setAbsorptionAmount(boss.getAbsorptionAmount() + 1.0f);
			}
		}
		return amount;
	}
}

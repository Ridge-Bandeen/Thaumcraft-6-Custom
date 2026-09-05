package thaumcraft.common.lib.potions;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Replaces the old (1.12.2) {@code PotionUnnaturalHunger} - a real Warp-event misfortune (see {@code
 * thaumcraft.common.lib.events.WarpEvents#checkWarpEvent}). Every real tick, drains a real player's hunger
 * faster (exhaustion scaled by amplifier), ported as-is.
 */
public class MobEffectUnnaturalHunger extends MobEffect {

	public static MobEffect instance = null; // assigned at registration time, same convention as its siblings

	public MobEffectUnnaturalHunger() {
		super(MobEffectCategory.HARMFUL, 0x604000);
	}

	@Override
	public boolean applyEffectTick(LivingEntity target, int amplifier) {
		if (!target.level().isClientSide && target instanceof Player player) {
			player.causeFoodExhaustion(0.025f * (amplifier + 1));
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}
}

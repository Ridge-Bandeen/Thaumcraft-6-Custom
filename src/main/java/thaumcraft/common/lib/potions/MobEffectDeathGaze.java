package thaumcraft.common.lib.potions;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code PotionDeathGaze} - a real Warp-event misfortune (see {@code
 * thaumcraft.common.lib.events.WarpEvents#checkWarpEvent}). The original's own {@code performEffect} was
 * already empty - its real effect (forcing every visible nearby real living entity to attack the wearer and
 * inflicting Wither on them) lives entirely in {@code WarpEvents#checkDeathGaze}, checked every real second
 * as long as this effect is active. Ported as-is: this class is just the real, inert marker the check reads.
 */
public class MobEffectDeathGaze extends MobEffect {

	public static MobEffect instance = null; // assigned at registration time, same convention as its siblings

	public MobEffectDeathGaze() {
		super(MobEffectCategory.HARMFUL, 0x400040);
	}

	@Override
	public boolean applyEffectTick(LivingEntity target, int amplifier) {
		return true;
	}
}

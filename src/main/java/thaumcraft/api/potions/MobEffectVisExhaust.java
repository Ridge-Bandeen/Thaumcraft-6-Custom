package thaumcraft.api.potions;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code PotionVisExhaust}. See {@link MobEffectFluxTaint} for notes on
 * what changed between {@code Potion} and {@link MobEffect}.
 */
public class MobEffectVisExhaust extends MobEffect {

    public static MobEffect instance = null; // assigned at registration time

    public MobEffectVisExhaust() {
        super(MobEffectCategory.HARMFUL, 0x664477); // the original's colour
    }

    @Override
    public boolean applyEffectTick(LivingEntity target, int amplifier) {
        return true;
    }
}

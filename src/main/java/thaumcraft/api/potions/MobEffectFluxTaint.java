package thaumcraft.api.potions;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import thaumcraft.api.ThaumcraftAttributes;
import thaumcraft.api.damagesource.ThaumcraftDamageTypes;
import thaumcraft.api.entities.ITaintedMob;

/**
 * Replaces the old (1.12.2) {@code PotionFluxTaint}. {@code Potion} was renamed to
 * {@link MobEffect} and reworked: there is no more per-effect status-icon rendering code to
 * write (the icon is an 18x18 PNG at {@code assets/thaumcraft/textures/mob_effect/flux_taint.png},
 * auto-drawn from the effect's registry id), and "effectiveness" (which only affected brewing-stand
 * amplifier/duration scaling) was dropped from the API entirely.
 *
 * The registered instance is expected to be assigned to {@link #instance} by the registry setup
 * code (mirrors the old convention, kept so the ~16 not-yet-ported call sites that reference
 * {@code PotionFluxTaint.instance}/{@code MobEffectFluxTaint.instance} need minimal changes when
 * they're ported).
 */
public class MobEffectFluxTaint extends MobEffect {

    public static MobEffect instance = null; // assigned at registration time

    public MobEffectFluxTaint() {
        super(MobEffectCategory.HARMFUL, 0x8080a0);
    }

    @Override
    public boolean applyEffectTick(LivingEntity target, int amplifier) {
        var championAttribute = target.getAttribute(ThaumcraftAttributes.CHAMPION_MOD);
        if (target instanceof ITaintedMob || (championAttribute != null && (int) championAttribute.getValue() == 13)) {
            target.heal(1);
        } else if (!target.isInvertedHealAndHarm()) {
            // Original 1.12.2 logic branched separately on "not undead && not player" vs.
            // "not undead && (maxHealth > 1 || player)" with identical effect (attack for 1)
            // in both cases; collapsed here to its actual behaviour: damage anything not undead.
            target.hurt(ThaumcraftDamageTypes.taint(target.level()), 1);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int k = 40 >> amplifier;
        return k <= 0 || duration % k == 0;
    }
}

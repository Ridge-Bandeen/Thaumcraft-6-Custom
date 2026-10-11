package thaumcraft.common.lib.potions;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Replaces the old (1.12.2) {@code PotionWarpWard} - a real, beneficial (not harmful, unlike every other
 * Warp-related effect this port has) ward: while active, real Warp misfortune rolls don't happen at all
 * (see {@code thaumcraft.common.lib.events.WarpEvents#checkWarpEvent}, gated on this effect's real
 * absence). The original's own {@code performEffect} was already empty - this is purely a real, inert
 * marker the check reads, same as the original.
 */
public class MobEffectWarpWard extends MobEffect {

	public static MobEffect instance = null; // assigned at registration time, same convention as its siblings

	public MobEffectWarpWard() {
		super(MobEffectCategory.BENEFICIAL, 0xE0F2F7); // the original's colour
	}

	@Override
	public boolean applyEffectTick(LivingEntity target, int amplifier) {
		return true;
	}
}

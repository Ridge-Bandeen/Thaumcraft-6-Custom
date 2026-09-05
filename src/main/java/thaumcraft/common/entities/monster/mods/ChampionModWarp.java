package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;

/**
 * Replaces the old (1.12.2) {@code ChampionModWarp}. Type 1 (offensive) - real, as-is: a real 33% chance to
 * give a hit player 1-3 temporary warp.
 */
public class ChampionModWarp implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		if (boss.getRandom().nextFloat() < 0.33f && target instanceof Player player) {
			ThaumcraftCapabilities.getWarp(player).add(IPlayerWarp.EnumWarpType.TEMPORARY, 1 + boss.getRandom().nextInt(3));
		}
		return amount;
	}
}

package thaumcraft.common.lib.utils;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The looting level of a killing blow - what 1.12 handed every {@code dropFewItems} and drop event, and Thaumcraft's
 * drop chances lean on: the Looting enchantment on whatever the killer was holding.
 */
public final class LootingHelper {

	private LootingHelper() {
	}

	public static int level(DamageSource source) {
		if (source != null && source.getEntity() instanceof LivingEntity killer) {
			return killer.level().registryAccess().lookup(Registries.ENCHANTMENT)
					.flatMap(registry -> registry.get(Enchantments.LOOTING))
					.map(looting -> EnchantmentHelper.getEnchantmentLevel(looting, killer))
					.orElse(0);
		}
		return 0;
	}
}

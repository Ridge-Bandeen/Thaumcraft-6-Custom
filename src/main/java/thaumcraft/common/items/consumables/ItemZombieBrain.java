package thaumcraft.common.items.consumables;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;

/**
 * Replaces the old (1.12.2) {@code ItemZombieBrain} - a real, fully reachable food item (a random vanilla
 * Zombie kill has a chance to drop one - see {@code Thaumcraft#onLivingDrops} in the main mod class) that
 * fills a little hunger and, on a 10% chance, adds 1 point of <em>permanent</em> Warp, otherwise 1-3 points
 * of <em>temporary</em> Warp - the original's own risk/reward: a free, common-ish food source that's
 * genuinely risky to eat regularly. The Hunger-effect-on-eat part is handled declaratively by
 * {@code FoodProperties#effect} at registration; only the warp addition needs a real Java hook here.
 */
public class ItemZombieBrain extends Item {

	public ItemZombieBrain(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
		ItemStack result = super.finishUsingItem(stack, level, livingEntity);
		if (!level.isClientSide && livingEntity instanceof Player player) {
			if (level.getRandom().nextFloat() < 0.1f) {
				ThaumcraftApi.internalMethods.addWarpToPlayer(player, 1, IPlayerWarp.EnumWarpType.NORMAL);
			} else {
				ThaumcraftApi.internalMethods.addWarpToPlayer(player, 1 + level.getRandom().nextInt(3), IPlayerWarp.EnumWarpType.TEMPORARY);
			}
		}
		return result;
	}
}

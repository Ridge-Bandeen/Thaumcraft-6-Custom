package thaumcraft.common.items.consumables;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;

/**
 * Replaces the old (1.12.2) {@code ItemSanitySoap} - hold right-click (block-style use animation) for
 * ~4.75s (95 of its 100-tick use duration) to consume it and clear all Warp: every point of Temporary Warp,
 * plus 1 point of Normal Warp (matching the original's own {@code amt}/removal split exactly). Real,
 * already-built API end to end - {@code ThaumcraftCapabilities.getWarp}/{@code addWarpToPlayer} were both
 * shipped early this session with no consumer until {@link thaumcraft.common.items.consumables.ItemZombieBrain}
 * added the first one a moment ago; this is the second.
 *
 * Simplified vs the original: no bonus extra point removed while standing in Purifying Fluid or under
 * {@code PotionWarpWard} - both are still-unbuilt/blocked subsystems (a custom finite fluid; a MobEffect with
 * no other consumer yet), so the soap just always removes the base amount. No client-side bubble particle FX
 * during the use-tick or on release - visual-only, dropped like everywhere else this session.
 */
public class ItemSanitySoap extends Item {

	private static final int USE_DURATION = 100;

	public ItemSanitySoap(Properties properties) {
		super(properties);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return USE_DURATION;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BLOCK;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(player.getItemInHand(hand));
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
		int ticksUsed = getUseDuration(stack, livingEntity) - timeLeft;
		if (ticksUsed <= 95 || !(livingEntity instanceof Player player)) return;
		stack.shrink(1);
		if (level.isClientSide) return;
		cleanse(player);
	}

	/** The actual warp-clearing logic, split out so GameTests can call it directly rather than depending
	 * on holding a real item down for 95 ticks. */
	public static void cleanse(Player player) {
		IPlayerWarp warp = ThaumcraftCapabilities.getWarp(player);
		if (warp.get(IPlayerWarp.EnumWarpType.NORMAL) > 0) {
			ThaumcraftApi.internalMethods.addWarpToPlayer(player, -1, IPlayerWarp.EnumWarpType.NORMAL);
		}
		if (warp.get(IPlayerWarp.EnumWarpType.TEMPORARY) > 0) {
			ThaumcraftApi.internalMethods.addWarpToPlayer(player, -warp.get(IPlayerWarp.EnumWarpType.TEMPORARY), IPlayerWarp.EnumWarpType.TEMPORARY);
		}
	}
}

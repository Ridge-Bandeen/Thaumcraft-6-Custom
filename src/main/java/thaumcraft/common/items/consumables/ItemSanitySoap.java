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
 * As the original: a point more while under Warp Ward, and another standing in Purifying Fluid; using it finishes on its
 * own after 95 ticks, with bubbles and a soft hiss while scrubbing and a burst of bubbles at the end.
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
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (USE_DURATION - remaining > 95) entity.releaseUsingItem();
		if (level.isClientSide) {
			if (level.random.nextFloat() < 0.2f) {
				level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), net.minecraft.sounds.SoundEvents.CHORUS_FLOWER_DEATH,
						net.minecraft.sounds.SoundSource.PLAYERS, 0.1f, 1.5f + level.random.nextFloat() * 0.2f, false);
			}
			for (int a = 0; a < 10; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleBubble(level, entity.getX() - 0.5 + level.random.nextFloat(),
						entity.getBoundingBox().minY + level.random.nextFloat() * entity.getBbHeight(), entity.getZ() - 0.5 + level.random.nextFloat(), 1.0f, 0.8f, 0.9f);
			}
		}
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
		int ticksUsed = getUseDuration(stack, livingEntity) - timeLeft;
		if (ticksUsed <= 95 || !(livingEntity instanceof Player player)) return;
		stack.shrink(1);
		if (level.isClientSide) {
			level.playLocalSound(player.getX(), player.getY(), player.getZ(), thaumcraft.common.registry.ThaumcraftSounds.CRAFTSTART.get(),
					net.minecraft.sounds.SoundSource.PLAYERS, 0.25f, 1.0f, false);
			for (int a = 0; a < 40; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleBubble(level, player.getX() - 0.5 + level.random.nextFloat() * 1.5,
						player.getBoundingBox().minY + level.random.nextFloat() * player.getBbHeight(), player.getZ() - 0.5 + level.random.nextFloat() * 1.5, 1.0f, 0.7f, 0.9f);
			}
			return;
		}
		cleanse(player);
	}

	/** The actual warp-clearing logic, split out so GameTests can call it directly rather than depending
	 * on holding a real item down for 95 ticks. */
	public static void cleanse(Player player) {
		IPlayerWarp warp = ThaumcraftCapabilities.getWarp(player);
		int amt = 1;
		if (player.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.WARP_WARD)) amt++;
		if (player.level().getBlockState(player.blockPosition()).is(thaumcraft.api.blocks.BlocksTC.purifyingFluid.get())) amt++;
		if (warp.get(IPlayerWarp.EnumWarpType.NORMAL) > 0) {
			ThaumcraftApi.internalMethods.addWarpToPlayer(player, -amt, IPlayerWarp.EnumWarpType.NORMAL);
		}
		if (warp.get(IPlayerWarp.EnumWarpType.TEMPORARY) > 0) {
			ThaumcraftApi.internalMethods.addWarpToPlayer(player, -warp.get(IPlayerWarp.EnumWarpType.TEMPORARY), IPlayerWarp.EnumWarpType.TEMPORARY);
		}
	}
}

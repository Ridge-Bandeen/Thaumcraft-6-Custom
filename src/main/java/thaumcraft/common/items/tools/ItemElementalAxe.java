package thaumcraft.common.items.tools;

import java.util.List;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Replaces the old (1.12.2) {@code ItemElementalAxe}, the Axe of the Stream: as the original, holding right-click
 * pulls every item within 10 blocks (a box) toward you - 0.3 along the line to a point half your height below your
 * feet, plus a 0.1 lift, each axis capped at 0.25 - with blue bubbles rising off each one.
 */
public class ItemElementalAxe extends AxeItem {

	public ItemElementalAxe(Tier tier, Properties properties) {
		super(tier, properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResultHolder.success(player.getItemInHand(hand));
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public void onUseTick(Level level, LivingEntity player, ItemStack stack, int remainingUseDuration) {
		List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, new AABB(player.position(), player.position()).inflate(10.0));
		for (ItemEntity item : nearby) {
			if (item.isRemoved()) continue;
			// as the original: (item - player) + half the player's height, normalised
			Vec3 away = new Vec3(item.getX() - player.getX(), item.getY() - player.getY() + player.getBbHeight() / 2.0f, item.getZ() - player.getZ());
			double d = away.length();
			if (d <= 0) continue;
			away = away.scale(1.0 / d);
			Vec3 motion = item.getDeltaMovement().add(-away.x * 0.3, -(away.y * 0.3 - 0.1), -away.z * 0.3);
			item.setDeltaMovement(
					Math.clamp(motion.x, -0.25, 0.25),
					Math.clamp(motion.y, -0.25, 0.25),
					Math.clamp(motion.z, -0.25, 0.25));
			if (level.isClientSide) {
				var r = level.random;
				thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleBubble(level, item.getX() + (r.nextFloat() - r.nextFloat()) * 0.2f,
						item.getY() + item.getBbHeight() + (r.nextFloat() - r.nextFloat()) * 0.2f, item.getZ() + (r.nextFloat() - r.nextFloat()) * 0.2f,
						0.33f, 0.33f, 1.0f);
			}
		}
	}
}

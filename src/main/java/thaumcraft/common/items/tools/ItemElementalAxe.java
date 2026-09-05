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
 * Replaces the old (1.12.2) {@code ItemElementalAxe} (the Water-aspect elemental tool). Real ability, ported
 * as-is: hold right-click to pull every nearby item entity within 10 blocks gently toward you (capped speed
 * per axis), same "magnet" effect the original had via a bow-style multi-tick use action. The original's
 * client-only crucible-bubble particle trail on each pulled item is dropped (a purely cosmetic per-item-per-
 * tick VFX, not worth chasing for this pass).
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
		List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(10.0));
		for (ItemEntity item : nearby) {
			if (item.isRemoved()) continue;
			Vec3 toPlayer = player.position().add(0, player.getBbHeight() / 2.0, 0).subtract(item.position());
			double distance = toPlayer.length() + 0.1;
			Vec3 pull = toPlayer.scale(0.3 / distance);
			Vec3 motion = item.getDeltaMovement().add(pull.x, pull.y + 0.1, pull.z);
			item.setDeltaMovement(
					Math.clamp(motion.x, -0.25, 0.25),
					Math.clamp(motion.y, -0.25, 0.25),
					Math.clamp(motion.z, -0.25, 0.25));
		}
	}
}

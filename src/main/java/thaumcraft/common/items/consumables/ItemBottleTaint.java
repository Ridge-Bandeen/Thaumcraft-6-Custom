package thaumcraft.common.items.consumables;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.projectile.EntityBottleTaint;

/**
 * Replaces the old (1.12.2) {@code ItemBottleTaint} - a thrown debuff bomb, crafted in the Crucible from a
 * Phial and Flux+Water essentia (see {@code data/thaumcraft/recipe/bottle_taint.json}). Right-click throws
 * a real {@link EntityBottleTaint}, same shape as {@link ItemAlumentum} (its closest already-shipped
 * sibling) rather than the original's raw {@code EntityThrowable}-spawning code. Unlike Alumentum's
 * overridden-and-hardcoded throw velocity, the original {@code EntityBottleTaint} never overrode
 * {@code shoot()}, so its call-site values (0.66f velocity, 1.0f inaccuracy) are the real effective ones,
 * reproduced here directly.
 */
public class ItemBottleTaint extends Item {

	public ItemBottleTaint(Properties properties) {
		super(properties.stacksTo(8));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EGG_THROW,
				SoundSource.NEUTRAL, 0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));

		if (!level.isClientSide) {
			EntityBottleTaint bottle = new EntityBottleTaint(player, level);
			bottle.shootFromRotation(player, player.getXRot(), player.getYRot(), -5.0f, 0.66f, 1.0f);
			level.addFreshEntity(bottle);
		}

		stack.consume(1, player);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}

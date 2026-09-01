package thaumcraft.common.items.consumables;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.projectile.EntityAlumentum;

/**
 * Replaces the old (1.12.2) {@code ItemAlumentum} - a throwable fire bomb, crafted in the Crucible from
 * coal (see {@code data/thaumcraft/recipe/alumentum.json}). Right-click throws a real
 * {@link EntityAlumentum}, same shape as vanilla's own {@code SnowballItem#use} (which this closely
 * follows) rather than the original's raw {@code EntityThrowable}-spawning code.
 *
 * The throw velocity/inaccuracy (0.75f/2.0f) matches the original's actual effective values, not the
 * literal numbers at its own call site - the original {@code EntityAlumentum} overrode
 * {@code EntityThrowable#shoot(x,y,z,velocity,inaccuracy)} to hardcode velocity to 0.75f regardless of
 * what was passed in, so the "0.4f" the original {@code ItemAlumentum} passed was actually discarded at
 * runtime. Reproduced here as the real 0.75f directly rather than the misleading literal.
 */
public class ItemAlumentum extends Item {

	public ItemAlumentum(Properties properties) {
		super(properties.stacksTo(64).rarity(Rarity.UNCOMMON));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EGG_THROW,
				SoundSource.NEUTRAL, 0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));

		if (!level.isClientSide) {
			EntityAlumentum alumentum = new EntityAlumentum(player, level);
			alumentum.shootFromRotation(player, player.getXRot(), player.getYRot(), -5.0f, 0.75f, 2.0f);
			level.addFreshEntity(alumentum);
		}

		stack.consume(1, player);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}

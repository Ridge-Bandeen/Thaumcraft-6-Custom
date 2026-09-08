package thaumcraft.common.items.consumables;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.projectile.EntityCausalityCollapser;

/**
 * Replaces the old (1.12.2) {@code ItemCausalityCollapser} - real port, found and closed while reviewing this
 * port's own "dead/blocked" candidates for real remaining gaps vs. genuine design cuts (see the port-tracking
 * memory). Previously skipped as blocked on {@link thaumcraft.common.entities.EntityFluxRift}, which is real
 * now (built later for Void Siphon). A thrown grenade that both explodes and collapses any nearby real flux
 * rifts - same real {@link ItemBottleTaint}/{@link thaumcraft.common.items.resources.ItemAlumentum} thrown-item
 * shape (this port's own closest already-shipped siblings).
 */
public class ItemCausalityCollapser extends Item {

	public ItemCausalityCollapser(Properties properties) {
		super(properties.stacksTo(16));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EGG_THROW,
				SoundSource.NEUTRAL, 0.3f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));

		if (!level.isClientSide) {
			EntityCausalityCollapser projectile = new EntityCausalityCollapser(player, level);
			projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), -5.0f, 0.8f, 2.0f);
			level.addFreshEntity(projectile);
		}

		stack.consume(1, player);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}

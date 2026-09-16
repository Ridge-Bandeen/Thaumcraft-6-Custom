package thaumcraft.common.items.tools;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.RechargeHelper;
import thaumcraft.common.entities.projectile.EntityGrapple;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * Replaces the old (1.12.2) {@code ItemGrappleGun} - a rechargable (100 max charge, same
 * {@link IRechargable}/{@link RechargeHelper} pattern already shipped for the Boots of the Traveller) hookshot
 * launcher. Fires one {@link EntityGrapple} per charge, discarding any grapple already pulling this player
 * first (only one active at a time - see that class's own doc comment). The original's "loaded" item-model
 * override (a bow-like drawn/undrawn appearance) is wired in {@code ThaumcraftClient} against
 * {@link EntityGrapple#hasActive} directly, needing no stored per-stack state.
 *
 * Real custom {@code ice} fire sound restored 2026-09-15 (was a vanilla {@code TRIDENT_THROW} stand-in) -
 * see {@link ThaumcraftSounds}'s own doc comment.
 */
public class ItemGrappleGun extends Item implements IRechargable {

	public ItemGrappleGun(Properties properties) {
		super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
	}

	@Override
	public int getMaxCharge(ItemStack stack, LivingEntity player) {
		return 100;
	}

	@Override
	public EnumChargeDisplay showInHud(ItemStack stack, LivingEntity player) {
		return EnumChargeDisplay.NORMAL;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), ThaumcraftSounds.ICE.get(),
				SoundSource.PLAYERS, 3.0f, 0.8f + level.getRandom().nextFloat() * 0.1f);

		if (!level.isClientSide && RechargeHelper.getCharge(stack) > 0) {
			EntityGrapple.discardActiveFor(player);
			EntityGrapple grapple = new EntityGrapple(player, level);
			grapple.shootFromRotation(player, player.getXRot(), player.getYRot(), -5.0f, 1.5f, 0.0f);
			if (level.addFreshEntity(grapple)) {
				RechargeHelper.consumeCharge(stack, player, 1);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}
}

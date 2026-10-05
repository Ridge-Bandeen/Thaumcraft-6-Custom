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
 * first (only one active at a time - see that class's own doc comment). As the original the hook leaves from beside
 * the firing hand (and, as the original's arithmetic had it, shifted along z by the look's height), and the gun's
 * "loaded" mark ({@link thaumcraft.api.items.ThaumcraftDataComponents#GRAPPLE_LOADED}, its model's fired look) is set
 * when it fires and cleared once its hook is gone.
 *
 * Real custom {@code ice} fire sound restored 2026-09-15 (was a vanilla {@code TRIDENT_THROW} stand-in) -
 * see {@link ThaumcraftSounds}'s own doc comment.
 */
public class ItemGrappleGun extends Item implements IRechargable {

	public ItemGrappleGun(Properties properties) {
		super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
		if (!level.isClientSide && entity instanceof LivingEntity living && !EntityGrapple.hasActive(living)
				&& stack.getOrDefault(thaumcraft.api.items.ThaumcraftDataComponents.GRAPPLE_LOADED.get(), false)) {
			stack.remove(thaumcraft.api.items.ThaumcraftDataComponents.GRAPPLE_LOADED.get());
		}
	}

	/** As the original: re-equip only when the loaded mark flips. */
	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		if (oldStack.is(this) && newStack.is(this)) {
			var key = thaumcraft.api.items.ThaumcraftDataComponents.GRAPPLE_LOADED.get();
			return oldStack.getOrDefault(key, false) != newStack.getOrDefault(key, false);
		}
		return !ItemStack.isSameItem(oldStack, newStack);
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
			EntityGrapple grapple = new EntityGrapple(player, level, hand);
			grapple.shootFromRotation(player, player.getXRot(), player.getYRot(), -5.0f, 1.5f, 0.0f);
			int side = hand == InteractionHand.MAIN_HAND ? 1 : -1;
			double px = -net.minecraft.util.Mth.cos((player.getYRot() - 0.5f) / 180.0f * 3.141593f) * 0.2f * side;
			double pz = -net.minecraft.util.Mth.sin((player.getYRot() - 0.5f) / 180.0f * 3.141593f) * 0.3f * side;
			var look = player.getLookAngle();
			grapple.setPos(grapple.getX() + px + look.x, grapple.getY(), grapple.getZ() + pz + look.y);
			if (level.addFreshEntity(grapple)) {
				grapple.claim(player);
				RechargeHelper.consumeCharge(stack, player, 1);
				stack.set(thaumcraft.api.items.ThaumcraftDataComponents.GRAPPLE_LOADED.get(), true);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}
}

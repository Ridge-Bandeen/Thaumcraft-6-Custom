package thaumcraft.common.items.armor;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftArmorMaterials;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.RechargeHelper;

/**
 * Replaces the old (1.12.2) {@code ItemBootsTraveller}. A rare boots piece that draws a trickle of stored
 * vis charge (via {@link IRechargable}/{@link RechargeHelper} - already ported this session, previously
 * exercised in {@code ThaumcraftGameTests} only via a synthetic stand-in item, this is the real thing) and,
 * while worn and moving forward with charge remaining, grants a speed boost. Recharged externally by
 * standing it on a {@link thaumcraft.common.tiles.devices.TileRechargePedestal} - already shipped.
 *
 * Simplified vs the original:
 * <ul>
 * <li>The original directly modified movement (raw {@code moveRelative}/{@code jumpMovementFactor} field
 * hacking, plus a client-side {@code stepHeight} override to ease climbing) - not safely reproducible the
 * same way against the modern {@code LivingEntity} API from an {@code Item} class. Reproduces the same
 * player-facing effect (faster forward movement, easier vertical movement) via
 * {@code MobEffects.MOVEMENT_SPEED}/{@code JUMP}, the same technique already used for
 * {@code BlockPavingStoneTravel}'s "traveller" speed boost this session - not a new pattern.</li>
 * <li>Dropped the original's NBT-based "energy" smoothing counter (decoupling "is charge available"
 * from "when does the next unit get consumed" across a 60-tick window) - same overall charge economy (1
 * unit per 3 seconds of use), just checked directly against {@link RechargeHelper#getCharge} every 60 ticks
 * instead of through an extra buffered field.</li>
 * </ul>
 */
public class ItemBootsTraveller extends ArmorItem implements IRechargable {

	public ItemBootsTraveller(Properties properties) {
		super(ThaumcraftArmorMaterials.TRAVELLER, ArmorItem.Type.BOOTS, properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		super.inventoryTick(stack, level, entity, slotId, isSelected);
		if (level.isClientSide || !(entity instanceof Player player) || player.getItemBySlot(EquipmentSlot.FEET) != stack) {
			return;
		}

		if (player.tickCount % 60 == 0 && RechargeHelper.getCharge(stack) > 0) {
			RechargeHelper.consumeCharge(stack, player, 1);
		}
		if (RechargeHelper.getCharge(stack) > 0 && !player.getAbilities().flying && player.zza > 0.0f) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false));
			if (!player.onGround()) {
				player.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 0, false, false));
			}
		}
	}

	@Override
	public int getMaxCharge(ItemStack stack, LivingEntity player) {
		return 240;
	}

	@Override
	public EnumChargeDisplay showInHud(ItemStack stack, LivingEntity player) {
		return EnumChargeDisplay.PERIODIC;
	}
}

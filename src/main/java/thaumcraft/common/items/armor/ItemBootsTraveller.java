package thaumcraft.common.items.armor;

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
 * Replaces the old (1.12.2) {@code ItemBootsTraveller}, as the original. Every 20 ticks the boots burn their "energy"
 * down, spending one charge (vis, from a Recharge Pedestal) for each minute's worth. While charged and running forward
 * (not flying) they push you along the ground (less in water), give better control in the air, and let you step up a
 * whole block (until you sneak or take them off - see {@code WornGearEvents}); jumps go higher
 * ({@code WornGearEvents#travellerJump}) and falls hurt less.
 */
public class ItemBootsTraveller extends ArmorItem implements IRechargable {

	/** The original's stepHeight 1.0 (from the usual 0.6). */
	public static final net.minecraft.resources.ResourceLocation STEP_ID = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "traveller_step");

	public ItemBootsTraveller(Properties properties) {
		super(ThaumcraftArmorMaterials.TRAVELLER, ArmorItem.Type.BOOTS, properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		super.inventoryTick(stack, level, entity, slotId, isSelected);
		if (!(entity instanceof Player player) || player.getItemBySlot(EquipmentSlot.FEET) != stack) return;
		boolean hasCharge = RechargeHelper.getCharge(stack) > 0;
		if (!level.isClientSide && player.tickCount % 20 == 0) {
			int e = stack.getOrDefault(thaumcraft.api.items.ThaumcraftDataComponents.TRAVELLER_ENERGY.get(), 0);
			if (e > 0) {
				e--;
			} else if (RechargeHelper.consumeCharge(stack, player, 1)) {
				e = 60;
			}
			stack.set(thaumcraft.api.items.ThaumcraftDataComponents.TRAVELLER_ENERGY.get(), e);
		}
		if (hasCharge && !player.getAbilities().flying && player.zza > 0.0f) {
			if (!player.isShiftKeyDown()) {
				var step = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
				if (step != null && !step.hasModifier(STEP_ID)) {
					step.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(STEP_ID, 0.4,
							net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
				}
			}
			if (player.onGround()) {
				float bonus = player.isInWater() ? 0.05f / 4.0f : 0.05f;
				player.moveRelative(bonus, new net.minecraft.world.phys.Vec3(0, 0, 1));
			} else {
				if (player.isInWater()) player.moveRelative(0.025f, new net.minecraft.world.phys.Vec3(0, 0, 1));
				// the original raised jumpMovementFactor to 0.05 from the usual 0.02: the extra 0.03 of air control
				player.moveRelative(0.03f, new net.minecraft.world.phys.Vec3(player.xxa, 0, player.zza));
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

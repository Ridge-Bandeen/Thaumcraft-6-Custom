package thaumcraft.common.lib.events;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.items.armor.ItemFortressArmor;

/**
 * Real port of the 2 combat effects the original's Fortress Helm masks gave (from its stale, never-ported
 * {@code EntityEvents}' own {@code LivingHurtEvent} handler) - Angry Ghost (chance to Wither whoever hits
 * you) and Sipping Fiend (chance to lifesteal off whoever you hit). Both ported as-is: the chance scales
 * with the damage amount ({@code amount/10} and {@code amount/12} respectively - the same real formulas the
 * original used), checked directly off the wearer's real equipped {@link ItemFortressArmor} helm.
 *
 * The third mask, Grinning Devil (damping Warp EVENT severity), is a real, documented cut - it's built on
 * top of the original's own Warp-event-severity system, which lives entirely in a separate, still-stale,
 * never-ported {@code WarpEvents} class. Building that whole system just to support one mask's real effect
 * is a substantially bigger, separate task, not attempted this pass - the mask item/component itself is
 * still real and craftable, it just has no gameplay effect yet, same "ship the real item, the effect is
 * real follow-up work" precedent this session has used elsewhere.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class FortressMaskEvents {

	private static final int MASK_ANGRY_GHOST = 1;
	private static final int MASK_SIPPING_FIEND = 2;

	@SubscribeEvent
	public static void onDamage(LivingIncomingDamageEvent event) {
		LivingEntity victim = event.getEntity();
		if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

		if (victim instanceof Player victimPlayer && hasMask(victimPlayer, MASK_ANGRY_GHOST)
				&& victim.getRandom().nextFloat() < event.getAmount() / 10.0f) {
			attacker.addEffect(new MobEffectInstance(MobEffects.WITHER, 80));
		}

		if (attacker instanceof Player attackerPlayer && hasMask(attackerPlayer, MASK_SIPPING_FIEND)
				&& attacker.getRandom().nextFloat() < event.getAmount() / 12.0f) {
			attackerPlayer.heal(1.0f);
		}
	}

	private static boolean hasMask(Player player, int mask) {
		ItemStack helm = player.getItemBySlot(EquipmentSlot.HEAD);
		if (!(helm.getItem() instanceof ItemFortressArmor)) return false;
		Integer stackMask = helm.get(ThaumcraftDataComponents.FORTRESS_MASK.get());
		return stackMask != null && stackMask == mask;
	}
}

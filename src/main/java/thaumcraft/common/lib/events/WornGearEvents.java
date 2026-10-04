package thaumcraft.common.lib.events;

import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.items.IWornGear;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.WornGearHelper;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;

/**
 * Real tick/death hooks standing in for the original's own real Baubles-slot equivalents - see
 * {@link IWornGear}'s own doc comment for why {@link WornGearHelper#getWornItems} (hotbar + offhand) is the
 * real source of "worn" items here instead of a dedicated bauble slot.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class WornGearEvents {

	@SubscribeEvent
	public static void tick(PlayerTickEvent.Post event) {
		Player player = event.getEntity();
		if (player.level().isClientSide) return;
		for (ItemStack stack : WornGearHelper.getWornItems(player)) {
			if (stack.getItem() instanceof IWornGear wornGear) {
				wornGear.wornTick(stack, player);
			}
		}
	}

	/** Real port of the original's {@code PlayerEvents#onDeath} - minus the Baubles-API-specific stat/
	 * criteria-trigger calls ({@code StatList.getObjectUseStats}/{@code CriteriaTriggers.USED_TOTEM}), which
	 * only matter for the vanilla "used a totem" advancement/stat and aren't worth chasing down a real
	 * equivalent for on top of everything else this pass changed. Everything that actually matters to
	 * gameplay - canceling the death, consuming the charm, the heal/Regeneration/Absorption grant, and the
	 * vanilla totem-use visual cue (entity event id 35) - is ported as-is. */
	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		for (ItemStack stack : WornGearHelper.getWornItems(player)) {
			if (stack.getItem() == ItemsTC.charmUndying.get()) {
				stack.shrink(1);
				player.setHealth(1.0f);
				player.removeAllEffects();
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
				player.level().broadcastEntityEvent(player, (byte) 35);
				event.setCanceled(true);
				return;
			}
		}
	}

	/** The original's PlayerEvents tick: Boots of the Traveller's step-up ends when you sneak or take them off. */
	@SubscribeEvent
	public static void travellerStep(PlayerTickEvent.Post event) {
		Player player = event.getEntity();
		var step = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
		if (step == null || !step.hasModifier(thaumcraft.common.items.armor.ItemBootsTraveller.STEP_ID)) return;
		if (player.isShiftKeyDown() || player.getItemBySlot(EquipmentSlot.FEET).getItem() != ItemsTC.travellerBoots.get()) {
			step.removeModifier(thaumcraft.common.items.armor.ItemBootsTraveller.STEP_ID);
		}
	}

	/** The original's playerJumps: charged Boots of the Traveller add 0.275 to a jump. */
	@SubscribeEvent
	public static void travellerJump(net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
		if (boots.getItem() == ItemsTC.travellerBoots.get() && thaumcraft.api.items.RechargeHelper.getCharge(boots) > 0) {
			player.setDeltaMovement(player.getDeltaMovement().add(0, 0.275, 0));
		}
	}

	/** Real port of both halves of the original's {@code PlayerEvents#onFallDamage} - the Cloud Ring (Baubles
	 * slot, now {@link WornGearHelper#getWornItems}) and Traveller's Boots (a real armor piece, checked
	 * directly off {@link EquipmentSlot#FEET} - it was never a Baubles item to begin with). Traveller's Boots
	 * was previously a real, flagged, still-open gap noted right here in this method's own earlier doc
	 * comment; now closed. Matches the original exactly: Traveller's Boots halves the raw amount (minus 1,
	 * floored at 0) unconditionally (no charge check - that's a separate mechanic, the speed/jump boost
	 * {@code ItemBootsTraveller#inventoryTick} already owns); the Cloud Ring's own reduction is applied
	 * afterward, on top of whatever Traveller's Boots already left. */
	@SubscribeEvent
	public static void fallDamage(LivingIncomingDamageEvent event) {
		if (!event.getSource().is(DamageTypes.FALL) || !(event.getEntity() instanceof Player player)) return;

		if (player.getItemBySlot(EquipmentSlot.FEET).getItem() == ItemsTC.travellerBoots.get()) {
			float reduced = Math.max(0.0f, event.getAmount() / 2.0f - 1.0f);
			if (reduced < 1.0f) {
				event.setCanceled(true);
				return;
			}
			event.setAmount(reduced);
		}

		boolean wearingRing = false;
		for (ItemStack stack : WornGearHelper.getWornItems(player)) {
			if (stack.getItem() == ItemsTC.cloudRing.get()) { wearingRing = true; break; }
		}
		if (!wearingRing) return;
		float reduced = Math.max(0.0f, event.getAmount() / 3.0f - 2.0f);
		if (reduced < 1.0f) {
			event.setCanceled(true);
		} else {
			event.setAmount(reduced);
		}
	}

	/** Real port of the original's {@code PlayerEvents#pickupXP} (Curiosity Band) - halves the orb's value
	 * and has a small, orb-size-scaled chance to convert the difference into free research progress in a
	 * random category (theory or observation). */
	@SubscribeEvent
	public static void xpPickup(PlayerXpEvent.PickupXp event) {
		Player player = event.getEntity();
		if (player.level().isClientSide) return;
		ExperienceOrb orb = event.getOrb();
		if (orb.getValue() <= 1) return;
		boolean wearingBand = false;
		for (ItemStack stack : WornGearHelper.getWornItems(player)) {
			if (stack.getItem() == ItemsTC.curiosityBand.get()) { wearingBand = true; break; }
		}
		if (!wearingBand) return;

		int d = orb.getValue() / 2;
		orb.value -= d;
		float r = player.getRandom().nextFloat();
		ResearchCategory[] categories = ResearchCategories.researchCategories.values().toArray(new ResearchCategory[0]);
		if (categories.length == 0) return;
		ResearchCategory cat = categories[player.getRandom().nextInt(categories.length)];
		if (r < 0.05f * d) {
			ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.THEORY, cat, 1);
		} else if (r < 0.2f * d) {
			ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, cat, 1);
		}
	}
}

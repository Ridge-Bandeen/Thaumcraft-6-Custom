package thaumcraft.common.lib.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.items.armor.ItemFortressArmor;

/**
 * The original Fortress armour's {@code ISpecialArmor} side (its {@code getProperties}/{@code damageArmor}), which modern
 * Minecraft has no hook for. On top of its ordinary armour value each piece takes a share of the damage before armour
 * applies - its armour value / 25 of ordinary hits, / 35 of magic, / 20 of fire and explosions, none of what bypasses
 * armour - at most what its durability can take, wearing the piece by the amount it took (not for falls). Each piece
 * worn also adds a point of armour (two with a masked helm) and a point of toughness.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class FortressArmorEvents {

	private static final ResourceLocation ARMOR_ID = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fortress_set_armor");
	private static final ResourceLocation TOUGHNESS_ID = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fortress_set_toughness");
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };

	private FortressArmorEvents() {
	}

	private static boolean isMagic(DamageSource source) {
		return source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.THORNS);
	}

	/** The original's getProperties ratio for one piece (priority 1 for magic/fire/explosions, else 0). */
	private static double ratio(ArmorItem armor, DamageSource source) {
		int def = armor.getDefense();
		if (isMagic(source)) return def / 35.0;
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) return def / 20.0;
		if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return 0.0;
		return def / 25.0;
	}

	/** Forge's ISpecialArmor.applyArmor for the Fortress pieces: the shares, capped by durability, together at most all of it. */
	@SubscribeEvent(priority = EventPriority.LOW)
	static void absorb(LivingIncomingDamageEvent event) {
		LivingEntity entity = event.getEntity();
		DamageSource source = event.getSource();
		float damage = event.getAmount();
		if (damage <= 0) return;
		double[] ratios = new double[SLOTS.length];
		double total = 0;
		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack stack = entity.getItemBySlot(SLOTS[i]);
			if (!(stack.getItem() instanceof ItemFortressArmor armor)) continue;
			double r = ratio(armor, source);
			int absorbMax = stack.getMaxDamage() + 1 - stack.getDamageValue();
			if (damage * r > absorbMax) r = absorbMax / (double) damage;
			ratios[i] = r;
			total += r;
		}
		if (total <= 0) return;
		if (total > 1.0) {
			for (int i = 0; i < ratios.length; i++) ratios[i] /= total;
			total = 1.0;
		}
		for (int i = 0; i < SLOTS.length; i++) {
			if (ratios[i] <= 0) continue;
			double absorbed = damage * ratios[i];
			if (absorbed > 0 && !source.is(DamageTypes.FALL)) {
				entity.getItemBySlot(SLOTS[i]).hurtAndBreak((int) Math.max(1, absorbed), entity, SLOTS[i]);
			}
		}
		event.setAmount((float) (damage - damage * total));
	}

	/** The set's extra armour and toughness (the original's ArmorProperties Armor/Toughness and getArmorDisplay). */
	@SubscribeEvent
	static void setBonus(PlayerTickEvent.Post event) {
		Player player = event.getEntity();
		if (player.level().isClientSide) return;
		int pieces = 0;
		int masks = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD }) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.getItem() instanceof ItemFortressArmor) {
				pieces++;
				if (stack.has(ThaumcraftDataComponents.FORTRESS_MASK.get())) masks++;
			}
		}
		set(player.getAttribute(Attributes.ARMOR), ARMOR_ID, pieces * (1 + masks));
		set(player.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS_ID, pieces);
	}

	private static void set(AttributeInstance attribute, ResourceLocation id, double amount) {
		if (attribute == null) return;
		AttributeModifier current = attribute.getModifier(id);
		if (current != null && current.amount() == amount) return;
		if (current != null) attribute.removeModifier(id);
		if (amount != 0) attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
	}
}

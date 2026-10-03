package thaumcraft.common.lib.events;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.config.ThaumcraftConfig;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Real port of the old (1.12.2) {@code PlayerEvents#handleRunicArmor} - the real payoff for
 * {@link thaumcraft.common.lib.crafting.RunicArmorRecipe}'s own real
 * {@link ThaumcraftDataComponents#RUNIC_CHARGE}: a real vis-fed absorption shield. Every 20 ticks, sums the
 * Runic Charge across the 4 armor slots and every bauble worn (in the hotbar/offhand here - see {@code WornGearHelper};
 * the original read its Baubles slots).
 * If that real max shrinks (armor removed/swapped), the player's real current
 * {@link Player#getAbsorptionAmount}) is trimmed down to match, matching the original exactly.
 *
 * Every tick otherwise: while below the real max and off cooldown, drains 1 vis from the real local aura
 * (via {@link AuraHandler}, real - the same source {@link thaumcraft.common.items.casters.ItemCaster} already
 * draws from) to regenerate 1 point of real Absorption, then goes on a real short cooldown before the next
 * point; a real longer cooldown kicks in specifically once the shield hits 0 (fully spent), matching the
 * original's own real two-speed "trickle regen, but a real pause right after being fully broken" pacing.
 *
 * {@code shieldRecharge}/{@code shieldWait}/{@code shieldCost} are now real, live {@link ThaumcraftConfig}
 * values (COMMON config, `runicShielding` category) - the original's own real default values remain the
 * shipped defaults, but are no longer hardcoded now that {@link ThaumcraftConfig} exists. Per-player tracking uses plain maps keyed by real entity id, same
 * real shape the original itself used - unlike the original, real logout cleanup ({@link #onLogout}) is
 * included here for real: a real, persistent server can reuse a low entity id for a completely different
 * player after one logs off, and a leftover cooldown timestamp under that id would otherwise wrongly gate
 * the new player's own shield (this exact collision is also why GameTests must call {@link #forget} before
 * asserting - GameTest mock players are especially prone to id reuse across separate test instances in the
 * same JVM run).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class RunicShieldEvents {

	private static final EquipmentSlot[] ARMOR_SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private static final Map<Integer, Integer> maxCharge = new HashMap<>();
	private static final Map<Integer, Integer> lastCharge = new HashMap<>();
	private static final Map<Integer, Long> nextCycle = new HashMap<>();

	@SubscribeEvent
	public static void tick(PlayerTickEvent.Post event) {
		checkRunicShield(event.getEntity());
	}

	/** As the original: a runic-shielded player whose shield takes a blow shows the shield flash. */
	@SubscribeEvent
	public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof Player player && !player.level().isClientSide()
				&& (int) player.getAbsorptionAmount() > 0 && maxCharge.containsKey(player.getId()) && lastCharge.containsKey(player.getId())) {
			thaumcraft.common.lib.network.fx.PacketFXShield.send(player, event.getSource());
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		forget(event.getEntity());
	}

	/** Clears this player's real tracked shield state (max/last charge, cooldown) - called for real on
	 * logout, and by GameTests before asserting to guard against a stale entry left by an unrelated earlier
	 * test's own mock player reusing the same real entity id in the same JVM run. */
	public static void forget(net.minecraft.world.entity.Entity entity) {
		int id = entity.getId();
		maxCharge.remove(id);
		lastCharge.remove(id);
		nextCycle.remove(id);
	}

	/** The real tick logic, split out so GameTests can call it directly against a real player rather than
	 * needing to construct a real {@link PlayerTickEvent.Post}. */
	public static void checkRunicShield(Player player) {
		if (player.level().isClientSide) return;
		if (player.tickCount % 20 == 0) {
			updateMaxCharge(player);
		}
		tickShield(player);
	}

	private static void updateMaxCharge(Player player) {
		int id = player.getId();
		int max = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			max += runicCharge(player.getItemBySlot(slot));
		}
		// the original also counted its Baubles slots; baubles are worn in the hotbar/offhand here
		for (ItemStack worn : thaumcraft.api.items.WornGearHelper.getWornItems(player)) {
			if (worn.is(thaumcraft.common.lib.crafting.RunicArmorRecipe.BAUBLES)) max += runicCharge(worn);
		}
		Integer previous = maxCharge.get(id);
		if (previous != null && previous > max) {
			player.setAbsorptionAmount(Math.max(0.0f, player.getAbsorptionAmount() - (previous - max)));
		}
		if (max > 0) {
			maxCharge.put(id, max);
		} else {
			maxCharge.remove(id);
		}
	}

	private static void tickShield(Player player) {
		int id = player.getId();
		Integer max = maxCharge.get(id);
		if (max == null) return;

		long time = System.currentTimeMillis();
		int charge = (int) player.getAbsorptionAmount();
		Integer previousCharge = lastCharge.get(id);
		if (charge == 0 && previousCharge != null && previousCharge > 0) {
			nextCycle.put(id, time + ThaumcraftConfig.SHIELD_WAIT_MS.get());
			lastCharge.put(id, 0);
		}

		long next = nextCycle.getOrDefault(id, 0L);
		Level level = player.level();
		BlockPos pos = player.blockPosition();
		double cost = ThaumcraftConfig.SHIELD_COST.get();
		if (charge < max && next < time
				&& !AuraHandler.shouldPreserveAura(level, player, pos)
				&& AuraHandler.getVis(level, pos) >= cost) {
			AuraHandler.drainVis(level, pos, (float) cost, false);
			nextCycle.put(id, time + ThaumcraftConfig.SHIELD_RECHARGE_MS.get());
			player.setAbsorptionAmount(charge + 1);
			lastCharge.put(id, charge + 1);
		}
	}

	private static int runicCharge(ItemStack stack) {
		Integer level = stack.get(ThaumcraftDataComponents.RUNIC_CHARGE.get());
		return level == null ? 0 : level;
	}
}

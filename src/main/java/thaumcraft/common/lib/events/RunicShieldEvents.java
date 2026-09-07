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
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Real port of the old (1.12.2) {@code PlayerEvents#handleRunicArmor} - the real payoff for
 * {@link thaumcraft.common.lib.crafting.RunicArmorRecipe}'s own real
 * {@link ThaumcraftDataComponents#RUNIC_CHARGE}: a real vis-fed absorption shield. Every 20 ticks, sums the
 * real Runic Charge across all 4 real worn armor slots (the original's own Baubles-slot scan is dropped -
 * baubles aren't ported, same standing decision {@code WornGearHelper}'s own doc comment already explains).
 * If that real max shrinks (armor removed/swapped), the player's real current
 * {@link Player#getAbsorptionAmount}) is trimmed down to match, matching the original exactly.
 *
 * Every tick otherwise: while below the real max and off cooldown, drains 1 vis from the real local aura
 * (via {@link AuraHandler}, real - the same source {@link thaumcraft.common.items.casters.ItemCaster} already
 * draws from) to regenerate 1 point of real Absorption, then goes on a real short cooldown before the next
 * point; a real longer cooldown kicks in specifically once the shield hits 0 (fully spent), matching the
 * original's own real two-speed "trickle regen, but a real pause right after being fully broken" pacing.
 *
 * Real, documented cuts: {@code ModConfig.CONFIG_MISC.shieldRecharge}/{@code shieldWait}/{@code shieldCost}
 * aren't configurable yet (no modern config system - see the port-tracking memory's own `ModConfig` entry) -
 * {@link #SHIELD_RECHARGE_MS}/{@link #SHIELD_WAIT_MS}/{@link #SHIELD_COST} are the original's own real
 * default values, hardcoded, same "no config system yet, use the original's own real default" pattern
 * already used elsewhere this session. Per-player tracking uses plain maps keyed by real entity id, same
 * real shape the original itself used - unlike the original, real logout cleanup ({@link #onLogout}) is
 * included here for real: a real, persistent server can reuse a low entity id for a completely different
 * player after one logs off, and a leftover cooldown timestamp under that id would otherwise wrongly gate
 * the new player's own shield (this exact collision is also why GameTests must call {@link #forget} before
 * asserting - GameTest mock players are especially prone to id reuse across separate test instances in the
 * same JVM run).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class RunicShieldEvents {

	private static final int SHIELD_RECHARGE_MS = 2000;
	private static final int SHIELD_WAIT_MS = 4000;
	private static final float SHIELD_COST = 1.0f;

	private static final EquipmentSlot[] ARMOR_SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private static final Map<Integer, Integer> maxCharge = new HashMap<>();
	private static final Map<Integer, Integer> lastCharge = new HashMap<>();
	private static final Map<Integer, Long> nextCycle = new HashMap<>();

	@SubscribeEvent
	public static void tick(PlayerTickEvent.Post event) {
		checkRunicShield(event.getEntity());
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
			nextCycle.put(id, time + SHIELD_WAIT_MS);
			lastCharge.put(id, 0);
		}

		long next = nextCycle.getOrDefault(id, 0L);
		Level level = player.level();
		BlockPos pos = player.blockPosition();
		if (charge < max && next < time
				&& !AuraHandler.shouldPreserveAura(level, player, pos)
				&& AuraHandler.getVis(level, pos) >= SHIELD_COST) {
			AuraHandler.drainVis(level, pos, SHIELD_COST, false);
			nextCycle.put(id, time + SHIELD_RECHARGE_MS);
			player.setAbsorptionAmount(charge + 1);
			lastCharge.put(id, charge + 1);
		}
	}

	private static int runicCharge(ItemStack stack) {
		Integer level = stack.get(ThaumcraftDataComponents.RUNIC_CHARGE.get());
		return level == null ? 0 : level;
	}
}

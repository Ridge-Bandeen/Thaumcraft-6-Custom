package thaumcraft.common.lib.research;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.IScanThing;
import thaumcraft.api.research.ScanningManager;

/**
 * Replaces the old (1.12.2) {@code ScanEnchantment} - scanning a real enchanted item counts as discovering
 * that enchantment. Reachable now that {@link ScanningManager}'s own real inventory recursion and
 * {@link ScanningManager#getItemFromParms} are ported (scan a chest, and everything really inside it gets
 * scanned too).
 *
 * <p><b>Real, necessary structural change from the original</b>, not a simplification for its own sake: the
 * original registered one instance of this class <i>per enchantment</i>, walking
 * {@code Enchantment.REGISTRY} at mod init and baking a fixed research key into each. Modern enchantments
 * are <b>data-driven</b> (a datapack registry, {@code Registries.ENCHANTMENT}) - they simply don't exist yet
 * at mod-setup time, so there is nothing to enumerate. This is instead a single registered instance that
 * resolves the key dynamically off whatever the scanned stack really carries, and deliberately returns the
 * first enchantment the player does <i>not</i> already know: scanning a multi-enchanted item repeatedly
 * walks through all of them one per scan, preserving the original's own "every enchantment is separately
 * discoverable" behaviour with one handler instead of dozens.
 *
 * <p>Research keys are the enchantment's own real namespaced id ({@code !minecraft:sharpness}) rather than
 * the original's own translation key ({@code !enchantment.protect.all}) - the modern id is both stable and
 * mod-unique, and nothing in this port's own converted research data consumes either form.
 */
public class ScanEnchantment implements IScanThing {

	@Override
	public boolean checkThing(Player player, Object obj) {
		return firstUnknownEnchantment(player, obj) != null;
	}

	@Override
	public String getResearchKey(Player player, Object obj) {
		Holder<Enchantment> enchantment = firstUnknownEnchantment(player, obj);
		return enchantment == null ? null : keyFor(enchantment);
	}

	private Holder<Enchantment> firstUnknownEnchantment(Player player, Object obj) {
		if (obj == null) return null;
		ItemStack stack = ScanningManager.getItemFromParms(player, obj);
		if (stack.isEmpty()) return null;
		Holder<Enchantment> first = null;
		// getEnchantmentsForCrafting is the real accessor that also covers enchanted books' own separate
		// stored-enchantments component, matching the original's own EnchantmentHelper.getEnchantments.
		for (Holder<Enchantment> enchantment : EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet()) {
			String key = keyFor(enchantment);
			if (key == null) continue;
			if (first == null) first = enchantment;
			if (!ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(key)) return enchantment;
		}
		// Every enchantment on it is already known - still a real match (the scan "recognises" it), the
		// manager just won't progress anything, same as any other already-known scan target.
		return first;
	}

	private String keyFor(Holder<Enchantment> enchantment) {
		return enchantment.unwrapKey().map(key -> "!" + key.location()).orElse(null);
	}
}

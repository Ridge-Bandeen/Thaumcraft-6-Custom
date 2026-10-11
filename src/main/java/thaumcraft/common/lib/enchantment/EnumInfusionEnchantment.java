package thaumcraft.common.lib.enchantment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.ItemAbilities;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.ThaumcraftDataComponents;

/**
 * Replaces the old (1.12.2) {@code EnumInfusionEnchantment}. Same 13 entries (tool-class restriction, max
 * level, research key) and the same NBT-style static accessors, now backed by
 * {@link ThaumcraftDataComponents#INFUSION_ENCHANTMENTS} instead of a raw {@code "infench"} NBTTagList - see
 * that field's own doc comment.
 *
 * {@link #isValidTool} replaces the big inline tool/armor/bauble/rechargable-matching block that used to live
 * in {@code InfusionEnchantmentRecipe.matches()} - {@code ItemTool}/{@code getToolClasses()} don't exist
 * anymore, so tool-class membership is now a vanilla {@link ItemTags} check or the matching dig {@link ItemAbilities}, and "weapon" is now a scan of the
 * item's own {@link DataComponents#ATTRIBUTE_MODIFIERS} for an {@code ATTACK_DAMAGE} entry rather than
 * {@code getAttributeModifiers(EntityEquipmentSlot.MAINHAND)}. {@link #isValidTool} still recognizes the
 * bauble-slot classes as vocabulary ("bauble", "amulet", "belt", "ring") but they can never match - Baubles/
 * Curios equipment isn't ported (see the port-tracking memory notes). None of the 13 entries below actually
 * need those classes ({@link #SWIFT}/{@link #AGILE}/{@link #INFESTED} are plain armor-slot-restricted), so
 * this doesn't cut anything reachable yet - it only matters once a future bauble-restricted enchantment or
 * real Curios items exist.
 *
 * <p><b>Real finding, not a porting gap</b>: {@link #SWIFT}/{@link #AGILE}/{@link #INFESTED}/{@link
 * #VISBATTERY}/{@link #VISCHARGE} have no gameplay effect anywhere in this port - confirmed by checking the
 * ORIGINAL 1.12.2 codebase directly (a real {@code git grep} across the whole original source tree, not
 * assumed): none of these five are referenced by name anywhere outside this one enum. They're real,
 * craftable, storable enchantment levels with no wired-up effect in the original mod either - the {@code
 * "?"} placeholder research key on {@code VISBATTERY}/{@code VISCHARGE} is the same real tell. Nothing to
 * port here; the data component still stores a level for them correctly if a future recipe or feature ever
 * wants one.
 */
public enum EnumInfusionEnchantment {
	COLLECTOR(ImmutableSet.of("axe", "pickaxe", "shovel", "weapon"), 1, "INFUSIONENCHANTMENT"),
	DESTRUCTIVE(ImmutableSet.of("axe", "pickaxe", "shovel"), 1, "INFUSIONENCHANTMENT"),
	BURROWING(ImmutableSet.of("axe", "pickaxe"), 1, "INFUSIONENCHANTMENT"),
	SOUNDING(ImmutableSet.of("pickaxe"), 4, "INFUSIONENCHANTMENT"),
	REFINING(ImmutableSet.of("pickaxe"), 4, "INFUSIONENCHANTMENT"),
	ARCING(ImmutableSet.of("weapon"), 4, "INFUSIONENCHANTMENT"),
	ESSENCE(ImmutableSet.of("weapon"), 5, "INFUSIONENCHANTMENT"),
	VISBATTERY(ImmutableSet.of("chargable"), 3, "?"),
	VISCHARGE(ImmutableSet.of("chargable"), 1, "?"),
	SWIFT(ImmutableSet.of("boots"), 4, "IEARMOR"),
	AGILE(ImmutableSet.of("legs"), 1, "IEARMOR"),
	INFESTED(ImmutableSet.of("chest"), 1, "IETAINT"),
	LAMPLIGHT(ImmutableSet.of("axe", "pickaxe", "shovel"), 1, "INFUSIONENCHANTMENT");

	public final Set<String> toolClasses;
	public final int maxLevel;
	public final String research;

	EnumInfusionEnchantment(Set<String> toolClasses, int maxLevel, String research) {
		this.toolClasses = toolClasses;
		this.maxLevel = maxLevel;
		this.research = research;
	}

	/** Is {@code stack} a valid infusion target for this enchantment's tool-class restriction? */
	public boolean isValidTool(ItemStack stack) {
		if (toolClasses.contains("all")) return true;
		if (toolClasses.contains("weapon") && hasAttackDamage(stack)) return true;
		// the original's tool classes: the vanilla tool tag, or the matching dig ability (the Primal Crusher is tagged
		// only as a shovel but digs as a pickaxe too, as its tool classes said)
		if (toolClasses.contains("axe") && (stack.is(ItemTags.AXES) || stack.canPerformAction(ItemAbilities.AXE_DIG))) return true;
		if (toolClasses.contains("pickaxe") && (stack.is(ItemTags.PICKAXES) || stack.canPerformAction(ItemAbilities.PICKAXE_DIG))) return true;
		if (toolClasses.contains("shovel") && (stack.is(ItemTags.SHOVELS) || stack.canPerformAction(ItemAbilities.SHOVEL_DIG))) return true;
		if (toolClasses.contains("hoe") && (stack.is(ItemTags.HOES) || stack.canPerformAction(ItemAbilities.HOE_DIG))) return true;
		if (toolClasses.contains("sword") && (stack.is(ItemTags.SWORDS) || stack.canPerformAction(ItemAbilities.SWORD_DIG))) return true;
		if (stack.getItem() instanceof ArmorItem armor) {
			String slot = switch (armor.getType()) {
				case HELMET -> "helm";
				case CHESTPLATE -> "chest";
				case LEGGINGS -> "legs";
				case BOOTS -> "boots";
				default -> "none";
			};
			if (toolClasses.contains("armor") || toolClasses.contains(slot)) return true;
		}
		if (toolClasses.contains("chargable") && stack.getItem() instanceof IRechargable) return true;
		return false;
	}

	private static boolean hasAttackDamage(ItemStack stack) {
		ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (mods == null) return false;
		for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
			if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) return true;
		}
		return false;
	}

	public static List<EnumInfusionEnchantment> getInfusionEnchantments(ItemStack stack) {
		Map<String, Integer> levels = stack.get(ThaumcraftDataComponents.INFUSION_ENCHANTMENTS.get());
		List<EnumInfusionEnchantment> list = new ArrayList<>();
		if (levels != null) {
			for (String name : levels.keySet()) {
				try {
					list.add(valueOf(name));
				} catch (IllegalArgumentException ignored) {
					// Stale/foreign data (e.g. from a since-removed enchantment) - skip rather than crash.
				}
			}
		}
		return list;
	}

	public static int getInfusionEnchantmentLevel(ItemStack stack, EnumInfusionEnchantment enchantment) {
		Map<String, Integer> levels = stack.get(ThaumcraftDataComponents.INFUSION_ENCHANTMENTS.get());
		return levels != null ? levels.getOrDefault(enchantment.name(), 0) : 0;
	}

	public static void addInfusionEnchantment(ItemStack stack, EnumInfusionEnchantment ie, int level) {
		if (stack.isEmpty() || level > ie.maxLevel) {
			return;
		}
		Map<String, Integer> current = stack.get(ThaumcraftDataComponents.INFUSION_ENCHANTMENTS.get());
		if (current != null && current.getOrDefault(ie.name(), 0) >= level) {
			return;
		}
		Map<String, Integer> updated = new HashMap<>(current != null ? current : Map.of());
		updated.put(ie.name(), level);
		stack.set(ThaumcraftDataComponents.INFUSION_ENCHANTMENTS.get(), Map.copyOf(updated));
	}
}

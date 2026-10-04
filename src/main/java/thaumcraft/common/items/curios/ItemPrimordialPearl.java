package thaumcraft.common.items.curios;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Replaces the old (1.12.2) {@code ItemPrimordialPearl} - a fragment of a collapsed {@code EntityFluxRift},
 * dropped mid-degraded (see that entity's own doc comment for the drop roll). The original's 3-tier
 * pearl/nodule/mote visual split (driven by its 0-8 damage value, via an {@code IItemPropertyGetter}) is
 * reproduced here unchanged using vanilla's real damage-value system ({@code Properties.durability(8)} +
 * {@link ItemStack#getDamageValue()}), with the same "type" model-override predicate the original asset
 * used - see {@code primordial_pearl.json}'s overrides and {@code Thaumcraft#clientSetup}'s registration of
 * it.
 *
 * As the original it is a crafting remnant: a recipe that uses it (at a table, the workbench or an Infusion Altar) hands
 * it back one step more worn, until the eighth use takes it.
 */
public class ItemPrimordialPearl extends Item {

	public ItemPrimordialPearl(Properties properties) {
		super(properties.stacksTo(1).durability(8).rarity(Rarity.UNCOMMON));
	}

	@Override
	public Component getName(ItemStack stack) {
		int damage = stack.getDamageValue();
		String suffix = damage < 3 ? "pearl" : damage < 6 ? "nodule" : "mote";
		return Component.translatable("item.thaumcraft.primordial_pearl." + suffix);
	}

	@Override
	public boolean hasCraftingRemainingItem(ItemStack stack) {
		return stack.getDamageValue() < 7;
	}

	@Override
	public ItemStack getCraftingRemainingItem(ItemStack stack) {
		if (!hasCraftingRemainingItem(stack)) return ItemStack.EMPTY;
		ItemStack next = stack.copy();
		next.setDamageValue(stack.getDamageValue() + 1);
		return next;
	}

	public static float typeProperty(ItemStack stack) {
		int damage = stack.getDamageValue();
		if (damage < 3) return 0.0f;
		if (damage < 6) return 1.0f;
		return 2.0f;
	}
}

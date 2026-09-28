package thaumcraft.common.items.curios;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

/**
 * Replaces the old (1.12.2) {@code ItemEnchantmentPlaceholder} - restored 2026-09-27 alongside real per-stage
 * research gating (see {@link thaumcraft.common.lib.research.ResearchRequirements}'s own doc comment). Not a real
 * held item in practice, same as the original: it exists only so the research JSON's own {@code enchanted_placeholder}
 * item-spec entries have a real registry id to key off while the loader resolves them into a real {@code EnchantSpec}
 * (see that record's own doc comment) - "must be carrying a real tool bearing this vanilla enchantment" - matching
 * every appearance of it since the true original never actually expected anyone to craft or hold this item either.
 * Kept as a real, glowing, uncraftable item for the same reason the original shipped one at all: a stray reference
 * to it (a JEI-alike, a creative search) should show something coherent, not a missing-model placeholder.
 */
public class ItemEnchantmentPlaceholder extends Item {

	public ItemEnchantmentPlaceholder(Properties properties) {
		super(properties.stacksTo(1).rarity(Rarity.RARE));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.enchanted_placeholder.text").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_AQUA));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}

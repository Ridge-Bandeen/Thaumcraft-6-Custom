package thaumcraft.common.items.baubles;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.api.items.IWarpingGear;

/**
 * Replaces the old (1.12.2) {@code ItemVoidseerCharm}. A real risk/reward trinket: the more permanent Warp
 * the wearer has racked up, the cheaper their vis casting gets (up to 25% at 100+ warp) - ported as-is from
 * the original's {@code getVisDiscount}. {@link #getWarp} is also ported as-is (a small amount proportional
 * to the charm's own current discount), even though - per {@link IWarpingGear}'s own note plus a full
 * {@code git grep} of the original codebase - {@code getWarp} is never actually consumed by anything there
 * beyond a tooltip display; it's genuinely inert in the original too, not a porting gap invented here.
 */
public class ItemVoidseerCharm extends Item implements IVisDiscountGear, IWarpingGear {

	public ItemVoidseerCharm(Properties properties) {
		super(properties.rarity(Rarity.RARE));
	}

	@Override
	public int getVisDiscount(ItemStack stack, Player player) {
		IPlayerWarp warp = ThaumcraftCapabilities.getWarp(player);
		int permanent = Math.min(100, warp.get(EnumWarpType.PERMANENT));
		return (int) (permanent / 100.0f * 25.0f);
	}

	@Override
	public int getWarp(ItemStack itemstack, Player player) {
		return getVisDiscount(itemstack, player) / 5;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.voidseer_charm.text").withStyle(ChatFormatting.DARK_BLUE, ChatFormatting.ITALIC));
	}
}

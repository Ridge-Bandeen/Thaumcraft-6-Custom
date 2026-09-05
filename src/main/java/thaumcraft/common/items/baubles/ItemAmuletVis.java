package thaumcraft.common.items.baubles;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import thaumcraft.api.items.IWornGear;
import thaumcraft.api.items.RechargeHelper;
import thaumcraft.api.items.WornGearHelper;

/**
 * Replaces the old (1.12.2) {@code ItemAmuletVis}. Every {@link #interval} ticks, scans the player's real
 * worn items (see {@link IWornGear}'s own doc comment) plus their armor for the first {@code IRechargable}
 * item and tops it up by 1 charge from the local aura - same "auto-recharge whatever's nearby" role the
 * original filled by also scanning Baubles slots. Two variants, same as the original: "found" (damage 0,
 * slower interval, common rarity) and "crafted" (damage 1 in the original, split into its own item here -
 * faster interval, rare).
 */
public class ItemAmuletVis extends Item implements IWornGear {

	private final int interval;

	public ItemAmuletVis(Properties properties, int interval, boolean crafted) {
		super(properties.rarity(crafted ? Rarity.RARE : Rarity.UNCOMMON));
		this.interval = interval;
	}

	@Override
	public void wornTick(ItemStack stack, Player player) {
		if (player.tickCount % interval != 0) return;
		for (ItemStack candidate : WornGearHelper.getWornItems(player)) {
			if (RechargeHelper.rechargeItem(player.level(), candidate, player.blockPosition(), player, 1) > 0) return;
		}
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (RechargeHelper.rechargeItem(player.level(), player.getItemBySlot(slot), player.blockPosition(), player, 1) > 0) return;
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.amulet_vis.text").withStyle(ChatFormatting.AQUA));
	}
}

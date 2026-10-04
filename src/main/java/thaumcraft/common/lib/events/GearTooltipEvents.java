package thaumcraft.common.lib.events;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.api.items.IWarpingGear;
import thaumcraft.api.items.RechargeHelper;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;

/**
 * Replaces the "cross-cutting item info" fragment of the old (1.12.2) {@code PlayerEvents}' own
 * {@code tooltipEvent} - see {@code WornGearEvents}/{@code PassiveResearchEvents}/{@code MobDeathDropEvents}'
 * own doc comments for the same "extract the one real mechanic, not the whole stale file" precedent this
 * follows (this fragment just happened to live in {@code PlayerEvents} rather than {@code EntityEvents}).
 * Global, not per-item - applies to any real item implementing one of these interfaces, matching the
 * original's own single {@code ItemTooltipEvent} subscriber rather than touching every item class.
 *
 * Real, still-meaningful pieces kept: vis charge (any real {@link IRechargable}, e.g.
 * {@link thaumcraft.common.items.armor.ItemBootsTraveller} - via {@link RechargeHelper#getCharge}), Warping
 * (any real {@link IWarpingGear} - 14 real items implement it), Vis discount (any real
 * {@link IVisDiscountGear} - same 14), essentia contents (any real {@link IEssentiaContainerItem}, e.g. a
 * loaded crystal essence, via its own real {@link AspectList}), and infused-tool enchantment names (any real
 * tool carrying {@link EnumInfusionEnchantment} data, matching {@code ToolEvents}' own real accessor).
 *
 * Runic charge (any real item carrying {@link ThaumcraftDataComponents#RUNIC_CHARGE}, e.g. armor infused by
 * {@link thaumcraft.common.lib.crafting.RunicArmorRecipe}) is also shown now - the original's other half of
 * this same method read a {@code "TC.RUNIC"} NBT tag, and this port's own equivalent data component didn't
 * exist yet when this class was first written; closed once {@code RunicArmorRecipe}/{@code RunicShieldEvents}
 * shipped.
 *
 * The Warping total is the original's getFinalWarp: the {@link IWarpingGear} value plus the item's own
 * {@link ThaumcraftDataComponents#ITEM_WARP} (the original's "TC.WARP" tag).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public class GearTooltipEvents {

	@SubscribeEvent
	public static void onItemTooltip(ItemTooltipEvent event) {
		ItemStack stack = event.getItemStack();
		Player player = event.getEntity();
		List<Component> tooltip = event.getToolTip();

		if (stack.getItem() instanceof IRechargable) {
			int charge = RechargeHelper.getCharge(stack);
			tooltip.add(Component.translatable("tc.charge").append(" " + charge).withStyle(ChatFormatting.YELLOW));
		}

		Integer runicCharge = stack.get(ThaumcraftDataComponents.RUNIC_CHARGE.get());
		if (runicCharge != null && runicCharge > 0) {
			tooltip.add(Component.translatable("item.runic.charge").append(" +" + runicCharge).withStyle(ChatFormatting.GOLD));
		}

		// NeoForge documents ItemTooltipEvent#getEntity() as nullable (e.g. the creative-search tooltip index
		// builds tooltips at resource-reload time with no player) - the gear's own warp needs the player.
		int warp = player != null ? thaumcraft.common.lib.events.WarpEvents.getFinalWarp(stack, player)
				: stack.getOrDefault(ThaumcraftDataComponents.ITEM_WARP.get(), 0);
		if (warp > 0) {
			tooltip.add(Component.translatable("item.warping").append(" " + warp).withStyle(ChatFormatting.DARK_PURPLE));
		}

		if (player != null && stack.getItem() instanceof IVisDiscountGear discountGear) {
			int discount = discountGear.getVisDiscount(stack, player);
			if (discount > 0) {
				tooltip.add(Component.translatable("tc.visdiscount").append(": " + discount + "%").withStyle(ChatFormatting.DARK_PURPLE));
			}
		}

		if (stack.getItem() instanceof IEssentiaContainerItem container) {
			AspectList aspects = container.getAspects(stack);
			if (aspects != null && aspects.size() > 0) {
				for (Aspect tag : aspects.getAspectsSortedByName()) {
					tooltip.add(Component.literal(tag.getName() + " x" + aspects.getAmount(tag)));
				}
			}
		}

		List<EnumInfusionEnchantment> enchantments = EnumInfusionEnchantment.getInfusionEnchantments(stack);
		for (EnumInfusionEnchantment ie : enchantments) {
			int level = EnumInfusionEnchantment.getInfusionEnchantmentLevel(stack, ie);
			if (level <= 0) continue;
			Component line = Component.translatable("enchantment.infusion." + ie.name()).withStyle(ChatFormatting.GOLD);
			if (ie.maxLevel > 1) {
				line = line.copy().append(" ").append(Component.translatable("enchantment.level." + level).withStyle(ChatFormatting.GOLD));
			}
			tooltip.add(Math.min(1, tooltip.size()), line);
		}
	}
}

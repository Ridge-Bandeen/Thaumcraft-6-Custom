package thaumcraft.common.items.baubles;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.IWornGear;
import thaumcraft.api.items.RechargeHelper;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * Replaces the old (1.12.2) {@code ItemVerdantCharm}. Was one item with an NBT byte "type" (0/1/2) driving
 * an item-model property override; split here into 3 separate items ({@link #TYPE_BASE}/{@link #TYPE_LIFE}/
 * {@link #TYPE_SUSTAIN} - "verdant_charm"/"verdant_charm_life"/"verdant_charm_sustain") matching every other
 * metadata-variant item this port has split, reusing the original's own numbered overlay textures 1:1
 * (type 0 -> {@code verdant_charm_over_0.png}, etc. - already present as separate item model layers).
 *
 * Real, charge-gated cure/heal/sustain priority chain, ported as-is from the original's {@code onWornTick}
 * (only one effect fires per tick, checked in this exact order): cure Wither (20 charge) &gt; cure Poison
 * (10) &gt; cure Flux Taint (5) &gt; (life only) heal 1 HP (5) &gt; (sustain only) refill air (1) or feed
 * +1 food/+0.3 saturation (1). See {@link IWornGear}'s own doc comment for why this fires via a hotbar/
 * offhand scan instead of a dedicated bauble slot.
 */
public class ItemVerdantCharm extends Item implements IRechargable, IWornGear {

	public static final int TYPE_BASE = 0;
	public static final int TYPE_LIFE = 1;
	public static final int TYPE_SUSTAIN = 2;

	private final int type;

	public ItemVerdantCharm(Properties properties, int type) {
		super(properties.rarity(Rarity.RARE));
		this.type = type;
	}

	@Override
	public void wornTick(ItemStack stack, Player player) {
		if (player.tickCount % 20 != 0) return;

		if (player.hasEffect(MobEffects.WITHER) && RechargeHelper.consumeCharge(stack, player, 20)) {
			player.removeEffect(MobEffects.WITHER);
			return;
		}
		if (player.hasEffect(MobEffects.POISON) && RechargeHelper.consumeCharge(stack, player, 10)) {
			player.removeEffect(MobEffects.POISON);
			return;
		}
		if (player.hasEffect(ThaumcraftMobEffects.FLUX_TAINT) && RechargeHelper.consumeCharge(stack, player, 5)) {
			player.removeEffect(ThaumcraftMobEffects.FLUX_TAINT);
			return;
		}
		if (type == TYPE_LIFE && player.getHealth() < player.getMaxHealth() && RechargeHelper.consumeCharge(stack, player, 5)) {
			player.heal(1.0f);
			return;
		}
		if (type == TYPE_SUSTAIN) {
			if (player.getAirSupply() < 100 && RechargeHelper.consumeCharge(stack, player, 1)) {
				player.setAirSupply(300);
				return;
			}
			if (player.getFoodData().needsFood() && RechargeHelper.consumeCharge(stack, player, 1)) {
				player.getFoodData().eat(1, 0.3f);
			}
		}
	}

	@Override
	public int getMaxCharge(ItemStack stack, LivingEntity player) {
		return 200;
	}

	@Override
	public EnumChargeDisplay showInHud(ItemStack stack, LivingEntity player) {
		return EnumChargeDisplay.NORMAL;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if (type == TYPE_LIFE) {
			tooltip.add(Component.translatable("item.verdant_charm.life.text").withStyle(ChatFormatting.GOLD));
		} else if (type == TYPE_SUSTAIN) {
			tooltip.add(Component.translatable("item.verdant_charm.sustain.text").withStyle(ChatFormatting.GOLD));
		}
	}
}

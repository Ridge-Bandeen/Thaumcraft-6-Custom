package thaumcraft.common.items.tools;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IWarpingGear;

/**
 * Replaces the old (1.12.2) {@code ItemCrimsonBlade} - {@link thaumcraft.common.entities.monster.boss.EntityCultistLeader}'s
 * default weapon (the Void Sword is an easy-difficulty-only substitute). Real self-repair and
 * {@link IWarpingGear} implementer (+2 warp, double {@link ItemVoidSword}'s own +1 - see that class's own
 * doc comment for the shared shape/rationale, including the same repair-ingredient substitution). Real
 * on-hit Weakness AND Hunger application (the original's own extra debuff over the Void Sword's Weakness-
 * only), same PvP-awareness check.
 */
public class ItemCrimsonBlade extends SwordItem implements IWarpingGear {

	public ItemCrimsonBlade(Tier tier, Properties properties) {
		super(tier, properties);
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!target.level().isClientSide
				&& (!(target instanceof Player) || !(attacker instanceof Player) || target.level().getServer().isPvpAllowed())) {
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60));
			target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120));
		}
		return super.hurtEnemy(stack, target, attacker);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		super.inventoryTick(stack, level, entity, slotId, isSelected);
		if (!level.isClientSide && stack.isDamaged() && entity.tickCount % 20 == 0) {
			stack.setDamageValue(stack.getDamageValue() - 1);
		}
	}

	@Override
	public int getWarp(ItemStack itemstack, Player player) {
		return 2;
	}

	/** The original named its Greater Sapping in gold. */
	@Override
	public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
			java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
		tooltip.add(net.minecraft.network.chat.Component.translatable("enchantment.special.sapgreat").withStyle(net.minecraft.ChatFormatting.GOLD));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}

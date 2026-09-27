package thaumcraft.common.items.tools;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IWarpingGear;

/**
 * Replaces the old (1.12.2) {@code ItemVoidSword} - a real {@link SwordItem}, the rare (1-in-5, harder-
 * difficulty-only) alternate weapon drop from {@link thaumcraft.common.entities.monster.cult.EntityCultistKnight}.
 * Real self-repair (regains 1 durability every 20 ticks while damaged, via {@link #inventoryTick} - modern
 * equivalent of the original's {@code onUpdate} override) and real on-hit Weakness application (skipped in
 * unauthorized PvP, matching the original's own {@code isPVPEnabled} check - modern replacement is
 * {@code ServerLevel#getServer().isPvpAllowed()}). Real {@link IWarpingGear} implementer (+1 warp while
 * held), matching the original's own "void" item family risk/reward.
 *
 * Simplified vs the original: no custom tooltip translation key registered yet (the original's own
 * {@code enchantment.special.sapless} lang entry) - a real, minor, documented cosmetic cut.
 */
public class ItemVoidSword extends SwordItem implements IWarpingGear {

	public ItemVoidSword(Tier tier, Properties properties) {
		super(tier, properties);
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!target.level().isClientSide
				&& (!(target instanceof Player) || !(attacker instanceof Player) || target.level().getServer().isPvpAllowed())) {
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60));
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
		return 1;
	}

	/** The original named its Lesser Sapping in gold. */
	@Override
	public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
			java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
		tooltip.add(net.minecraft.network.chat.Component.translatable("enchantment.special.sapless").withStyle(net.minecraft.ChatFormatting.GOLD));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}

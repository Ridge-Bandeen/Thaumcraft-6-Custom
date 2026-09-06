package thaumcraft.common.items.tools;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IWarpingGear;

/**
 * Replaces the old (1.12.2) {@code ItemVoidPickaxe} - a real {@link PickaxeItem} sharing the exact same
 * real mechanics as {@link ItemVoidSword} (its own doc comment covers the rest of what's simplified for
 * this whole "void" item family): self-repair (1 durability every 20 ticks while damaged) and real on-hit
 * Weakness (skipped in unauthorized PvP), plus the same real {@link IWarpingGear} +1 warp while held. The
 * original applied Weakness from a real left-click hook fired before damage resolution
 * ({@code onLeftClickEntity}); modern Minecraft's closest real equivalent is {@link #hurtEnemy}, the same
 * hook the already-shipped {@link ItemVoidSword} uses - fires once the tool actually lands a hit, which is
 * the meaningfully equivalent moment for a real on-hit debuff.
 */
public class ItemVoidPickaxe extends PickaxeItem implements IWarpingGear {

	public ItemVoidPickaxe(Tier tier, Properties properties) {
		super(tier, properties);
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!target.level().isClientSide
				&& (!(target instanceof Player) || !(attacker instanceof Player) || target.level().getServer().isPvpAllowed())) {
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80));
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
}

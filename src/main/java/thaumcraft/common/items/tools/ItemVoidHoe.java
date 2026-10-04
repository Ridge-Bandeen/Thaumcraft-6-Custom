package thaumcraft.common.items.tools;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IWarpingGear;

/** Replaces the old (1.12.2) {@code ItemVoidHoe} - see {@link ItemVoidPickaxe}'s own doc comment, same
 * real mechanics. */
public class ItemVoidHoe extends HoeItem implements IWarpingGear {

	public ItemVoidHoe(Tier tier, Properties properties) {
		super(tier, properties);
	}

	/** The original's onLeftClickEntity: Weakness on any living thing a player swings at (players only with PvP on). */
	@Override
	public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
		if (!player.level().isClientSide && entity instanceof LivingEntity target
				&& (!(target instanceof Player) || player.level().getServer().isPvpAllowed())) {
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80));
		}
		return super.onLeftClickEntity(stack, player, entity);
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

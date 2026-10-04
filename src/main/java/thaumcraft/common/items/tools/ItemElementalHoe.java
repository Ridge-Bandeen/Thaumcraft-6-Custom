package thaumcraft.common.items.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Replaces the old (1.12.2) {@code ItemElementalHoe}, the Hoe of Growth: as the original, a right-click tills the 3x3
 * around the clicked block (a little earthy puff over each, with a poof for the middle one), and if nothing there
 * would till it bonemeals the block instead for 3 durability, with a green mist. Sneaking tills just the one block.
 */
public class ItemElementalHoe extends HoeItem {

	public ItemElementalHoe(Tier tier, Properties properties) {
		super(tier, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player != null && player.isShiftKeyDown()) {
			return super.useOn(context);
		}

		BlockPos center = context.getClickedPos();
		boolean tilledAny = false;
		for (int xx = -1; xx <= 1; xx++) {
			for (int zz = -1; zz <= 1; zz++) {
				BlockPos target = center.offset(xx, 0, zz);
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), context.getClickedFace(), target, false);
				UseOnContext shifted = new UseOnContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(), hit);
				if (super.useOn(shifted).consumesAction()) {
					tilledAny = true;
					if (context.getLevel().isClientSide) {
						thaumcraft.client.fx.ClientFxHandlers.spawnBamf(context.getLevel(), target.getX() + 0.5, target.getY() + 1.01, target.getZ() + 0.5,
								0x4C1F19, xx == 0 && zz == 0, false, net.minecraft.core.Direction.UP);
					}
				}
			}
		}

		if (!tilledAny && player != null) {
			Level level = context.getLevel();
			if (BoneMealItem.applyBonemeal(context.getItemInHand(), level, center, player)) {
				context.getItemInHand().hurtAndBreak(3, player, context.getHand() == net.minecraft.world.InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
				if (level.isClientSide) thaumcraft.client.fx.ClientFxHandlers.spawnBlockMist(level, center, 4259648);
			}
		}
		return InteractionResult.SUCCESS;
	}
}

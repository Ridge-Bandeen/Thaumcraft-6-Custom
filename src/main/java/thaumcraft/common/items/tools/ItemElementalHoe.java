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
 * Replaces the old (1.12.2) {@code ItemElementalHoe} (the Order/Plant/Entropy-aspect elemental tool). Real
 * ability, ported as-is: right-clicking a tillable block tills the full real 3x3 area centered on it (by
 * replaying the vanilla hoe's own real {@code useOn} against the 8 surrounding positions too), and falls
 * back to a real vanilla bonemeal effect (consuming 3 real durability, matching the original) when nothing
 * in the area was actually tillable. Sneaking skips straight to plain vanilla single-block behavior, same as
 * the original.
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
				if (super.useOn(shifted) == InteractionResult.SUCCESS) {
					tilledAny = true;
				}
			}
		}

		if (!tilledAny && player != null) {
			Level level = context.getLevel();
			if (BoneMealItem.applyBonemeal(context.getItemInHand(), level, center, player)) {
				context.getItemInHand().hurtAndBreak(3, player, EquipmentSlot.MAINHAND);
				level.globalLevelEvent(2005, center, 0);
			}
		}
		return InteractionResult.SUCCESS;
	}
}

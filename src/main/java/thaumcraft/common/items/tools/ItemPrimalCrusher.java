package thaumcraft.common.items.tools;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IWarpingGear;

/**
 * Replaces the old (1.12.2) {@code ItemPrimalCrusher} - a real dual pickaxe+shovel tool, extending
 * {@link TieredItem} directly rather than {@link net.minecraft.world.item.PickaxeItem}/{@link
 * net.minecraft.world.item.ShovelItem}: neither single-tag constructor can express "mines both real tag sets
 * at the same real flat speed", so the real {@link Tool} component is built by hand instead (two real {@link
 * Tool.Rule#minesAndDrops} entries, one per real vanilla tag, both at the original's own real flat 8.0 speed).
 *
 * As the original: its tool classes were shovel and pickaxe, so everything either digs well came at its full 8.0 (its
 * hardcoded list only repeated those), which the two tag rules give. Wood, leaves and plants are
 * naturally excluded (neither tag covers them), matching the original's own real {@code canHarvestBlock}
 * exclusion without needing to repeat it.
 *
 * Real mechanics kept: self-repair (1 durability every 20 ticks while damaged, matching {@link
 * ItemVoidPickaxe}'s own real precedent exactly) and a real {@link IWarpingGear} +2 warp while held/worn.
 * The creative tab offers it pre-infused with Destructive and Refining, as the original ({@code ThaumcraftCreativeTabs}).
 */
public class ItemPrimalCrusher extends TieredItem implements IWarpingGear {

	private static final float MINING_SPEED = 8.0F;

	public ItemPrimalCrusher(Tier tier, Properties properties) {
		super(tier, properties.component(DataComponents.TOOL, buildTool(tier)));
	}

	private static Tool buildTool(Tier tier) {
		return new Tool(List.of(
				Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, MINING_SPEED),
				Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_SHOVEL, MINING_SPEED),
				Tool.Rule.deniesDrops(tier.getIncorrectBlocksForDrops())),
				1.0F, 1);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		super.inventoryTick(stack, level, entity, slotId, isSelected);
		if (!level.isClientSide && stack.isDamaged() && entity.tickCount % 20 == 0) {
			stack.setDamageValue(stack.getDamageValue() - 1);
		}
	}

	/** Its tool classes were pickaxe and shovel, so it counts as both for Infusion Enchantment (and other mods asking).
	 * It is only in {@code #minecraft:shovels}, though, which already makes it enchantable for mining (an addon asked:
	 * the pickaxe tag would make it a piercing weapon in TFC). */
	@Override
	public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility ability) {
		return ability == net.neoforged.neoforge.common.ItemAbilities.PICKAXE_DIG || ability == net.neoforged.neoforge.common.ItemAbilities.SHOVEL_DIG;
	}

	@Override
	public int getWarp(ItemStack itemstack, Player player) {
		return 2;
	}
}

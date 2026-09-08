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
 * Real, documented simplification: the original's own {@code getDestroySpeed} gave iron/anvil/rock-material
 * blocks that same flat 8.0 speed while everything else in its own hardcoded ~38-block list (dirt, sand,
 * rails, taint blocks, etc.) fell back to a slower, plain {@code ItemTool}-default speed - this real modern
 * {@link Tool} just gives the whole real {@link BlockTags#MINEABLE_WITH_PICKAXE}/{@link
 * BlockTags#MINEABLE_WITH_SHOVEL} tag set the same flat speed, a real, minor fidelity loss (soft materials
 * dig a bit faster here than the original), in exchange for a real modern tag-driven rule instead of a
 * ~38-block hardcoded list that would need re-deriving by hand anyway (several of the original's own named
 * blocks, e.g. {@code LIT_REDSTONE_ORE}, don't exist as separate blocks any more). Wood/leaves/plants are
 * naturally excluded (neither tag covers them), matching the original's own real {@code canHarvestBlock}
 * exclusion without needing to repeat it.
 *
 * Real mechanics kept: self-repair (1 durability every 20 ticks while damaged, matching {@link
 * ItemVoidPickaxe}'s own real precedent exactly) and a real {@link IWarpingGear} +2 warp while held/worn.
 * Real, documented cut: the original's own {@code getSubItems} pre-infused a creative-tab copy with real
 * Destructive+Refining infusion enchantments - a minor creative-tab convenience, not a real mechanic (a
 * player can still infuse a plain copy at the Infusion Altar); dropped rather than chasing a
 * {@code FillCreativeModeTabEvent} hook for one decorative default.
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

	@Override
	public int getWarp(ItemStack itemstack, Player player) {
		return 2;
	}
}

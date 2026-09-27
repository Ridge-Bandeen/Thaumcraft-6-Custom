package thaumcraft.common.blocks.essentia;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.tiles.devices.TileJarBrain;

/**
 * Replaces the old (1.12.2) {@code BlockJarBrainItem} - restored 2026-09-27 alongside {@link JarBlockItem}. A broken
 * {@link BlockJarBrain} carries its stored XP ({@link ThaumcraftDataComponents#JAR_XP}, see {@link
 * BlockJarBrain#onRemove}) onto the dropped item and restores it on the newly placed tile here; the tooltip shows
 * "N xp" in green, matching the original's own "§a&lt;xp&gt; xp" line exactly.
 */
public class JarBrainBlockItem extends BlockItem {

	public JarBrainBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		Integer xp = context.getItemInHand().get(ThaumcraftDataComponents.JAR_XP.get());
		InteractionResult result = super.place(context);
		if (result.consumesAction() && xp != null && !context.getLevel().isClientSide
				&& context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TileJarBrain tile) {
			tile.setXp(xp);
			tile.setChanged();
		}
		return result;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Integer xp = stack.get(ThaumcraftDataComponents.JAR_XP.get());
		if (xp != null && xp > 0) {
			tooltip.add(Component.literal(xp + " xp").withStyle(ChatFormatting.GREEN));
		}
		super.appendHoverText(stack, context, tooltip, flag);
	}
}

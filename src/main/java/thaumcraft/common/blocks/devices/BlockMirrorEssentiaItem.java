package thaumcraft.common.blocks.devices;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.api.items.ThaumcraftDataComponents.MirrorLink;
import thaumcraft.common.tiles.devices.TileMirrorEssentia;

/**
 * Replaces the old (1.12.2) {@code BlockMirrorItem} for the essentia-transport mirror - real port of
 * {@link BlockMirrorItem}'s own "right-click an unlinked mirror to charge a copy with its coordinates, then
 * place that copy elsewhere to link" workflow (see that class's own doc comment), retargeted at
 * {@link BlockMirrorEssentia}/{@link TileMirrorEssentia}. Reuses the same {@link ThaumcraftDataComponents#MIRROR_LINK}
 * component type as the item-transport mirror - {@code MirrorLink} (dimension + pos) has nothing
 * device-specific about it, so a second parallel component type would just be duplication.
 */
public class BlockMirrorEssentiaItem extends BlockItem {

	public BlockMirrorEssentiaItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!(level.getBlockState(context.getClickedPos()).getBlock() instanceof BlockMirrorEssentia) || context.getPlayer() == null) {
			return super.useOn(context);
		}
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		if (!(level.getBlockEntity(context.getClickedPos()) instanceof TileMirrorEssentia target) || target.isLinkValid()) {
			player.displayClientMessage(Component.translatable("item.thaumcraft.mirror.already_linked").withStyle(ChatFormatting.DARK_PURPLE), true);
			return InteractionResult.FAIL;
		}

		ItemStack held = context.getItemInHand();
		ItemStack charged = held.copyWithCount(1);
		charged.set(ThaumcraftDataComponents.MIRROR_LINK.get(), new MirrorLink(level.dimension(), context.getClickedPos().immutable()));
		level.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 2.0f);
		if (!player.getInventory().add(charged)) {
			player.drop(charged, false);
		}
		if (!player.getAbilities().instabuild) {
			held.shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		MirrorLink link = context.getItemInHand().get(ThaumcraftDataComponents.MIRROR_LINK.get());
		InteractionResult result = super.place(context);
		if (link != null && result.consumesAction() && !context.getLevel().isClientSide
				&& context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TileMirrorEssentia tile) {
			tile.applyLink(link);
		}
		return result;
	}

	/** Restored 2026-09-27 - see {@link BlockMirrorItem#appendHoverText}. */
	@Override
	public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
			java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
		MirrorLink link = stack.get(ThaumcraftDataComponents.MIRROR_LINK.get());
		if (link != null) {
			net.minecraft.core.BlockPos pos = link.pos();
			String dim = link.dimension().location().toString();
			tooltip.add(Component.literal("Linked to " + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " in " + dim));
		}
		super.appendHoverText(stack, context, tooltip, flag);
	}
}

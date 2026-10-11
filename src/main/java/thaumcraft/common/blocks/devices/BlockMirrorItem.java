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
import thaumcraft.common.tiles.devices.TileMirror;

/**
 * Replaces the old (1.12.2) {@code BlockMirrorItem} - the Node Transducer's own {@code BlockItem} doubles as
 * its own linking tool, exactly like the original: right-clicking an existing, not-yet-linked mirror
 * ({@link #useOn}) consumes one from the stack and hands back a "pre-charged" copy carrying that mirror's
 * coordinates ({@link ThaumcraftDataComponents#MIRROR_LINK}, replacing the original's own raw
 * {@code linkX}/{@code linkY}/{@code linkZ}/{@code linkDim} NBT). Placing that charged copy elsewhere
 * ({@link #place}) applies the link to the newly-placed tile, which then confirms the mutual pairing with
 * the mirror it was charged from - see {@link TileMirror#applyLink}. No separate "linking wand" item needed,
 * same as the original.
 */
public class BlockMirrorItem extends BlockItem {

	public BlockMirrorItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!(level.getBlockState(context.getClickedPos()).getBlock() instanceof BlockMirror) || context.getPlayer() == null) {
			return super.useOn(context);
		}
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		if (!(level.getBlockEntity(context.getClickedPos()) instanceof TileMirror target) || target.isLinkValid()) {
			player.displayClientMessage(Component.translatable("item.thaumcraft.mirror.already_linked").withStyle(ChatFormatting.DARK_PURPLE), true);
			return InteractionResult.FAIL;
		}

		ItemStack held = context.getItemInHand();
		ItemStack charged = held.copyWithCount(1);
		charged.set(ThaumcraftDataComponents.MIRROR_LINK.get(), new MirrorLink(level.dimension(), context.getClickedPos().immutable()));
		level.playSound(null, context.getClickedPos(), thaumcraft.common.registry.ThaumcraftSounds.JAR.get(), SoundSource.BLOCKS, 1.0f, 2.0f);
		if (!player.getInventory().add(charged)) {
			player.drop(charged, false);
		}
		if (!player.getAbilities().instabuild) {
			held.shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	/** Restored 2026-09-27: the original's own "Linked to X,Y,Z in &lt;dim&gt;" tooltip line, read off the real
	 * {@link ThaumcraftDataComponents#MIRROR_LINK} component this port already carries. */
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

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		MirrorLink link = context.getItemInHand().get(ThaumcraftDataComponents.MIRROR_LINK.get());
		InteractionResult result = super.place(context);
		if (link != null && result.consumesAction() && !context.getLevel().isClientSide
				&& context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TileMirror tile) {
			tile.applyLink(link);
		}
		return result;
	}
}

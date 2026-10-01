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
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.tiles.essentia.TileJarFillable;

/**
 * Replaces the old (1.12.2) {@code BlockJarItem} - restored 2026-09-27 (the user remembered a picked-up jar keeping
 * its label/contents; a playtest of the shift-tooltip work found jars had neither). A broken {@link BlockJar}/
 * {@link BlockJarVoid} now carries its held essentia ({@link ThaumcraftDataComponents#ASPECTS}) and label ({@link
 * ThaumcraftDataComponents#ASPECT_FILTER}) onto the dropped item (see {@link BlockJar#onRemove}) and restores both
 * on the newly placed tile here ({@link #place}); the label shows in the tooltip the way the original's own
 * "§5&lt;aspect&gt;" line did, and implementing {@link IEssentiaContainerItem} means the held contents show up
 * automatically under the restored shift-tooltip (see {@code thaumcraft.client.gui.AspectTooltipEvents}), matching
 * the original's own scan-tooltip behaviour for essentia containers.
 */
public class JarBlockItem extends BlockItem implements IEssentiaContainerItem {

	public JarBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		AspectList aspects = context.getItemInHand().get(ThaumcraftDataComponents.ASPECTS.get());
		Aspect filter = context.getItemInHand().get(ThaumcraftDataComponents.ASPECT_FILTER.get());
		InteractionResult result = super.place(context);
		if (result.consumesAction() && !context.getLevel().isClientSide
				&& context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TileJarFillable tile) {
			if (aspects != null) tile.setAspects(aspects.copy());
			if (filter != null) tile.applyLabel(filter);
			tile.setChanged();
		}
		return result;
	}

	@Override
	public AspectList getAspects(ItemStack stack) {
		return stack.get(ThaumcraftDataComponents.ASPECTS.get());
	}

	@Override
	public void setAspects(ItemStack stack, AspectList aspects) {
		if (aspects == null || aspects.size() == 0) {
			stack.remove(ThaumcraftDataComponents.ASPECTS.get());
		} else {
			stack.set(ThaumcraftDataComponents.ASPECTS.get(), aspects);
		}
	}

	/** The original's getDurabilityForDisplay: 1 when empty, 0 when holding the jar's full 250. Drives both the
	 * fill bar below and the item model's "fill" predicate (ThaumcraftClient). */
	public static double emptiness(ItemStack stack) {
		AspectList al = stack.get(ThaumcraftDataComponents.ASPECTS.get());
		return al == null ? 1.0 : 1.0 - al.visSize() / 250.0;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return stack.has(ThaumcraftDataComponents.ASPECTS.get());
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return (int) Math.round(13.0 - emptiness(stack) * 13.0);
	}

	/** 1.12's default durability colour: hue from red (empty) to green (full). */
	@Override
	public int getBarColor(ItemStack stack) {
		return net.minecraft.util.Mth.hsvToRgb((float) Math.max(0.0, 1.0 - emptiness(stack)) / 3.0f, 1.0f, 1.0f);
	}

	@Override
	public boolean ignoreContainedAspects() {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Aspect filter = stack.get(ThaumcraftDataComponents.ASPECT_FILTER.get());
		if (filter != null) {
			tooltip.add(Component.literal(filter.getName()).withStyle(ChatFormatting.DARK_PURPLE));
		}
		super.appendHoverText(stack, context, tooltip, flag);
	}
}

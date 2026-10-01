package thaumcraft.common.items.consumables;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.blocks.ILabelable;
import thaumcraft.api.items.ThaumcraftDataComponents;

/**
 * Replaces the old (1.12.2) {@code ItemLabel} - applies to anything implementing {@link ILabelable} (in this
 * port, just {@code BlockJar} - see its own {@code applyLabel}) to lock it to one aspect. Same single-item,
 * component-presence-is-state shape as {@link ItemPhial}: a blank label carries no {@code ASPECTS} component,
 * a "filled" (aspected) one does.
 *
 * Deliberately not a subclass of {@link thaumcraft.api.items.ItemGenericEssentiaContainer} for the exact same
 * reason as {@link ItemPhial} - that base class's auto-random-aspect behavior would fight a label's actual
 * design (blank until deliberately dyed with a specific aspect via a filled Phial, see the recipe side).
 *
 * Uses the same {@code onItemUseFirst} hook as {@link ItemPhial} to intercept the click before the target
 * block's own right-click handling runs.
 *
 * Dyeing and clearing both have real crafting recipes now (see
 * {@link thaumcraft.common.lib.crafting.LabelDyeRecipe}/{@link thaumcraft.common.lib.crafting.LabelClearRecipe}),
 * matching the true original's own {@code label_<aspect>}/{@code JarLabelNull} recipe family. The original
 * matched a filled Phial by its exact NBT contents ({@code IngredientNBTTC}, a custom 1.12.2 ingredient type -
 * vanilla shapeless recipes couldn't match NBT either, then or now); rather than build a whole new
 * {@code ICustomIngredient} plumbing layer for it, both are real {@code CustomRecipe}s checking the real
 * {@code ASPECTS} component directly - the same technique {@code InfusionPotionRecipe}/{@code RecipeTripleMeatTreat}
 * already established for this port. The core mechanic (applying an aspected label to lock a jar) is fully
 * implemented and tested regardless of how a filled label is obtained (creative mode, {@link ItemLabel}'s
 * own {@code setAspects}, crafting, etc.).
 */
public class ItemLabel extends Item implements IEssentiaContainerItem {

	/** As the original: "Blank Label", or "Marked Label" once it carries an aspect. */
	@Override
	public net.minecraft.network.chat.Component getName(ItemStack stack) {
		thaumcraft.api.aspects.AspectList aspects = stack.get(ThaumcraftDataComponents.ASPECTS.get());
		return aspects != null && aspects.size() > 0 ? net.minecraft.network.chat.Component.translatable("item.thaumcraft.label.filled") : super.getName(stack);
	}

	public ItemLabel(Properties properties) {
		super(properties);
	}

	@Override
	public AspectList getAspects(ItemStack itemstack) {
		AspectList aspects = itemstack.get(ThaumcraftDataComponents.ASPECTS.get());
		return aspects != null && aspects.size() > 0 ? aspects : null;
	}

	@Override
	public void setAspects(ItemStack itemstack, AspectList aspects) {
		itemstack.set(ThaumcraftDataComponents.ASPECTS.get(), aspects);
	}

	@Override
	public boolean ignoreContainedAspects() {
		return true;
	}

	@Override
	public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null || level.isClientSide) return InteractionResult.PASS;
		BlockPos pos = context.getClickedPos();
		BlockState bs = level.getBlockState(pos);

		ILabelable target = null;
		if (bs.getBlock() instanceof ILabelable labelable) {
			target = labelable;
		} else if (level.getBlockEntity(pos) instanceof BlockEntity be && be instanceof ILabelable labelable) {
			target = labelable;
		}
		if (target == null) return InteractionResult.PASS;

		if (target.applyLabel(player, pos, context.getClickedFace(), stack)) {
			stack.shrink(1);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.SUCCESS;
	}
}

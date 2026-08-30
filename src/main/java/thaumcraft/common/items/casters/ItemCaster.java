package thaumcraft.common.items.casters;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code ItemCaster}. Now that {@link AuraHandler} is real, this draws vis live
 * from the world aura at the player's position again, same as the original - the "wand holds its own fixed
 * pool" version from the previous pass is gone (the {@code WAND_VIS} data component is unused now, kept
 * registered rather than ripped out mid-port).
 *
 * The original constructor took an "area" (0/1/2: local chunk, a horizontal cross of chunks, or a 3x3
 * block of chunks to draw vis from). Simplified here to always draw from the wand's own chunk only -
 * restore the wider search radii if/when it matters.
 *
 * Also dropped for this pass: architect/block-highlighting (IArchitect), dust triggers, block-picker foci,
 * and the caster-trigger-on-right-clicked-block system - all real features, just not part of the minimal
 * "does casting work" slice.
 */
public class ItemCaster extends Item implements ICaster {

	public ItemCaster(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public float getConsumptionModifier(ItemStack is, Player player, boolean crafting) {
		return 1.0f;
	}

	@Override
	public boolean consumeVis(ItemStack is, Player player, float amount, boolean crafting, boolean simulate) {
		amount *= getConsumptionModifier(is, player, crafting);
		Level level = player.level();
		float available = AuraHandler.getVis(level, player.blockPosition());
		if (available < amount) return false;
		if (simulate) return true;
		return AuraHandler.drainVis(level, player.blockPosition(), amount, false) >= amount - 0.001f;
	}

	@Override
	public Item getFocus(ItemStack stack) {
		ItemStack focus = getFocusStack(stack);
		return focus.isEmpty() ? null : focus.getItem();
	}

	@Override
	public ItemStack getFocusStack(ItemStack stack) {
		return stack.getOrDefault(ThaumcraftDataComponents.WAND_FOCUS.get(), ItemStack.EMPTY);
	}

	@Override
	public void setFocus(ItemStack stack, ItemStack focus) {
		if (focus == null || focus.isEmpty()) {
			stack.remove(ThaumcraftDataComponents.WAND_FOCUS.get());
		} else {
			stack.set(ThaumcraftDataComponents.WAND_FOCUS.get(), focus.copyWithCount(1));
		}
	}

	@Override
	public ItemStack getPickedBlock(ItemStack stack) {
		return ItemStack.EMPTY;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		ItemStack focus = getFocusStack(stack);
		if (!focus.isEmpty()) {
			tooltip.add(focus.getHoverName().copy());
			if (focus.getItem() instanceof ItemFocus itemFocus) {
				tooltip.add(Component.translatable("item.thaumcraft.focus.cost", String.format("%.1f", itemFocus.getVisCost(focus))));
			}
		} else {
			tooltip.add(Component.translatable("item.thaumcraft.caster.no_focus"));
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack wand = player.getItemInHand(hand);

		if (player.isShiftKeyDown()) {
			ItemStack offhand = player.getOffhandItem();
			if (!offhand.isEmpty() && offhand.getItem() instanceof ItemFocus) {
				if (!level.isClientSide) {
					setFocus(wand, offhand);
					if (!player.getAbilities().instabuild) offhand.shrink(1);
				}
				return InteractionResultHolder.sidedSuccess(wand, level.isClientSide);
			}
			return InteractionResultHolder.pass(wand);
		}

		ItemStack focusStack = getFocusStack(wand);
		if (!(focusStack.getItem() instanceof ItemFocus focus)) {
			return InteractionResultHolder.pass(wand);
		}
		if (level.isClientSide) {
			return InteractionResultHolder.success(wand);
		}

		FocusPackage core = focus.getPackage(focusStack);
		List<IFocusElement> nodes = core.nodes;
		if (nodes.isEmpty()) return InteractionResultHolder.fail(wand);

		float cost = focus.getVisCost(focusStack);
		if (!consumeVis(wand, player, cost, false, false)) {
			return InteractionResultHolder.fail(wand);
		}

		core.initialize(player);
		FocusEngine.castFocusPackage(player, core, true);
		player.swing(hand);
		return InteractionResultHolder.success(wand);
	}
}

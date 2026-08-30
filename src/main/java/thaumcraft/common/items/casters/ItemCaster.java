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
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.items.ThaumcraftDataComponents;

/**
 * Replaces the old (1.12.2) {@code ItemCaster}. Vis is now stored directly on the wand's own stack (a
 * {@code WAND_VIS} data component) rather than being drawn live from the world aura each cast - the aura
 * system isn't ported yet (see AuraHelper/GolemHelper's TODOs), so for now the wand just holds its own
 * fixed pool and nothing refills it. Restore aura-backed casting once that subsystem exists; the
 * ICaster/consumeVis contract this implements won't need to change to do that.
 *
 * Also dropped for this pass: architect/block-highlighting (IArchitect), dust triggers, block-picker foci,
 * and the caster-trigger-on-right-clicked-block system - all real features, just not part of the minimal
 * "does casting work" slice.
 */
public class ItemCaster extends Item implements ICaster {

	private final int maxVis;

	public ItemCaster(Properties properties, int maxVis) {
		super(properties.stacksTo(1));
		this.maxVis = maxVis;
	}

	public int getVis(ItemStack stack) {
		return stack.getOrDefault(ThaumcraftDataComponents.WAND_VIS.get(), 0);
	}

	public void setVis(ItemStack stack, int vis) {
		stack.set(ThaumcraftDataComponents.WAND_VIS.get(), Math.max(0, Math.min(maxVis, vis)));
	}

	public int getMaxVis() {
		return maxVis;
	}

	@Override
	public float getConsumptionModifier(ItemStack is, Player player, boolean crafting) {
		return 1.0f;
	}

	@Override
	public boolean consumeVis(ItemStack is, Player player, float amount, boolean crafting, boolean simulate) {
		amount *= getConsumptionModifier(is, player, crafting);
		int have = getVis(is);
		if (have < amount) return false;
		if (!simulate) setVis(is, (int) (have - amount));
		return true;
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
		tooltip.add(Component.translatable("item.thaumcraft.caster.vis", getVis(stack), maxVis));
		ItemStack focus = getFocusStack(stack);
		if (!focus.isEmpty()) {
			tooltip.add(focus.getHoverName().copy());
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

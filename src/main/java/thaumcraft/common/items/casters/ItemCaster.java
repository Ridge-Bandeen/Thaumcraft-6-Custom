package thaumcraft.common.items.casters;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.casters.IFocusBlockPicker;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.api.casters.IInteractWithCaster;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.api.items.WornGearHelper;
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
 * Also dropped for this pass: architect/block-highlighting (IArchitect), dust triggers - both real features,
 * just not part of the minimal "does casting work" slice. The caster-trigger-on-right-clicked-block system
 * (the original's {@code onItemUseFirst}) IS real now, via {@link #onItemUseFirst} below - see that method's
 * own doc comment, which also covers the real block-picker foci support restored 2026-09-15.
 */
public class ItemCaster extends Item implements ICaster {

	public ItemCaster(Properties properties) {
		super(properties.stacksTo(1));
	}

	/**
	 * Replaces the original's {@code CasterManager#getTotalVisDiscount}. Sums {@link IVisDiscountGear}'s
	 * discount off worn armor plus the player's hotbar/offhand (the original scanned Baubles slots for this -
	 * see {@link thaumcraft.api.items.WornGearHelper}'s own doc comment for why a plain inventory scan
	 * stands in for that here) and the potion-based Vis Exhaust penalty the original applied on top - that
	 * potion isn't built yet, so this omits it, capped at the interface's own documented 50% max (the
	 * original never actually enforced that cap in code despite documenting it - enforced here for real).
	 */
	@Override
	public float getConsumptionModifier(ItemStack is, Player player, boolean crafting) {
		int discount = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack armor = player.getItemBySlot(slot);
			if (armor.getItem() instanceof IVisDiscountGear discountGear) {
				discount += discountGear.getVisDiscount(armor, player);
			}
		}
		for (ItemStack stack : WornGearHelper.getWornItems(player)) {
			if (stack.getItem() instanceof IVisDiscountGear discountGear) {
				discount += discountGear.getVisDiscount(stack, player);
			}
		}
		discount = Math.min(discount, 50);
		return 1.0f - discount / 100.0f;
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
		ThaumcraftDataComponents.WandFocus wandFocus = stack.get(ThaumcraftDataComponents.WAND_FOCUS.get());
		return wandFocus == null ? ItemStack.EMPTY : wandFocus.stack();
	}

	@Override
	public void setFocus(ItemStack stack, ItemStack focus) {
		if (focus == null || focus.isEmpty()) {
			stack.remove(ThaumcraftDataComponents.WAND_FOCUS.get());
		} else {
			stack.set(ThaumcraftDataComponents.WAND_FOCUS.get(), new ThaumcraftDataComponents.WandFocus(focus.copyWithCount(1)));
		}
	}

	/** Real, restored 2026-09-15 - see {@link #onItemUseFirst}'s own doc comment for how a block gets picked
	 * in the first place. Matches the true original's own real gate exactly: only returns the stored block if
	 * the currently-loaded focus package still actually contains a real {@link IFocusBlockPicker} node (e.g.
	 * {@code FocusEffectExchange}) - swap to a different focus and this goes back to reporting empty, even if
	 * a block is still stored underneath. */
	@Override
	public ItemStack getPickedBlock(ItemStack stack) {
		ThaumcraftDataComponents.WandFocus picked = stack.get(ThaumcraftDataComponents.WAND_PICKED_BLOCK.get());
		if (picked == null || picked.stack().isEmpty()) return ItemStack.EMPTY;
		if (!hasBlockPickerFocus(stack)) return ItemStack.EMPTY;
		return picked.stack();
	}

	private boolean hasBlockPickerFocus(ItemStack stack) {
		ItemStack focusStack = getFocusStack(stack);
		if (!(focusStack.getItem() instanceof ItemFocus focus)) return false;
		FocusPackage core = focus.getPackage(focusStack);
		for (IFocusElement node : core.nodes) {
			if (node instanceof IFocusBlockPicker) return true;
		}
		return false;
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

	/**
	 * Lets a block the wand is pointed at handle the click itself before the default block interaction or
	 * this item's own focus-casting {@link #use} runs - the modern equivalent of the original's
	 * caster-trigger-on-right-clicked-block dispatch. Currently the only real implementer is
	 * {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix} (its own doc comment already anticipated
	 * this: it was calling {@code activate} directly from a plain block right-click as a stand-in until a
	 * wand item existed to call {@code onCasterRightClick} for real - this is that wiring, added with no
	 * changes needed on the tile's side). The block right-click fallback stays in place alongside this,
	 * purely additive - not worth removing an already-working, already-tested path.
	 *
	 * <p>Real block-picking, restored 2026-09-15: sneak-right-clicking a bare block (one with no real
	 * {@code BlockEntity}, matching the true original's own {@code world.getTileEntity(pos) == null} gate)
	 * while a real {@link IFocusBlockPicker} focus (e.g. {@code FocusEffectExchange}) is loaded stores that
	 * block into {@link ThaumcraftDataComponents#WAND_PICKED_BLOCK}, readable back via {@link #getPickedBlock}.
	 * Real, deliberate simplification: the true original's own silk-touch-aware drop lookup
	 * ({@code BlockUtils#getSilkTouchDrop}, part of the still-unported {@code BlockUtils} utility class) is
	 * replaced with the block's own plain item form ({@code Block#asItem}) - the picked block is only ever
	 * used as a real placement template by {@code FocusEffectExchange}, never actually given to a player, so
	 * the silk-touch-specific item form doesn't matter here the way it would for an actual drop.
	 */
	@Override
	public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		if (player == null) return InteractionResult.PASS;
		if (level.getBlockEntity(context.getClickedPos()) instanceof IInteractWithCaster caster) {
			boolean handled = caster.onCasterRightClick(level, stack, player, context.getClickedPos(), context.getClickedFace(), context.getHand());
			if (handled) {
				return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
			}
		}
		if (player.isShiftKeyDown() && level.getBlockEntity(context.getClickedPos()) == null && hasBlockPickerFocus(stack)) {
			if (!level.isClientSide) {
				net.minecraft.world.level.block.state.BlockState picked = level.getBlockState(context.getClickedPos());
				stack.set(ThaumcraftDataComponents.WAND_PICKED_BLOCK.get(),
						new ThaumcraftDataComponents.WandFocus(new ItemStack(picked.getBlock().asItem())));
			}
			player.swing(context.getHand());
			return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
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

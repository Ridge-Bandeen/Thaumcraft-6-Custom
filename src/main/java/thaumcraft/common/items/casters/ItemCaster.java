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
 * The original's constructor took an "area" (which chunks to draw vis from); its one caster used 0, its own chunk, as
 * here.
 *
 * As the original it is an {@link IArchitect}: with a Plan focus it outlines the blocks the spell will work on, and the
 * misc caster key (G; Ctrl+G picks the axis) sets the area ({@link #toggleMisc}). The caster-trigger-on-right-clicked-block system
 * (the original's {@code onItemUseFirst}) IS real now, via {@link #onItemUseFirst} below - see that method's
 * own doc comment, which also covers the real block-picker foci support restored 2026-09-15.
 */
public class ItemCaster extends Item implements ICaster, thaumcraft.api.items.IArchitect {

	public ItemCaster(Properties properties) {
		super(properties.stacksTo(1));
	}

	/**
	 * Replaces the original's {@code CasterManager#getTotalVisDiscount}. Sums {@link IVisDiscountGear}'s
	 * discount off worn armor plus the player's hotbar/offhand (the original scanned Baubles slots for this -
	 * see {@link thaumcraft.api.items.WornGearHelper}'s own doc comment for why a plain inventory scan
	 * stands in for that here) and the Vis Exhaust penalty the original applied on top (10% more per level of
	 * whichever of the plain / infectious effects is stronger; wired 2026-09-25 - the effects exist now),
	 * with the discount side capped at the interface's own documented 50% max (the original never actually
	 * enforced that cap in code despite documenting it - enforced here for real).
	 */
	@Override
	public float getConsumptionModifier(ItemStack is, Player player, boolean crafting) {
		return totalVisModifier(player);
	}

	/** The multiplier applied to every vis cost this player pays: below 1 with discount gear, above 1 when vis-exhausted. */
	public static float totalVisModifier(Player player) {
		if (player == null) return 1.0f;
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
		net.minecraft.world.effect.MobEffectInstance plain = player.getEffect(thaumcraft.common.registry.ThaumcraftMobEffects.VIS_EXHAUST);
		net.minecraft.world.effect.MobEffectInstance infectious = player.getEffect(thaumcraft.common.registry.ThaumcraftMobEffects.INFECTIOUS_VIS_EXHAUST);
		if (plain != null || infectious != null) {
			int level = Math.max(plain != null ? plain.getAmplifier() : 0, infectious != null ? infectious.getAmplifier() : 0);
			discount -= (level + 1) * 10;
		}
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

	/** The original's {@code onUpdate}: every 10 ticks, while a caster is in either hand, the aura of the player's chunk
	 * goes to their client for the vis dial ({@link thaumcraft.client.gui.HudOverlays}). */
	@Override
	public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, level, entity, slot, selected);
		if (!level.isClientSide && entity.tickCount % 10 == 0 && entity instanceof net.minecraft.server.level.ServerPlayer player) {
			for (ItemStack h : player.getHandSlots()) {
				if (!h.isEmpty() && h.getItem() instanceof ICaster) {
					var ac = AuraHandler.getAuraChunk(level, player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4);
					thaumcraft.common.lib.network.ClientboundAuraPacket.send(player, ac == null
							? new thaumcraft.common.lib.network.ClientboundAuraPacket((short) 0, 0f, 0f)
							: new thaumcraft.common.lib.network.ClientboundAuraPacket(ac.getBase(), ac.getVis(), ac.getFlux()));
					break;
				}
			}
		}
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
	 * As the original the stored item is the block's silk-touch drop (its loot table run with a Silk Touch pick - lit
	 * redstone ore gives redstone ore, grass gives grass), else its plain item.
	 */
	/** The original's BlockUtils.getSilkTouchDrop: what the block drops to a Silk Touch tool (the first such drop that
	 * is a block's item), else the block's own item. */
	public static ItemStack silkTouchDrop(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
			net.minecraft.world.level.block.state.BlockState state, Player player) {
		ItemStack plain = new ItemStack(state.getBlock().asItem());
		if (state.isAir()) return plain;
		try {
			ItemStack tool = new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
			tool.enchant(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
					.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
			for (ItemStack drop : net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, player, tool)) {
				if (drop.getItem() instanceof net.minecraft.world.item.BlockItem) return drop.copyWithCount(1);
			}
		} catch (Exception ex) {
			// as the original: fall back to the plain item
		}
		return plain;
	}

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
						new ThaumcraftDataComponents.WandFocus(silkTouchDrop((net.minecraft.server.level.ServerLevel) level, context.getClickedPos(), picked, player)));
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
		thaumcraft.api.ThaumcraftApi.applyFocusPowerModifiers(focusStack, core);
		FocusEngine.castFocusPackage(player, core, true);
		player.swing(hand);
		return InteractionResultHolder.success(wand);
	}

	// ---- the Plan area (the original's CasterManager area methods) and IArchitect ----------------------------------

	private static final int AREA_SIZE = 3;

	public static ThaumcraftDataComponents.CasterArea getArea(ItemStack stack) {
		ThaumcraftDataComponents.CasterArea area = stack.get(ThaumcraftDataComponents.CASTER_AREA.get());
		if (area == null) return ThaumcraftDataComponents.CasterArea.DEFAULT;
		return new ThaumcraftDataComponents.CasterArea(Math.min(area.x(), AREA_SIZE), Math.min(area.y(), AREA_SIZE), Math.min(area.z(), AREA_SIZE), area.dim());
	}

	/** The original's toggleMisc, on the misc caster key with a Plan focus: {@code mod} 0 grows the area along the chosen
	 * axis (or all three), wrapping past 3 to 0; {@code mod} 1 (Ctrl) moves to the next axis choice. */
	public static void toggleMisc(ItemStack stack, int mod) {
		if (!(stack.getItem() instanceof ItemCaster caster)) return;
		ItemStack focusStack = caster.getFocusStack(stack);
		if (!(focusStack.getItem() instanceof ItemFocus focus)) return;
		FocusPackage fp = focus.getPackage(focusStack);
		if (fp == null || !FocusEngine.doesPackageContainElement(fp, "thaumcraft.PLAN")) return;
		ThaumcraftDataComponents.CasterArea a = getArea(stack);
		int x = a.x(), y = a.y(), z = a.z(), dim = a.dim();
		if (mod == 0) {
			switch (dim) {
				case 0 -> { x++; y++; z++; }
				case 1 -> x++;
				case 2 -> z++;
				case 3 -> y++;
				default -> {
				}
			}
			if (x > AREA_SIZE) x = 0;
			if (y > AREA_SIZE) y = 0;
			if (z > AREA_SIZE) z = 0;
		} else if (mod == 1) {
			if (++dim > 3) dim = 0;
		}
		stack.set(ThaumcraftDataComponents.CASTER_AREA.get(), new ThaumcraftDataComponents.CasterArea(x, y, z, dim));
	}

	@javax.annotation.Nullable
	private thaumcraft.api.items.IArchitect planNode(ItemStack stack) {
		ItemStack focusStack = getFocusStack(stack);
		if (!(focusStack.getItem() instanceof ItemFocus focus)) return null;
		FocusPackage fp = focus.getPackage(focusStack);
		if (fp == null) return null;
		for (IFocusElement node : fp.nodes) {
			if (node instanceof thaumcraft.api.items.IArchitect architect) return architect;
		}
		return null;
	}

	@Override
	public net.minecraft.world.phys.HitResult getArchitectMOP(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity player) {
		thaumcraft.api.items.IArchitect plan = planNode(stack);
		return plan != null ? plan.getArchitectMOP(stack, level, player) : null;
	}

	@Override
	public boolean useBlockHighlight(ItemStack stack) {
		return false;
	}

	@Override
	public java.util.ArrayList<net.minecraft.core.BlockPos> getArchitectBlocks(ItemStack stack, Level level, net.minecraft.core.BlockPos pos,
			net.minecraft.core.Direction side, Player player) {
		thaumcraft.api.items.IArchitect plan = planNode(stack);
		return plan != null ? plan.getArchitectBlocks(stack, level, pos, side, player) : new java.util.ArrayList<>();
	}

	@Override
	public boolean showAxis(ItemStack stack, Level level, Player player, net.minecraft.core.Direction side, EnumAxis axis) {
		thaumcraft.api.items.IArchitect plan = planNode(stack);
		return plan != null && plan.showAxis(stack, level, player, side, axis);
	}
}

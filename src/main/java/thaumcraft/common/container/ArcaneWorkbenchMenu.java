package thaumcraft.common.container;

import javax.annotation.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.ArcaneCraftingInput;
import thaumcraft.api.crafting.ArcaneRecipe;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.common.registry.ThaumcraftMenus;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code ContainerArcaneWorkbench}. Slot layout is unchanged: 0 = result,
 * 1-9 = 3x3 grid, 10-15 = crystal ring, 16-42 = player inventory, 43-51 = hotbar.
 *
 * Now checks and spends real vis (via AuraHandler). Draws from the workbench's own chunk normally, or the
 * full 3x3 chunk block around it if an Arcane Workbench Charger sits directly above (see
 * {@code BlockArcaneWorkbenchCharger} and {@code AuraHandler#getWideVis}/{@code #drainWideVis}), matching
 * the original's own wider-area draw exactly.
 *
 * Vis cost is {@code recipe vis * the player's total modifier} (see {@link #visCost}) - discount gear makes crafts
 * cheaper, Vis Exhaust dearer. The original computed {@code vis *= (int)(1.0f - discount)}, which truncates to 0 for
 * ANY positive discount (free crafts) and ignores the exhaust penalty; the intended percentage is implemented here
 * instead (2026-09-25). Each crystal-ring slot only takes its own primal aspect's crystal (air, fire, water, earth,
 * order, entropy - as the original's SlotCrystal did). The available vis, cost and discount % are synced to the
 * client through data slots for the screen's readout, and the result re-evaluates when the vis level crosses the cost.
 *
 * Simplifications still remaining versus the original for this pass (see TileArcaneWorkbench):
 * - No fallback to plain vanilla 3x3 crafting-table recipes if no arcane recipe matches.
 * - No vanilla recipe-book integration (extends AbstractContainerMenu directly, not RecipeBookMenu).
 * - Taking the result only decrements grid ingredients by 1 each; doesn't swap in "crafting remainder"
 *   items (buckets etc.) the way vanilla's ResultSlot does - most Thaumcraft recipe inputs don't have one.
 */
public class ArcaneWorkbenchMenu extends AbstractContainerMenu {

	private static final int RESULT_SLOT = 0;
	private static final int GRID_START = 1;
	private static final int CRYSTAL_START = 10;
	private static final int PLAYER_INV_START = 16;
	private static final int PLAYER_INV_END = 43;
	private static final int HOTBAR_END = 52;

	private final Container container;
	private final ResultContainer resultContainer = new ResultContainer();
	private final ContainerLevelAccess access;
	private final Player player;
	@Nullable
	private RecipeHolder<ArcaneRecipe> currentRecipe;
	private final DataSlot availableVis = DataSlot.standalone();
	private final DataSlot shownCost = DataSlot.standalone();
	private final DataSlot shownDiscount = DataSlot.standalone();
	private boolean refreshing;
	private boolean lastHadVis = true;

	/** The primal aspect each crystal-ring slot takes, in ring order. */
	public static final Aspect[] RING_ASPECTS = {Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY};

	public ArcaneWorkbenchMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new SimpleContainer(TileArcaneWorkbench.SLOT_COUNT), ContainerLevelAccess.NULL);
	}

	public ArcaneWorkbenchMenu(int containerId, Inventory playerInventory, TileArcaneWorkbench blockEntity) {
		this(containerId, playerInventory, blockEntity, ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()));
	}

	private ArcaneWorkbenchMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access) {
		super(ThaumcraftMenus.ARCANE_WORKBENCH.get(), containerId);
		this.container = container;
		this.access = access;
		this.player = playerInventory.player;

		addSlot(new Slot(resultContainer, 0, 160, 64) { // the texture's own socket for it, as in the original
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player p, ItemStack stack) {
				consumeIngredients();
				super.onTake(p, stack);
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				addSlot(new Slot(container, col + row * 3, 40 + col * 24, 40 + row * 24));
			}
		}

		int[] crystalX = {64, 17, 112, 17, 112, 64};
		int[] crystalY = {13, 35, 35, 93, 93, 115};
		for (int i = 0; i < TileArcaneWorkbench.CRYSTAL_SIZE; i++) {
			addSlot(new CrystalSlot(RING_ASPECTS[i], container, TileArcaneWorkbench.GRID_SIZE + i, crystalX[i], crystalY[i]));
		}
		addDataSlot(availableVis);
		addDataSlot(shownCost);
		addDataSlot(shownDiscount);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 16 + col * 18, 151 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 16 + col * 18, 209));
		}

		slotsChanged(container);
	}

	/** Restricts a crystal-ring slot to the crystal essence of its own primal aspect. */
	private static class CrystalSlot extends Slot {
		private final Aspect aspect;

		CrystalSlot(Aspect aspect, Container container, int slot, int x, int y) {
			super(container, slot, x, y);
			this.aspect = aspect;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (!(stack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem c)) return false;
			AspectList aspects = c.getAspects(stack);
			return aspects != null && aspects.size() == 1 && aspects.getAmount(aspect) > 0;
		}
	}

	/** What the recipe really costs this player: its vis times their modifier, never free while the recipe has a cost. */
	public static int visCost(ArcaneRecipe recipe, Player player) {
		int base = recipe.getVis();
		if (base <= 0) return 0;
		return Math.max(1, Math.round(base * thaumcraft.common.items.casters.ItemCaster.totalVisModifier(player)));
	}

	public int getAvailableVis() {
		return availableVis.get();
	}

	public int getShownCost() {
		return shownCost.get();
	}

	/** The discount as a whole percent; negative while vis-exhausted. */
	public int getShownDiscount() {
		return shownDiscount.get();
	}

	/** The crystals the recipe in the grid asks for, or null (client and server: worked out from the synced slots). */
	@Nullable
	public AspectList findCrystalNeeds(Level level) {
		return level.getRecipeManager().getRecipeFor(ThaumcraftRecipeTypes.ARCANE_TYPE.get(), buildInput(), level)
				.map(h -> h.value().getCrystals()).orElse(null);
	}

	/** What the grid would make if the vis were there - the screen draws it greyed out when it is not. */
	public ItemStack previewResult(Level level) {
		ArcaneCraftingInput input = buildInput();
		return level.getRecipeManager().getRecipeFor(ThaumcraftRecipeTypes.ARCANE_TYPE.get(), input, level)
				.map(h -> h.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	private float availableVisAt(Level level, net.minecraft.core.BlockPos pos) {
		return hasCharger(level, pos) ? AuraHandler.getWideVis(level, pos) : AuraHandler.getVis(level, pos);
	}

	/** Keeps the synced vis readout current and re-evaluates the result when the vis crosses the recipe's cost. */
	@Override
	public void broadcastChanges() {
		if (!refreshing && !player.level().isClientSide) {
			refreshing = true;
			try {
				access.execute((level, pos) -> {
					availableVis.set((int) availableVisAt(level, pos));
					if (currentRecipe == null) {
						shownCost.set(0);
						shownDiscount.set(0);
					} else {
						shownCost.set(visCost(currentRecipe.value(), player));
						shownDiscount.set(Math.round((1.0f - thaumcraft.common.items.casters.ItemCaster.totalVisModifier(player)) * 100.0f));
						boolean hasVis = availableVisAt(level, pos) >= shownCost.get();
						if (hasVis != lastHadVis) updateResult(level, pos);
					}
				});
			} finally {
				refreshing = false;
			}
		}
		super.broadcastChanges();
	}

	@Override
	public void slotsChanged(Container inventory) {
		access.execute(this::updateResult);
	}

	private void updateResult(Level level, net.minecraft.core.BlockPos pos) {
		ArcaneCraftingInput input = buildInput();
		currentRecipe = level.getRecipeManager().getRecipeFor(ThaumcraftRecipeTypes.ARCANE_TYPE.get(), input, level).orElse(null);
		boolean hasVis = currentRecipe == null || availableVisAt(level, pos) >= visCost(currentRecipe.value(), player);
		lastHadVis = hasVis;
		boolean known = currentRecipe == null || thaumcraft.api.capabilities.ThaumcraftCapabilities.knowsResearchStrict(player, currentRecipe.value().getResearch());
		ItemStack result = currentRecipe != null && hasVis && known ? currentRecipe.value().assemble(input, level.registryAccess()) : ItemStack.EMPTY;
		resultContainer.setItem(0, result);
		setRemoteSlot(RESULT_SLOT, result);
		broadcastChanges();
	}

	private static boolean hasCharger(Level level, net.minecraft.core.BlockPos pos) {
		return level.getBlockState(pos.above()).is(BlocksTC.arcaneWorkbenchCharger.get());
	}

	private ArcaneCraftingInput buildInput() {
		java.util.List<ItemStack> gridItems = new java.util.ArrayList<>(TileArcaneWorkbench.GRID_SIZE);
		for (int i = 0; i < TileArcaneWorkbench.GRID_SIZE; i++) gridItems.add(container.getItem(i));
		CraftingInput grid = CraftingInput.of(3, 3, gridItems);

		java.util.List<ItemStack> crystalItems = new java.util.ArrayList<>(TileArcaneWorkbench.CRYSTAL_SIZE);
		for (int i = 0; i < TileArcaneWorkbench.CRYSTAL_SIZE; i++) crystalItems.add(container.getItem(TileArcaneWorkbench.GRID_SIZE + i));

		return new ArcaneCraftingInput(grid, crystalItems);
	}

	private void consumeIngredients() {
		for (int i = 0; i < TileArcaneWorkbench.GRID_SIZE; i++) {
			if (!container.getItem(i).isEmpty()) container.removeItem(i, 1);
		}
		if (currentRecipe != null) {
				int cost = visCost(currentRecipe.value(), player);
			access.execute((level, pos) -> {
				if (hasCharger(level, pos)) {
					AuraHandler.drainWideVis(level, pos, cost);
				} else {
					AuraHandler.drainVis(level, pos, cost, false);
				}
			});
			AspectList crystals = currentRecipe.value().getCrystals();
			if (crystals != null) {
				for (Aspect aspect : crystals.getAspects()) {
					int need = crystals.getAmount(aspect);
					for (int i = 0; i < TileArcaneWorkbench.CRYSTAL_SIZE && need > 0; i++) {
						int slot = TileArcaneWorkbench.GRID_SIZE + i;
						ItemStack stack = container.getItem(slot);
						if (stack.isEmpty() || !(stack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem c)) continue;
						AspectList stackAspects = c.getAspects(stack);
						if (stackAspects == null || stackAspects.size() != 1 || stackAspects.getAmount(aspect) != 1) continue;
						int take = Math.min(need, stack.getCount());
						container.removeItem(slot, take);
						need -= take;
					}
				}
			}
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, BlocksTC.arcaneWorkbench.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack copy = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			copy = stack.copy();
			if (index == RESULT_SLOT) {
				// Not calling Slot#onQuickCraft here - it's protected and only accessible to
				// same-package (vanilla) menu classes. onTake (below, via slot.onTake) already
				// handles ingredient consumption; this just skips vanilla's bulk-shift-click
				// crafting-stat bookkeeping, which isn't load-bearing for correctness.
				if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) {
					return ItemStack.EMPTY;
				}
			} else if (index >= PLAYER_INV_START && index < HOTBAR_END) {
				if (!moveItemStackTo(stack, GRID_START, PLAYER_INV_START, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
			if (stack.getCount() == copy.getCount()) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, stack);
		}
		return copy;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != resultContainer && super.canTakeItemForPickAll(stack, slot);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		access.execute((level, pos) -> clearContainer(player, resultContainer));
	}
}

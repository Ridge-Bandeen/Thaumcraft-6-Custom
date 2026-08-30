package thaumcraft.common.container;

import javax.annotation.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
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
 * Now checks and spends real vis (via AuraHandler, drawn from the workbench's own chunk - the original
 * also supported drawing from a wider area when an Arcane Workbench Charger is placed above it; that
 * block isn't ported, so this always uses the single-chunk draw).
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

		addSlot(new Slot(resultContainer, 0, 129, 65) {
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
			addSlot(new CrystalSlot(container, TileArcaneWorkbench.GRID_SIZE + i, crystalX[i], crystalY[i]));
		}

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

	/** Restricts a crystal-ring slot to essentia-container items (crystal essence, etc). */
	private static class CrystalSlot extends Slot {
		CrystalSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem;
		}
	}

	@Override
	public void slotsChanged(Container inventory) {
		access.execute(this::updateResult);
	}

	private void updateResult(Level level, net.minecraft.core.BlockPos pos) {
		ArcaneCraftingInput input = buildInput();
		currentRecipe = level.getRecipeManager().getRecipeFor(ThaumcraftRecipeTypes.ARCANE_TYPE.get(), input, level).orElse(null);
		boolean hasVis = currentRecipe == null || AuraHandler.getVis(level, pos) >= currentRecipe.value().getVis();
		ItemStack result = currentRecipe != null && hasVis ? currentRecipe.value().assemble(input, level.registryAccess()) : ItemStack.EMPTY;
		resultContainer.setItem(0, result);
		setRemoteSlot(RESULT_SLOT, result);
		broadcastChanges();
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
			access.execute((level, pos) -> AuraHandler.drainVis(level, pos, currentRecipe.value().getVis(), false));
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
		return stillValid(access, player, Blocks.AIR);
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

package thaumcraft.common.container;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.registry.ThaumcraftMenus;
import thaumcraft.common.tiles.essentia.TileSmelter;

/**
 * Replaces the old (1.12.2) {@code ContainerSmelter}: slot 0 = the input (only items that have object aspects), slot 1
 * = fuel, then the player inventory (2-28) and hotbar (29-37), at the original's positions. The three gauges the
 * screen draws (fuel left, cook progress, stored vis) travel as {@link ContainerData} - the same vanilla-furnace-style
 * sync the golem builder uses - so no packet is needed. Restored 2026-09-25: the port had replaced the screen with
 * right-click routing, which lost the gauges.
 */
public class SmelterMenu extends AbstractContainerMenu {

	public static final int DATA_BURN = 0;
	public static final int DATA_FUEL_BURN = 1;
	public static final int DATA_COOK = 2;
	public static final int DATA_SMELT = 3;
	public static final int DATA_VIS = 4;
	private static final int DATA_COUNT = 5;

	private final Container container;
	private final ContainerData data;

	/** Client constructor. */
	public SmelterMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new SimpleContainer(2) {
			@Override
			public boolean canPlaceItem(int slot, ItemStack stack) {
				return TileSmelter.accepts(slot, stack);
			}
		}, new SimpleContainerData(DATA_COUNT));
	}

	/** Server constructor - the gauges read straight from the tile. */
	public SmelterMenu(int containerId, Inventory playerInventory, TileSmelter tile) {
		this(containerId, playerInventory, tile, new ContainerData() {
			@Override
			public int get(int index) {
				return switch (index) {
					case DATA_BURN -> tile.getBurnTime();
					case DATA_FUEL_BURN -> tile.getFuelBurnTime();
					case DATA_COOK -> tile.getCookTime();
					case DATA_SMELT -> tile.getSmeltTime();
					case DATA_VIS -> Math.min(tile.aspects.visSize(), TileSmelter.MAX_VIS_GAUGE);
					default -> 0;
				};
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int getCount() {
				return DATA_COUNT;
			}
		});
	}

	private SmelterMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
		super(ThaumcraftMenus.SMELTER.get(), containerId);
		this.container = container;
		this.data = data;
		checkContainerSize(container, 2);
		addDataSlots(data);

		addSlot(new Slot(container, 0, 80, 8) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return container.canPlaceItem(0, stack);
			}
		});
		addSlot(new Slot(container, 1, 80, 48) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return container.canPlaceItem(1, stack);
			}
		});
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 9; j++) {
				addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
			}
		}
		for (int i = 0; i < 9; i++) {
			addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
		}
	}

	public int getBurnTimeScaled(int pixels) {
		int total = data.get(DATA_FUEL_BURN);
		return total == 0 ? 0 : data.get(DATA_BURN) * pixels / total;
	}

	public int getCookProgressScaled(int pixels) {
		int total = Math.max(1, data.get(DATA_SMELT));
		return data.get(DATA_COOK) * pixels / total;
	}

	public int getVisScaled(int pixels) {
		return data.get(DATA_VIS) * pixels / TileSmelter.MAX_VIS_GAUGE;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			result = stack.copy();
			if (index < 2) {
				if (!moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
			} else if (container.canPlaceItem(1, stack)) {
				if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
			} else if (container.canPlaceItem(0, stack)) {
				if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
			} else {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
			else slot.setChanged();
			if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;
			slot.onTake(player, stack);
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}
}

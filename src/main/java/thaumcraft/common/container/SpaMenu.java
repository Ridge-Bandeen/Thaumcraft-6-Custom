package thaumcraft.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import thaumcraft.common.registry.ThaumcraftMenus;
import thaumcraft.common.tiles.devices.TileSpa;

/**
 * Replaces the old (1.12.2) {@code ContainerSpa}: the Bath Salts slot at (65, 31), the player's inventory, and as
 * the original's window properties the mix flag and the tank (amount, fluid) for the screen's toggle and gauge.
 * Button 1 toggles the mix mode.
 */
public class SpaMenu extends AbstractContainerMenu {

	private static final int INPUT_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int HOTBAR_END = 37;

	private final ContainerLevelAccess access;
	@javax.annotation.Nullable
	private final TileSpa tile;
	public static final int DATA_MIX = 0;
	public static final int DATA_AMOUNT = 1;
	public static final int DATA_FLUID = 2;
	private final net.minecraft.world.inventory.ContainerData data;

	/** Client-side reconstruction - see {@link PechMenu}'s own doc comment for why. */
	public SpaMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new ItemStackHandler(1), ContainerLevelAccess.NULL, null);
	}

	public SpaMenu(int containerId, Inventory playerInventory, TileSpa tile) {
		this(containerId, playerInventory, tile.getItemHandler(), ContainerLevelAccess.create(tile.getLevel(), tile.getBlockPos()), tile);
	}

	private SpaMenu(int containerId, Inventory playerInventory, IItemHandler handler, ContainerLevelAccess access, @javax.annotation.Nullable TileSpa tile) {
		super(ThaumcraftMenus.SPA.get(), containerId);
		this.access = access;
		this.tile = tile;
		this.data = tile == null ? new net.minecraft.world.inventory.SimpleContainerData(3) : new net.minecraft.world.inventory.ContainerData() {
			@Override
			public int get(int index) {
				return switch (index) {
					case DATA_MIX -> tile.isMix() ? 1 : 0;
					case DATA_AMOUNT -> tile.tank.getFluidAmount();
					default -> tile.tank.isEmpty() ? -1 : net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(tile.tank.getFluid().getFluid());
				};
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int getCount() {
				return 3;
			}
		};
		addDataSlots(data);

		addSlot(new SlotItemHandler(handler, INPUT_SLOT, 65, 31));
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	public boolean isMix() {
		return data.get(DATA_MIX) != 0;
	}

	public int tankAmount() {
		return data.get(DATA_AMOUNT);
	}

	@javax.annotation.Nullable
	public net.minecraft.world.level.material.Fluid tankFluid() {
		int id = data.get(DATA_FLUID);
		return id < 0 ? null : net.minecraft.core.registries.BuiltInRegistries.FLUID.byId(id);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == 1 && tile != null) {
			tile.toggleMix();
			return true;
		}
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, thaumcraft.api.blocks.BlocksTC.spa.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack copy = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			copy = stack.copy();
			if (index < PLAYER_INV_START) {
				if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
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
}

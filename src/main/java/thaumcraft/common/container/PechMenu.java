package thaumcraft.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import thaumcraft.common.entities.monster.EntityPech;
import thaumcraft.common.registry.ThaumcraftMenus;

/**
 * Replaces the old (1.12.2) {@code ContainerPech}. Slot layout matches the original exactly: 0 = the trade
 * input (36, 29), 1-4 = a 2x2 output grid (106/124, 20/38) - real {@link SlotItemHandler}s against
 * {@link EntityPech#getTradeItems()}, same "no shared inventory-tile base" pattern {@link GolemBuilderMenu}
 * already established (replacing the original's shared-base {@code InventoryPech}) - then the usual 27
 * player-inventory + 9-hotbar slots.
 *
 * The original drove the "confirm trade" action through a client-side icon-button hit-test plus
 * {@code sendEnchantPacket}; this uses a real {@link #clickMenuButton} id 0 instead, the same modern
 * replacement {@link GolemBuilderMenu}/{@code SealConfigMenu} already established for that exact 1.12
 * mechanism. No {@code ContainerData} is needed here (unlike those two) - the trade's only visible state is
 * the slot contents themselves, which vanilla already syncs for free.
 *
 * This is this port's first menu tied to a live {@link net.minecraft.world.entity.Entity} rather than a
 * block position - {@link #stillValid} checks the Pech is still alive, still tamed, and still in range
 * instead of the usual {@code AbstractContainerMenu#stillValid(ContainerLevelAccess, Player, Block)} helper.
 */
public class PechMenu extends AbstractContainerMenu {

	private static final int INPUT_SLOT = 0;
	private static final int PLAYER_INV_START = 5;
	private static final int HOTBAR_END = 41;

	private final EntityPech pech;

	/** Client-side reconstruction - the real menu only ever exists server-side attached to a real
	 * {@link EntityPech}; the client just needs slots to render into, filled by the normal container-sync
	 * packets. */
	public PechMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new ItemStackHandler(5), null);
	}

	public PechMenu(int containerId, Inventory playerInventory, EntityPech pech) {
		this(containerId, playerInventory, pech.getTradeItems(), pech);
	}

	private PechMenu(int containerId, Inventory playerInventory, IItemHandler handler, EntityPech pech) {
		super(ThaumcraftMenus.PECH_TRADE.get(), containerId);
		this.pech = pech;

		addSlot(new SlotItemHandler(handler, INPUT_SLOT, 36, 29));
		for (int row = 0; row < 2; row++) {
			for (int col = 0; col < 2; col++) {
				addSlot(new SlotItemHandler(handler, 1 + col + row * 2, 106 + 18 * col, 20 + 18 * row));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	public EntityPech getPech() {
		return pech;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == 0 && pech != null) {
			pech.generateTradeContents();
			return true;
		}
		return super.clickMenuButton(player, id);
	}

	@Override
	public boolean stillValid(Player player) {
		return pech != null && pech.isAlive() && pech.isTamed() && player.distanceToSqr(pech) < 64.0;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (pech == null || player.level().isClientSide) return;
		pech.trading = false;
		for (int i = 0; i < 5; i++) {
			ItemStack leftover = pech.getTradeItems().extractItem(i, Integer.MAX_VALUE, false);
			if (!leftover.isEmpty()) {
				player.getInventory().placeItemBackInInventory(leftover);
			}
		}
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

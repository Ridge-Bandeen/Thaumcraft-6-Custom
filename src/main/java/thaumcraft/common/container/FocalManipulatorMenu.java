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
import thaumcraft.common.tiles.crafting.TileFocalManipulator;

/**
 * Replaces the old (1.12.2) {@code ContainerFocalManipulator}. Slot layout: 0 = focus item, 1-27 = player
 * inventory, 28-36 = hotbar - same "no shared inventory-tile base" purpose-built approach
 * {@code ArcaneWorkbenchMenu} already established, but simpler: this device has no implicit
 * recipe-matching grid (crafting is triggered by an explicit action, not "place matching ingredients"), so
 * there's no result slot/recipe lookup here at all.
 *
 * Uses a real {@link SlotItemHandler} against {@link TileFocalManipulator#getItemHandler()} rather than
 * vanilla's plain {@link Slot} (which wants a {@code Container}, not an {@code IItemHandler}) - first real
 * use of that NeoForge helper in this port.
 *
 * Drops the original's secondary duplicate-hotbar display panel (a pure UI convenience, not core
 * functionality) and the "craft" button wiring (needs the node-graph screen and its own client->server
 * packet, both a later commit in this same arc - see {@code TileFocalManipulator}'s own doc comment) -
 * {@link TileFocalManipulator#startCraft} is reachable directly from server-side code/tests already, just
 * not from this menu's own UI yet.
 */
public class FocalManipulatorMenu extends AbstractContainerMenu {

	private static final int FOCUS_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int HOTBAR_END = 37;

	private final TileFocalManipulator tile;
	private final ContainerLevelAccess access;

	/** Client-side placeholder constructor - {@code IMenuTypeExtension} always needs one of these (the
	 * client never has the real {@link TileFocalManipulator}; slot contents arrive via vanilla's own
	 * menu-sync afterward), same precedent {@code ArcaneWorkbenchMenu}'s own no-arg-tile overload already
	 * established. */
	public FocalManipulatorMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new ItemStackHandler(1), ContainerLevelAccess.NULL, null);
	}

	public FocalManipulatorMenu(int containerId, Inventory playerInventory, TileFocalManipulator tile) {
		this(containerId, playerInventory, tile.getItemHandler(), ContainerLevelAccess.create(tile.getLevel(), tile.getBlockPos()), tile);
	}

	private FocalManipulatorMenu(int containerId, Inventory playerInventory, IItemHandler focusSlotHandler, ContainerLevelAccess access, TileFocalManipulator tile) {
		super(ThaumcraftMenus.FOCAL_MANIPULATOR.get(), containerId);
		this.tile = tile;
		this.access = access;

		addSlot(new SlotItemHandler(focusSlotHandler, 0, 31, 191));

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	public TileFocalManipulator getTile() {
		return tile;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, thaumcraft.api.blocks.BlocksTC.focalManipulator.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack copy = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			copy = stack.copy();
			if (index == FOCUS_SLOT) {
				if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) {
					return ItemStack.EMPTY;
				}
			} else if (stack.getItem() instanceof thaumcraft.common.items.casters.ItemFocus) {
				if (!moveItemStackTo(stack, FOCUS_SLOT, PLAYER_INV_START, false)) {
					return ItemStack.EMPTY;
				}
			} else if (index >= PLAYER_INV_START && index < HOTBAR_END) {
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

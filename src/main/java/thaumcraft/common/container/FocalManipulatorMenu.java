package thaumcraft.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import thaumcraft.common.registry.ThaumcraftMenus;
import thaumcraft.common.registry.ThaumcraftSounds;
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
 * As the original the player inventory sits in a side panel left of the screen: the main inventory as three columns
 * of nine, the hotbar as a 3x3 block above it. The "craft" button is real ({@link #clickMenuButton}, button id 0) - reuses vanilla's
 * own generic container-button-click packet (the same mechanism an enchanting table's buttons use)
 * rather than a bespoke payload, since it only ever needs to carry one int. The node-graph itself still
 * has no UI (needs the canvas screen and its own client->server packet, a later commit in this same
 * arc - see {@code TileFocalManipulator}'s own doc comment) - {@link TileFocalManipulator#nodes} is only
 * reachable from server-side code/tests for now, but a future screen's "craft" button can already call
 * {@code sendButtonClick(0)} against a real, working server-side handler.
 */
public class FocalManipulatorMenu extends AbstractContainerMenu {

	private static final int FOCUS_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int HOTBAR_END = 37;

	private final TileFocalManipulator tile;
	private final ContainerLevelAccess access;

	/** Client side: the block's position comes with the open-menu packet, so the screen can read the client copy of the
	 * tile (its synced node tree, name and vis) as the original's screen read its table. */
	public FocalManipulatorMenu(int containerId, Inventory playerInventory, net.minecraft.core.BlockPos pos) {
		this(containerId, playerInventory, handlerOf(clientTile(playerInventory, pos)), ContainerLevelAccess.NULL, clientTile(playerInventory, pos));
	}

	private static IItemHandler handlerOf(@javax.annotation.Nullable TileFocalManipulator tile) {
		return tile != null ? tile.getItemHandler() : new ItemStackHandler(1);
	}

	@javax.annotation.Nullable
	private static TileFocalManipulator clientTile(Inventory playerInventory, net.minecraft.core.BlockPos pos) {
		return playerInventory.player.level().getBlockEntity(pos) instanceof TileFocalManipulator t ? t : null;
	}

	public FocalManipulatorMenu(int containerId, Inventory playerInventory, TileFocalManipulator tile) {
		this(containerId, playerInventory, tile.getItemHandler(), ContainerLevelAccess.create(tile.getLevel(), tile.getBlockPos()), tile);
	}

	private FocalManipulatorMenu(int containerId, Inventory playerInventory, IItemHandler focusSlotHandler, ContainerLevelAccess access, TileFocalManipulator tile) {
		super(ThaumcraftMenus.FOCAL_MANIPULATOR.get(), containerId);
		this.tile = tile;
		this.access = access;

		addSlot(new SlotItemHandler(focusSlotHandler, 0, 31, 191));

		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 9; j++) {
				addSlot(new Slot(playerInventory, j + i * 9 + 9, i * 18 - 62, 64 + j * 18));
			}
		}
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				addSlot(new Slot(playerInventory, i + j * 3, i * 18 - 62, j * 18 + 7));
			}
		}
	}

	public TileFocalManipulator getTile() {
		return tile;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, thaumcraft.api.blocks.BlocksTC.focalManipulator.get());
	}

	/** Button id 0 = "start craft" - replaces the original's own {@code enchantItem} hook. Only meaningful
	 * server-side (the client instance has no real {@link #tile}); a failed attempt plays the real custom
	 * {@code craftfail} cue (restored 2026-09-15, was a vanilla {@code VILLAGER_NO} stand-in - see {@link
	 * ThaumcraftSounds}'s own doc comment), matching the true original's own {@code SoundsTC.craftfail}
	 * exactly - distinct from {@code TileFocalManipulator#tick}'s own {@code wandfail} cue for the separate
	 * mid-charge abort case. */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == 0 && tile != null && !player.level().isClientSide) {
			boolean started = tile.startCraft(player);
			if (!started) {
				Level level = tile.getLevel();
				if (level != null) {
					level.playSound(null, tile.getBlockPos(), ThaumcraftSounds.CRAFTFAIL.get(), SoundSource.BLOCKS, 0.33f, 1.0f);
				}
			}
			return started;
		}
		return super.clickMenuButton(player, id);
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

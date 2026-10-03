package thaumcraft.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.api.items.ThaumcraftDataComponents.MirrorLink;
import thaumcraft.common.items.tools.ItemHandMirror;
import thaumcraft.common.registry.ThaumcraftMenus;
import thaumcraft.common.tiles.devices.TileMirror;

/**
 * Replaces the old (1.12.2) {@code ContainerHandMirror}. Real port of the original's own "drop it in, it's
 * instantly sent" single slot (80, 24): dropping any item into it immediately attempts real delivery via
 * {@link TileMirror#transportDirect} (through the real linked mirror recorded on the real Hand Mirror stack
 * that opened this menu) - on real success the slot clears; on real failure (the linked mirror vanished mid-
 * session) the item bounces back into the slot, matching the original's own real
 * {@code onCraftMatrixChanged} behavior exactly. Real, kept cut: a real {@link ItemHandMirror} itself can
 * never be placed into the send slot (matches the original's own real {@code slotClick}/
 * {@code transferStackInSlot} guard against feeding a mirror into itself).
 */
public class HandMirrorMenu extends AbstractContainerMenu {

	private static final int SEND_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int HOTBAR_END = 37;

	private final ItemStackHandler handler;
	private final ItemStack mirrorStack;
	private final Player player;
	private final Level level;

	/** Client-side reconstruction - see {@code PechMenu}'s own doc comment for why. */
	public HandMirrorMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, ItemStack.EMPTY);
	}

	public HandMirrorMenu(int containerId, Inventory playerInventory, ItemStack mirrorStack) {
		super(ThaumcraftMenus.HAND_MIRROR.get(), containerId);
		this.mirrorStack = mirrorStack;
		this.player = playerInventory.player;
		this.level = playerInventory.player.level();
		this.handler = new ItemStackHandler(1) {
			@Override
			public boolean isItemValid(int slot, ItemStack stack) {
				return !(stack.getItem() instanceof ItemHandMirror);
			}

			@Override
			protected void onContentsChanged(int slot) {
				super.onContentsChanged(slot);
				attemptDelivery();
			}
		};

		addSlot(new SlotItemHandler(handler, SEND_SLOT, 80, 24));
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	/** Real port of the original's own {@code onCraftMatrixChanged} - only ever real on the server (the
	 * client-side reconstruction above never receives real menu-opening data, so its own {@code mirrorStack}
	 * is always empty and this is a real no-op there). */
	private void attemptDelivery() {
		if (level.isClientSide || mirrorStack.isEmpty()) return;
		ItemStack held = handler.getStackInSlot(SEND_SLOT);
		if (held.isEmpty()) return;

		MirrorLink link = mirrorStack.get(ThaumcraftDataComponents.MIRROR_LINK.get());
		if (link == null || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
		net.minecraft.server.level.ServerLevel targetLevel = serverLevel.getServer().getLevel(link.dimension());
		if (targetLevel == null || !targetLevel.isLoaded(link.pos())) return; // the item waits in the slot
		if (!(targetLevel.getBlockEntity(link.pos()) instanceof TileMirror target)) {
			// as the original: the linked mirror is gone - the link breaks and the item stays
			ItemHandMirror.breakLink(mirrorStack, player);
			return;
		}
		if (target.transportDirect(held)) {
			handler.setStackInSlot(SEND_SLOT, ItemStack.EMPTY);
			player.playNotifySound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 0.1f, 1.0f);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!player.level().isClientSide) {
			ItemStack remaining = handler.getStackInSlot(SEND_SLOT);
			if (!remaining.isEmpty()) {
				player.drop(remaining, false);
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
			} else if (stack.getItem() instanceof ItemHandMirror) {
				return ItemStack.EMPTY;
			} else if (!moveItemStackTo(stack, SEND_SLOT, SEND_SLOT + 1, false)) {
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

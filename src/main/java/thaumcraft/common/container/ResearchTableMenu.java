package thaumcraft.common.container;

import java.util.function.Predicate;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;
import thaumcraft.common.registry.ThaumcraftMenus;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/**
 * Replaces the old (1.12.2) {@code ContainerResearchTable}. Slot layout: 0 = scribing tools (ink),
 * 1 = paper, 2-28 = player inventory, 29-37 = hotbar - same "no shared inventory-tile base" purpose-built
 * approach every device this session has used, with a real {@link SlotItemHandler} against
 * {@link TileResearchTable#getItemHandler()} (same precedent {@code FocalManipulatorMenu} already set).
 *
 * The real button-driven flow, direct translation: 1 = save/confirm the currently-selected draw, 2/3 = draw
 * 2 or 3 cards (consuming paper), 4/5/6 = pick card choice index 0/1/2 (checking/consuming its required
 * items, activating it, consuming ink, spending inspiration), 7 = finish the theory (only once complete),
 * 9 = abandon it. Starting a *new* theory isn't wired up here - the original triggered that with a
 * dedicated packet carrying the player's chosen aid set (a variable-size selection a single button id can't
 * carry) - real, separately-scoped follow-up work for whenever the canvas-style aid-picker screen exists;
 * {@link TileResearchTable#startNewTheory} is already real and directly callable server-side/from tests.
 *
 * Real, documented cut: no live sync of {@code data} (the drawn cards/category progress) to the viewing
 * player yet - same gap {@code FocalManipulatorMenu}'s own node graph left open, for the same reason (this
 * needs real per-action sync to whoever has the menu open, a job for the screen commit that actually needs
 * it, not invented speculatively here). Every button's real server-side effect on {@code tile.data} is
 * already correct and independently testable without it.
 */
public class ResearchTableMenu extends AbstractContainerMenu {

	private static final int INK_SLOT = 0;
	private static final int PAPER_SLOT = 1;
	private static final int PLAYER_INV_START = 2;
	private static final int HOTBAR_END = 38;
	private static final long ANTI_SPAM_MILLIS = 333L;

	private final TileResearchTable tile;
	private final ContainerLevelAccess access;
	private long lastActionTime;

	public ResearchTableMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new ItemStackHandler(2), ContainerLevelAccess.NULL, null);
	}

	public ResearchTableMenu(int containerId, Inventory playerInventory, TileResearchTable tile) {
		this(containerId, playerInventory, tile.getItemHandler(), ContainerLevelAccess.create(tile.getLevel(), tile.getBlockPos()), tile);
	}

	private ResearchTableMenu(int containerId, Inventory playerInventory, IItemHandler handler, ContainerLevelAccess access, TileResearchTable tile) {
		super(ThaumcraftMenus.RESEARCH_TABLE.get(), containerId);
		this.tile = tile;
		this.access = access;

		addSlot(new SlotItemHandler(handler, INK_SLOT, 16, 15));
		addSlot(new SlotItemHandler(handler, PAPER_SLOT, 224, 15));

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 77 + col * 18, 190 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 77 + col * 18, 248));
		}
	}

	public TileResearchTable getTile() {
		return tile;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		return switch (id) {
			case 1 -> confirmSelection();
			case 2 -> draw(player, 2);
			case 3 -> draw(player, 3);
			case 4, 5, 6 -> pick(player, id - 4);
			case 7 -> finish(player);
			case 9 -> abandon();
			default -> super.clickMenuButton(player, id);
		};
	}

	private boolean confirmSelection() {
		if (tile == null || tile.data == null) return false;
		ResearchTableData data = tile.data;
		if (data.lastDraw != null) {
			data.savedCards.add(data.lastDraw.card.getSeed());
		}
		for (ResearchTableData.CardChoice cc : data.cardChoices) {
			if (cc.selected) {
				data.lastDraw = cc;
				break;
			}
		}
		data.cardChoices.clear();
		tile.setChanged();
		return true;
	}

	private boolean draw(Player player, int amount) {
		if (tile == null || tile.data == null || tile.data.isComplete()) return false;
		if (!tile.consumePaperFromTable()) return false;
		tile.data.drawCards(amount, player);
		tile.setChanged();
		return true;
	}

	private boolean pick(Player player, int index) {
		if (tile == null || tile.data == null) return false;
		long now = System.currentTimeMillis();
		if (now - lastActionTime < ANTI_SPAM_MILLIS) return false;
		lastActionTime = now;

		if (index < 0 || index >= tile.data.cardChoices.size()) return false;
		ResearchTableData.CardChoice choice = tile.data.cardChoices.get(index);
		TheorycraftCard card = choice.card;

		ItemStack[] required = card.getRequiredItems();
		if (required != null) {
			for (ItemStack stack : required) {
				if (stack != null && !stack.isEmpty() && player.getInventory().countItem(stack.getItem()) < stack.getCount()) {
					return false;
				}
			}
			boolean[] consumed = card.getRequiredItemsConsumed();
			if (consumed != null && consumed.length == required.length) {
				for (int a = 0; a < required.length; a++) {
					ItemStack requiredStack = required[a];
					if (consumed[a] && requiredStack != null && !requiredStack.isEmpty()) {
						Predicate<ItemStack> matches = s -> s.getItem() == requiredStack.getItem();
						player.getInventory().clearOrCountMatchingItems(matches, requiredStack.getCount(), player.getInventory());
					}
				}
			}
		}

		if (card.activate(player, tile.data)) {
			tile.consumeInkFromTable();
			choice.selected = true;
			tile.data.addInspiration(-card.getInspirationCost());
			tile.setChanged();
			return true;
		}
		return false;
	}

	private boolean finish(Player player) {
		if (tile == null || tile.data == null || !tile.data.isComplete()) return false;
		tile.finishTheory(player);
		tile.setChanged();
		return true;
	}

	private boolean abandon() {
		if (tile == null || tile.data == null || tile.data.isComplete()) return false;
		tile.data = null;
		tile.setChanged();
		return true;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, thaumcraft.api.blocks.BlocksTC.researchTable.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack copy = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			copy = stack.copy();
			if (index == INK_SLOT || index == PAPER_SLOT) {
				if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) {
					return ItemStack.EMPTY;
				}
			} else if (stack.getItem() instanceof thaumcraft.api.items.IScribeTools) {
				if (!moveItemStackTo(stack, INK_SLOT, INK_SLOT + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (stack.getItem() == net.minecraft.world.item.Items.PAPER) {
				if (!moveItemStackTo(stack, PAPER_SLOT, PAPER_SLOT + 1, false)) {
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

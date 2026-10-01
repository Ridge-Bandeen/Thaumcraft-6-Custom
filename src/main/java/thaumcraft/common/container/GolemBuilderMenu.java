package thaumcraft.common.container;


import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import thaumcraft.api.golems.IGolemProperties;
import thaumcraft.common.golems.GolemProperties;
import thaumcraft.common.registry.ThaumcraftMenus;
import thaumcraft.common.tiles.crafting.TileGolemBuilder;

/**
 * Replaces the old (1.12.2) {@code ContainerGolemBuilder}. Slot layout as the original: 0 = the finished-placer output
 * slot (x 160, y 104), 1-27 player inventory, 28-36 hotbar (from x 24).
 *
 * As the original, the part choice lives in the screen ({@code GuiGolemBuilder}, research-gated there), which sends the
 * golem it shows ({@link thaumcraft.common.lib.network.ServerboundGolemBuilderPacket}): {@link #check} answers which
 * components the inventories beside the press hold (synced back here as a bit mask, the original's "stuff" reply), and
 * {@link #craft} starts the build ({@link TileGolemBuilder#startCraft}). The tile's cost/max cost sync through
 * {@link ContainerData}, as the original's window properties.
 */
public class GolemBuilderMenu extends AbstractContainerMenu {

	private static final int OUTPUT_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int HOTBAR_END = 37;

	public static final int DATA_COST = 0;
	public static final int DATA_MAX_COST = 1;
	/** Bit a set: the press found component a beside it (the original's hasStuff). */
	public static final int DATA_STUFF = 2;
	/** Bumped with every check answer so the screen redoes its component list (the original's ContainerGolemBuilder.redo). */
	public static final int DATA_STUFF_SERIAL = 3;
	private static final int DATA_COUNT = 4;

	private final TileGolemBuilder tile;
	private final ContainerLevelAccess access;
	private final ContainerData data = new SimpleContainerData(DATA_COUNT);

	public GolemBuilderMenu(int containerId, Inventory playerInventory) {
		this(containerId, playerInventory, new ItemStackHandler(1), ContainerLevelAccess.NULL, null);
	}

	public GolemBuilderMenu(int containerId, Inventory playerInventory, TileGolemBuilder tile) {
		this(containerId, playerInventory, tile.getItemHandler(), ContainerLevelAccess.create(tile.getLevel(), tile.getBlockPos()), tile);
	}

	private GolemBuilderMenu(int containerId, Inventory playerInventory, IItemHandler handler, ContainerLevelAccess access, TileGolemBuilder tile) {
		super(ThaumcraftMenus.GOLEM_BUILDER.get(), containerId);
		this.tile = tile;
		this.access = access;

		addSlot(new SlotItemHandler(handler, OUTPUT_SLOT, 160, 104));
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 24 + col * 18, 142 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 24 + col * 18, 200));
		}
		addDataSlots(data);
	}

	public TileGolemBuilder getTile() {
		return tile;
	}

	public int getCost() {
		return data.get(DATA_COST);
	}

	public int getMaxCost() {
		return data.get(DATA_MAX_COST);
	}

	/** Whether the press found component {@code index} beside it, per the last check. */
	public boolean pressHas(int index) {
		return index < 15 && (data.get(DATA_STUFF) & (1 << index)) != 0;
	}

	public int stuffSerial() {
		return data.get(DATA_STUFF_SERIAL);
	}

	/** The original's "check" message. */
	public boolean[] check(long id) {
		if (tile == null) return new boolean[0];
		boolean[] has = tile.checkCraft(id);
		int mask = 0;
		for (int a = 0; a < has.length && a < 15; a++) {
			if (has[a]) mask |= 1 << a;
		}
		data.set(DATA_STUFF, mask);
		data.set(DATA_STUFF_SERIAL, (data.get(DATA_STUFF_SERIAL) + 1) & 0x7FFF);
		return has;
	}

	/** The original's craft message (the screen only offers parts its player has researched; checked again here). */
	public boolean craft(Player player, long id) {
		if (tile == null) return false;
		IGolemProperties props = GolemProperties.fromLong(id);
		if (!knows(player, props.getMaterial().research) || !knows(player, props.getHead().research) || !knows(player, props.getArms().research)
				|| !knows(player, props.getLegs().research) || !knows(player, props.getAddon().research)) {
			return false;
		}
		boolean started = tile.startCraft(id, player);
		check(id);
		return started;
	}

	private static boolean knows(Player player, String[] research) {
		return research == null || research.length == 0 || thaumcraft.api.capabilities.ThaumcraftCapabilities.knowsResearchStrict(player, research);
	}

	/** Pulls the tile's own authoritative {@code cost}/{@code maxCost} into the synced {@link #data} every
	 * server tick, same "blockEntity field -> ContainerData -> diff-and-sync" shape vanilla's own
	 * {@code AbstractFurnaceMenu} uses for burn time/cook progress. */
	@Override
	public void broadcastChanges() {
		if (tile != null) {
			data.set(DATA_COST, tile.cost);
			data.set(DATA_MAX_COST, tile.maxCost);
		}
		super.broadcastChanges();
	}
	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, thaumcraft.api.blocks.BlocksTC.golemBuilder.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack copy = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			copy = stack.copy();
			if (index == OUTPUT_SLOT) {
				if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) {
					return ItemStack.EMPTY;
				}
			} else {
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

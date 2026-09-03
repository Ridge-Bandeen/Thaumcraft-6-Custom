package thaumcraft.common.container;

import java.util.function.Predicate;

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
import thaumcraft.api.golems.parts.GolemAddon;
import thaumcraft.api.golems.parts.GolemArm;
import thaumcraft.api.golems.parts.GolemHead;
import thaumcraft.api.golems.parts.GolemLeg;
import thaumcraft.api.golems.parts.GolemMaterial;
import thaumcraft.common.golems.GolemProperties;
import thaumcraft.common.registry.ThaumcraftMenus;
import thaumcraft.common.tiles.crafting.TileGolemBuilder;

/**
 * Replaces the old (1.12.2) {@code ContainerGolemBuilder}. Slot layout: 0 = the finished-placer output slot
 * (real {@link SlotItemHandler} against {@link TileGolemBuilder#getItemHandler()}, same "no shared
 * inventory-tile base" pattern every menu-backed device this session uses), 1-27 player inventory, 28-36
 * hotbar.
 *
 * The original drove part selection through 5 client-side {@code GuiGolemPropButton}s cycling each slot's
 * options and a start-craft {@code GuiGolemCraftButton}, syncing the chosen {@code long} id + cost/max cost
 * to the server as raw NBT messages. This port keeps the same 5-slot selection idea but drives it entirely
 * through real {@link #clickMenuButton} ids (0-4 cycle material/head/arm/leg/addon, 5 starts the craft) with
 * the current selection + cost/max cost synced for real via a plain {@link ContainerData} (the same
 * vanilla-furnace-style mechanism {@code AbstractFurnaceMenu} itself uses for burn time/cook progress, not
 * used by any device this port has ported before now) - no custom NBT packet needed.
 *
 * {@link #startCraft} checks and consumes the real component cost directly against the player's own
 * inventory - see {@code TileGolemBuilder}'s own doc comment for why the original's adjacent-chest scanning
 * ({@code InventoryUtils}) isn't ported.
 */
public class GolemBuilderMenu extends AbstractContainerMenu {

	private static final int OUTPUT_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int HOTBAR_END = 37;

	public static final int DATA_MATERIAL = 0;
	public static final int DATA_HEAD = 1;
	public static final int DATA_ARM = 2;
	public static final int DATA_LEG = 3;
	public static final int DATA_ADDON = 4;
	public static final int DATA_COST = 5;
	public static final int DATA_MAX_COST = 6;
	private static final int DATA_COUNT = 7;

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
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 142 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 200));
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

	public GolemMaterial getSelectedMaterial() {
		return GolemMaterial.getMaterials()[wrap(data.get(DATA_MATERIAL), GolemMaterial.getMaterials().length)];
	}

	public GolemHead getSelectedHead() {
		return GolemHead.getHeads()[wrap(data.get(DATA_HEAD), GolemHead.getHeads().length)];
	}

	public GolemArm getSelectedArm() {
		return GolemArm.getArms()[wrap(data.get(DATA_ARM), GolemArm.getArms().length)];
	}

	public GolemLeg getSelectedLeg() {
		return GolemLeg.getLegs()[wrap(data.get(DATA_LEG), GolemLeg.getLegs().length)];
	}

	public GolemAddon getSelectedAddon() {
		return GolemAddon.getAddons()[wrap(data.get(DATA_ADDON), GolemAddon.getAddons().length)];
	}

	private static int wrap(int value, int length) {
		return length <= 0 ? 0 : ((value % length) + length) % length;
	}

	public IGolemProperties buildSelectedProperties() {
		IGolemProperties props = new GolemProperties();
		props.setMaterial(getSelectedMaterial());
		props.setHead(getSelectedHead());
		props.setArms(getSelectedArm());
		props.setLegs(getSelectedLeg());
		props.setAddon(getSelectedAddon());
		return props;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		return switch (id) {
			case 0 -> cycle(DATA_MATERIAL, GolemMaterial.getMaterials().length);
			case 1 -> cycle(DATA_HEAD, GolemHead.getHeads().length);
			case 2 -> cycle(DATA_ARM, GolemArm.getArms().length);
			case 3 -> cycle(DATA_LEG, GolemLeg.getLegs().length);
			case 4 -> cycle(DATA_ADDON, GolemAddon.getAddons().length);
			case 5 -> startCraft(player);
			default -> super.clickMenuButton(player, id);
		};
	}

	private boolean cycle(int dataIndex, int length) {
		if (length <= 0 || tile == null) return false;
		data.set(dataIndex, wrap(data.get(dataIndex) + 1, length));
		return true;
	}

	/** Real translation of the original's own cost formula (2 per trait + 1 per component item, see
	 * {@link TileGolemBuilder#startCraft}) - checks then consumes the real component cost against the
	 * player's own inventory. */
	private boolean startCraft(Player player) {
		if (tile == null) return false;
		IGolemProperties props = buildSelectedProperties();
		ItemStack[] components = props.generateComponents();
		for (ItemStack required : components) {
			if (!required.isEmpty() && player.getInventory().countItem(required.getItem()) < required.getCount()) {
				return false;
			}
		}
		if (!tile.startCraft(props)) {
			return false;
		}
		for (ItemStack required : components) {
			if (required.isEmpty()) continue;
			Predicate<ItemStack> matches = s -> s.getItem() == required.getItem();
			player.getInventory().clearOrCountMatchingItems(matches, required.getCount(), player.getInventory());
		}
		return true;
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

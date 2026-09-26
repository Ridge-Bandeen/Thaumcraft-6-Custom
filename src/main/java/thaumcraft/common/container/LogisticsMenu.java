package thaumcraft.common.container;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.common.golems.seals.SealProvide;
import thaumcraft.common.registry.ThaumcraftMenus;

/**
 * Replaces the old (1.12.2) {@code ContainerLogistics}: 81 read-only "ghost" slots (9 x 9, at 19 + 19 * n) listing what
 * the player's golem Provide seals within 32 blocks can supply - every stocked stack the seal's filter allows, merged by
 * item and sorted by name, optionally narrowed by a search string - scrolled a row (9 items) at a time. Clicking a slot
 * only selects it client-side; the request goes out as a packet. Opened by the Golem Bell used away from a seal: the
 * requested items are delivered to the clicked block face, or to the player if the bell was used in the air.
 *
 * <p>Scroll position and range travel as {@link ContainerData}; menu buttons: 0 = scroll down, 1 = scroll up,
 * 22 = re-scan now, 100 + n = jump to row n.
 */
public class LogisticsMenu extends AbstractContainerMenu {

	public static final int SLOTS = 81;
	private static final int DATA_START = 0;
	private static final int DATA_END = 1;
	private static final int RANGE = 32;

	private final Player player;
	private final SimpleContainer input = new SimpleContainer(SLOTS) {
		@Override
		public int getMaxStackSize() {
			return Integer.MAX_VALUE;
		}
	};
	private final ContainerData data;
	@Nullable
	private final BlockPos target;
	@Nullable
	private final Direction side;

	private TreeMap<String, ItemStack> items = new TreeMap<>();
	private String searchText = "";
	private int start;
	private int end;
	private int lastTotal = -1;
	private int lastStart = -1;

	/** The delivery target as written into the open-menu extra data: a flag, then (if set) the block and its face. */
	private record TargetData(@Nullable BlockPos pos, @Nullable Direction side) {
		static TargetData read(FriendlyByteBuf buf) {
			return buf.readBoolean() ? new TargetData(buf.readBlockPos(), buf.readEnum(Direction.class)) : new TargetData(null, null);
		}
	}

	public static void writeTarget(FriendlyByteBuf buf, @Nullable BlockPos pos, @Nullable Direction side) {
		buf.writeBoolean(pos != null && side != null);
		if (pos != null && side != null) {
			buf.writeBlockPos(pos);
			buf.writeEnum(side);
		}
	}

	/** Client constructor. */
	public LogisticsMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
		this(containerId, playerInventory, TargetData.read(buf));
	}

	private LogisticsMenu(int containerId, Inventory playerInventory, TargetData target) {
		this(containerId, playerInventory, target.pos(), target.side());
	}

	public LogisticsMenu(int containerId, Inventory playerInventory, @Nullable BlockPos target, @Nullable Direction side) {
		super(ThaumcraftMenus.LOGISTICS.get(), containerId);
		this.player = playerInventory.player;
		this.target = target;
		this.side = side;
		this.data = new ContainerData() {
			@Override
			public int get(int index) {
				return index == DATA_START ? start : end;
			}

			@Override
			public void set(int index, int value) {
				if (index == DATA_START) start = value;
				else end = value;
			}

			@Override
			public int getCount() {
				return 2;
			}
		};
		addDataSlots(data);

		for (int a = 0; a < SLOTS; a++) {
			addSlot(new Slot(input, a, 19 + a % 9 * 19, 19 + a / 9 * 19) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}

				@Override
				public boolean mayPickup(Player player) {
					return false;
				}
			});
		}
		if (!player.level().isClientSide) refreshItemList(true);
	}

	@Nullable
	public BlockPos getTarget() {
		return target;
	}

	@Nullable
	public Direction getSide() {
		return side;
	}

	public int getStart() {
		return data.get(DATA_START);
	}

	public int getEnd() {
		return data.get(DATA_END);
	}

	public void setSearchText(String text) {
		searchText = text == null ? "" : text;
		start = 0;
		refreshItemList(true);
	}

	/** Rebuilds the visible window (and, when {@code full}, the whole list from the seals in range). */
	public void refreshItemList(boolean full) {
		if (player.level().isClientSide) return;
		int newTotal = lastTotal;
		if (full) {
			TreeMap<String, ItemStack> found = new TreeMap<>();
			newTotal = 0;
			String needle = searchText.toLowerCase(Locale.ROOT);
			for (Map.Entry<?, ISealEntity> e : GolemHelper.getSealEntities(player.level().dimension()).entrySet()) {
				ISealEntity seal = e.getValue();
				if (!(seal.getSeal() instanceof SealProvide provide) || !player.getUUID().toString().equals(seal.getOwner())) continue;
				if (seal.getSealPos().pos.distSqr(player.blockPosition()) > RANGE * RANGE) continue;
				IItemHandler handler = player.level().getCapability(Capabilities.ItemHandler.BLOCK, seal.getSealPos().pos, seal.getSealPos().face);
				if (handler == null) continue;
				for (int slot = 0; slot < handler.getSlots(); slot++) {
					ItemStack stack = handler.getStackInSlot(slot).copy();
					if (stack.isEmpty() || !provide.matchesFilters(stack)) continue;
					if (!needle.isEmpty() && !stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) continue;
					String key = stack.getHoverName().getString() + "|" + stack.getItem() + "|" + stack.getComponentsPatch();
					ItemStack existing = found.get(key);
					if (existing != null) stack.grow(existing.getCount());
					found.put(key, stack);
					newTotal += handler.getStackInSlot(slot).getCount();
				}
			}
			items = found;
		}
		end = Math.max(0, items.size() / 9 - 8);
		start = Math.max(0, Math.min(start, end));
		if (lastTotal != newTotal || start != lastStart || full) {
			lastTotal = newTotal;
			lastStart = start;
			input.clearContent();
			int skipped = 0;
			int placed = 0;
			for (ItemStack stack : items.values()) {
				if (++skipped <= start * 9) continue;
				input.setItem(placed, stack.copy());
				if (++placed >= SLOTS) break;
			}
		}
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (player.level().isClientSide) return true;
		if (id == 22) {
			refreshItemList(true);
			return true;
		}
		if (id == 0) {
			if (start < items.size() / 9 - 8) start++;
		} else if (id == 1) {
			if (start > 0) start--;
		} else if (id >= 100) {
			start = Math.max(0, Math.min(id - 100, Math.max(0, items.size() / 9 - 8)));
		} else {
			return false;
		}
		refreshItemList(false);
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}

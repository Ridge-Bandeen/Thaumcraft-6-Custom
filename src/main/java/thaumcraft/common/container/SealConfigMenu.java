package thaumcraft.common.container;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.IMenuProviderExtension;

import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealConfigArea;
import thaumcraft.api.golems.seals.ISealConfigFilter;
import thaumcraft.api.golems.seals.ISealConfigToggles;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.ISealGui;
import thaumcraft.common.registry.ThaumcraftMenus;

/**
 * Replaces the old (1.12.2) {@code SealBaseContainer} - real category/priority/color/lock/redstone-
 * sensitivity/blacklist/area/toggle state for ANY {@link ISealGui} seal, reused generically the same way the
 * original's own single container class was (never one container class per seal type).
 *
 * <p>Live state (priority, color, area size, locked, redstone-sensitive, blacklist, and up to 32 toggle
 * values packed as one bitmask) syncs through a plain {@link ContainerData} - the same
 * {@code AbstractFurnaceMenu}-style mechanism {@link GolemBuilderMenu} already established in this port,
 * replacing the original's own per-field {@code sendWindowProperty}/{@code updateProgressBar} pairs.
 * Category switches, priority/color nudges, lock/redstone/blacklist flips, area resizing, and toggle flips
 * all go through real {@link #clickMenuButton} ids (see the {@code BUTTON_*} constants) - the modern
 * replacement for the original's own {@code enchantItem}/{@code sendEnchantPacket} pairs, needing no custom
 * network packet.
 *
 * <p>Variable per-seal SHAPE (which GUI categories this seal exposes, its filter size, its toggles' own
 * keys/translation-key names, its required/forbidden trait ordinals) can't fit in {@link ContainerData}'s
 * fixed int array, so it's written once at menu-open time via {@link IMenuProviderExtension#writeClientSideData}
 * into the menu-open packet's own extra-data buffer and read back by the client constructor before any slots
 * are added - the first real use of that mechanism in this port (every previously-registered
 * {@link ThaumcraftMenus} menu ignores the buffer entirely, since none of them needed variable per-instance
 * shape before this).
 *
 * <p>Real, documented improvement over the original: the original's own lock-toggle button was only ever
 * owner-gated on the CLIENT side (the button simply wasn't sent unless the button was the owner) - its
 * server-side {@code enchantItem} handler applied no ownership check of its own, a real gap a modified client
 * could exploit. {@link #clickMenuButton} enforces the owner check for real, server-side, for both locking
 * and priority/color/area/toggle changes on a currently-locked seal.
 */
public class SealConfigMenu extends AbstractContainerMenu implements IMenuProviderExtension {

	public static final int DATA_PRIORITY = 0;
	public static final int DATA_COLOR = 1;
	public static final int DATA_AREA_X = 2;
	public static final int DATA_AREA_Y = 3;
	public static final int DATA_AREA_Z = 4;
	public static final int DATA_LOCKED = 5;
	public static final int DATA_REDSTONE = 6;
	public static final int DATA_BLACKLIST = 7;
	public static final int DATA_TOGGLES = 8;
	/** 9 consecutive slots (indices {@code DATA_FILTER_SIZE_BASE..+MAX_FILTER_SIZE-1}), one per ghost filter
	 * slot's real target count - the ghost slot's own {@link ItemStack} always has count 1 (or 0 when empty),
	 * so the actual stacksize-limiter target needs its own sync, unlike a normal slot where the stack's own
	 * count already IS the number to show. */
	private static final int DATA_FILTER_SIZE_BASE = 9;
	private static final int MAX_FILTER_SIZE = 9;
	private static final int DATA_COUNT = DATA_FILTER_SIZE_BASE + MAX_FILTER_SIZE;

	public static final int BUTTON_LOCK = 10;
	public static final int BUTTON_REDSTONE = 11;
	public static final int BUTTON_BLACKLIST = 12;
	public static final int BUTTON_PRIORITY_UP = 13;
	public static final int BUTTON_PRIORITY_DOWN = 14;
	public static final int BUTTON_COLOR_UP = 15;
	public static final int BUTTON_COLOR_DOWN = 16;
	public static final int BUTTON_AREA_Y_UP = 17;
	public static final int BUTTON_AREA_Y_DOWN = 18;
	public static final int BUTTON_AREA_X_UP = 19;
	public static final int BUTTON_AREA_X_DOWN = 20;
	public static final int BUTTON_AREA_Z_UP = 21;
	public static final int BUTTON_AREA_Z_DOWN = 22;
	public static final int BUTTON_TOGGLE_BASE = 30;

	private final ISealEntity sealEntity; // null on the client - see class doc comment
	private final SimpleContainer ghostInventory;
	private final ContainerData data = new SimpleContainerData(DATA_COUNT);

	private final boolean hasFilter;
	private final boolean hasArea;
	private final boolean hasStacksizeLimiters;
	private final int filterSize;
	private final int[] categories;
	private final String[] toggleKeys;
	private final String[] toggleNames;
	private final int[] requiredTagOrdinals;
	private final int[] forbiddenTagOrdinals;
	private final String owner;
	private int category;

	/** Server-side: the menu can read every real field straight off the live seal. */
	public SealConfigMenu(int containerId, Inventory playerInventory, ISealEntity sealEntity) {
		super(ThaumcraftMenus.SEAL_CONFIG.get(), containerId);
		this.sealEntity = sealEntity;
		ISeal seal = sealEntity.getSeal();

		this.hasFilter = seal instanceof ISealConfigFilter;
		this.hasArea = seal instanceof ISealConfigArea;
		this.hasStacksizeLimiters = hasFilter && ((ISealConfigFilter) seal).hasStacksizeLimiters();
		this.filterSize = hasFilter ? Math.min(((ISealConfigFilter) seal).getFilterSize(), MAX_FILTER_SIZE) : 0;
		this.categories = seal instanceof ISealGui gui ? gui.getGuiCategories() : new int[] { ISealGui.CAT_PRIORITY, ISealGui.CAT_TAGS };
		this.category = categories[0];
		this.owner = sealEntity.getOwner();

		if (seal instanceof ISealConfigToggles toggles) {
			ISealConfigToggles.SealToggle[] t = toggles.getToggles();
			toggleKeys = new String[t.length];
			toggleNames = new String[t.length];
			for (int i = 0; i < t.length; i++) {
				toggleKeys[i] = t[i].getKey();
				toggleNames[i] = t[i].getName();
			}
		} else {
			toggleKeys = new String[0];
			toggleNames = new String[0];
		}
		requiredTagOrdinals = ordinalsOf(seal.getRequiredTags());
		forbiddenTagOrdinals = ordinalsOf(seal.getForbiddenTags());

		ghostInventory = new SimpleContainer(Math.max(filterSize, 1));
		if (hasFilter) {
			NonNullList<ItemStack> filterItems = ((ISealConfigFilter) seal).getInv();
			for (int i = 0; i < filterSize; i++) {
				ghostInventory.setItem(i, i < filterItems.size() ? filterItems.get(i) : ItemStack.EMPTY);
			}
		}

		addGhostSlots();
		addPlayerSlots(playerInventory);
		addDataSlots(data);
	}

	/** Client-side: no live seal to read from - every real field this constructor needs comes from the
	 * extra-data buffer {@link #writeClientSideData} wrote, read back in the exact order it was written. */
	public SealConfigMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
		super(ThaumcraftMenus.SEAL_CONFIG.get(), containerId);
		this.sealEntity = null;
		this.hasFilter = buf.readBoolean();
		this.filterSize = buf.readVarInt();
		this.hasStacksizeLimiters = buf.readBoolean();
		this.hasArea = buf.readBoolean();
		this.categories = buf.readVarIntArray();
		this.category = categories[0];
		this.owner = buf.readUtf();
		int toggleCount = buf.readVarInt();
		toggleKeys = new String[toggleCount];
		toggleNames = new String[toggleCount];
		for (int i = 0; i < toggleCount; i++) {
			toggleKeys[i] = buf.readUtf();
			toggleNames[i] = buf.readUtf();
		}
		requiredTagOrdinals = buf.readVarIntArray();
		forbiddenTagOrdinals = buf.readVarIntArray();

		ghostInventory = new SimpleContainer(Math.max(filterSize, 1));
		addGhostSlots();
		addPlayerSlots(playerInventory);
		addDataSlots(data);
	}

	private static int[] ordinalsOf(EnumGolemTrait[] tags) {
		if (tags == null) return new int[0];
		int[] out = new int[tags.length];
		for (int i = 0; i < tags.length; i++) out[i] = tags[i].ordinal();
		return out;
	}

	/** Same pixel layout the original computed for its ghost-slot grid, centered the same way. */
	private void addGhostSlots() {
		int middleX = 88;
		int middleY = 72;
		int s = Math.max(filterSize, 1);
		int sx = 16 + (s - 1) % 3 * 12;
		int sy = 16 + (s - 1) / 3 * 12;
		for (int a = 0; a < filterSize; a++) {
			int x = a % 3;
			int y = a / 3;
			addSlot(new GhostSlot(ghostInventory, a, middleX + x * 24 - sx + 8, middleY + y * 24 - sy + 8) {
				@Override
				public boolean isActive() {
					return category == ISealGui.CAT_FILTER;
				}
			});
		}
	}

	private void addPlayerSlots(Inventory playerInventory) {
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 150 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, 208));
		}
	}

	@Override
	public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(hasFilter);
		buf.writeVarInt(filterSize);
		buf.writeBoolean(hasStacksizeLimiters);
		buf.writeBoolean(hasArea);
		buf.writeVarIntArray(categories);
		buf.writeUtf(owner);
		buf.writeVarInt(toggleKeys.length);
		for (int i = 0; i < toggleKeys.length; i++) {
			buf.writeUtf(toggleKeys[i]);
			buf.writeUtf(toggleNames[i]);
		}
		buf.writeVarIntArray(requiredTagOrdinals);
		buf.writeVarIntArray(forbiddenTagOrdinals);
	}

	/** Pulls the live seal's own authoritative fields into the synced {@link #data} every server tick - same
	 * "real field -> ContainerData -> diff-and-sync" shape {@link GolemBuilderMenu} already established. */
	@Override
	public void broadcastChanges() {
		if (sealEntity != null) {
			data.set(DATA_PRIORITY, sealEntity.getPriority());
			data.set(DATA_COLOR, sealEntity.getColor());
			data.set(DATA_AREA_X, sealEntity.getArea().getX());
			data.set(DATA_AREA_Y, sealEntity.getArea().getY());
			data.set(DATA_AREA_Z, sealEntity.getArea().getZ());
			data.set(DATA_LOCKED, sealEntity.isLocked() ? 1 : 0);
			data.set(DATA_REDSTONE, sealEntity.isRedstoneSensitive() ? 1 : 0);
			if (hasFilter) {
				ISealConfigFilter filter = (ISealConfigFilter) sealEntity.getSeal();
				data.set(DATA_BLACKLIST, filter.isBlacklist() ? 1 : 0);
				for (int i = 0; i < filterSize; i++) {
					data.set(DATA_FILTER_SIZE_BASE + i, filter.getFilterSlotSize(i));
				}
			}
			if (sealEntity.getSeal() instanceof ISealConfigToggles toggles) {
				int mask = 0;
				ISealConfigToggles.SealToggle[] t = toggles.getToggles();
				for (int i = 0; i < t.length && i < 32; i++) {
					if (t[i].getValue()) mask |= 1 << i;
				}
				data.set(DATA_TOGGLES, mask);
			}
		}
		super.broadcastChanges();
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (sealEntity == null) return false;
		// as the original, a tab switch sends the tab's index (the filter's ghost slots only live on the filter tab)
		if (id >= 0 && id < categories.length) {
			category = categories[id];
			return true;
		}
		if (isLockedForNonOwner(player) && id != BUTTON_LOCK) return false;
		boolean changed = switch (id) {
			case BUTTON_LOCK -> ownerOnly(player, () -> sealEntity.setLocked(!sealEntity.isLocked()));
			case BUTTON_REDSTONE -> set(() -> sealEntity.setRedstoneSensitive(!sealEntity.isRedstoneSensitive()));
			case BUTTON_BLACKLIST -> hasFilter && set(() -> filter().setBlacklist(!filter().isBlacklist()));
			case BUTTON_PRIORITY_UP -> set(() -> sealEntity.setPriority(clamp((byte) (sealEntity.getPriority() + 1), (byte) -5, (byte) 5)));
			case BUTTON_PRIORITY_DOWN -> set(() -> sealEntity.setPriority(clamp((byte) (sealEntity.getPriority() - 1), (byte) -5, (byte) 5)));
			case BUTTON_COLOR_UP -> set(() -> sealEntity.setColor(clamp((byte) (sealEntity.getColor() + 1), (byte) 0, (byte) 16)));
			case BUTTON_COLOR_DOWN -> set(() -> sealEntity.setColor(clamp((byte) (sealEntity.getColor() - 1), (byte) 0, (byte) 16)));
			case BUTTON_AREA_Y_UP -> hasArea && resizeArea(0, 1, 0);
			case BUTTON_AREA_Y_DOWN -> hasArea && resizeArea(0, -1, 0);
			case BUTTON_AREA_X_UP -> hasArea && resizeArea(1, 0, 0);
			case BUTTON_AREA_X_DOWN -> hasArea && resizeArea(-1, 0, 0);
			case BUTTON_AREA_Z_UP -> hasArea && resizeArea(0, 0, 1);
			case BUTTON_AREA_Z_DOWN -> hasArea && resizeArea(0, 0, -1);
			default -> id >= BUTTON_TOGGLE_BASE && flipToggle(id - BUTTON_TOGGLE_BASE);
		};
		// colour, area and redstone show in the world while a seal or Golem Bell is held - keep clients up to date
		if (changed && !player.level().isClientSide) sealEntity.syncToClient(player.level());
		return changed;
	}

	private boolean isLockedForNonOwner(Player player) {
		return sealEntity.isLocked() && !owner.equals(player.getStringUUID());
	}

	private boolean ownerOnly(Player player, Runnable action) {
		if (!owner.equals(player.getStringUUID())) return false;
		action.run();
		return true;
	}

	private boolean set(Runnable action) {
		action.run();
		return true;
	}

	private ISealConfigFilter filter() {
		return (ISealConfigFilter) sealEntity.getSeal();
	}

	private static byte clamp(byte value, byte min, byte max) {
		return (byte) Math.max(min, Math.min(max, value));
	}

	private boolean resizeArea(int dx, int dy, int dz) {
		var area = sealEntity.getArea();
		int nx = Math.max(1, Math.min(8, area.getX() + dx));
		int ny = Math.max(1, Math.min(8, area.getY() + dy));
		int nz = Math.max(1, Math.min(8, area.getZ() + dz));
		sealEntity.setArea(new net.minecraft.core.BlockPos(nx, ny, nz));
		return true;
	}

	private boolean flipToggle(int index) {
		if (!(sealEntity.getSeal() instanceof ISealConfigToggles toggles)) return false;
		ISealConfigToggles.SealToggle[] t = toggles.getToggles();
		if (index < 0 || index >= t.length) return false;
		toggles.setToggle(index, !t[index].getValue());
		return true;
	}

	/** Ghost-slot interaction: left click sets/adds the cursor item (accumulating a target count when this
	 * seal has real stacksize limiters), right click removes/decrements, matching the original's own
	 * behavior - see this class's own doc comment. Pushes the real change straight into the live
	 * {@link ISealConfigFilter}; a no-op on the client (no live seal to mutate there). */
	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {
		if (hasFilter && slotId >= 0 && slotId < filterSize) {
			if (sealEntity != null && category == ISealGui.CAT_FILTER) handleGhostSlotClick(slotId, button, clickType);
			return;
		}
		super.clicked(slotId, button, clickType, player);
	}

	private void handleGhostSlotClick(int slotId, int button, ClickType clickType) {
		ISealConfigFilter filter = filter();
		ItemStack cursor = getCarried();
		Slot slot = slots.get(slotId);
		int step = clickType == ClickType.QUICK_MOVE ? 10 : 1;

		if (button == 1) {
			if (!hasStacksizeLimiters) {
				slot.set(ItemStack.EMPTY);
				filter.setFilterSlotSize(slotId, 0);
			} else if (cursor.isEmpty()) {
				if (slot.hasItem()) {
					int newSize = filter.getFilterSlotSize(slotId) - step;
					if (newSize <= 0) {
						slot.set(ItemStack.EMPTY);
						filter.setFilterSlotSize(slotId, 0);
					} else {
						filter.setFilterSlotSize(slotId, newSize);
					}
				}
			} else if (slot.hasItem() && ItemStack.isSameItemSameComponents(cursor, slot.getItem())) {
				int newSize = Math.max(filter.getFilterSlotSize(slotId) - cursor.getCount(), 0);
				filter.setFilterSlotSize(slotId, newSize);
				if (newSize <= 0) slot.set(ItemStack.EMPTY);
			}
		} else if (cursor.isEmpty()) {
			if (hasStacksizeLimiters && slot.hasItem()) {
				filter.setFilterSlotSize(slotId, filter.getFilterSlotSize(slotId) + step);
			}
		} else {
			ItemStack single = cursor.copyWithCount(1);
			if (!hasStacksizeLimiters) {
				filter.setFilterSlotSize(slotId, 0);
			} else if (slot.hasItem() && ItemStack.isSameItemSameComponents(single, slot.getItem())) {
				filter.setFilterSlotSize(slotId, filter.getFilterSlotSize(slotId) + cursor.getCount());
			} else {
				filter.setFilterSlotSize(slotId, 0);
			}
			slot.set(single);
		}
		filter.setFilterSlot(slotId, slot.getItem());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		if (sealEntity == null) return true;
		return GolemHelper.getSealEntity(player.level().dimension(), sealEntity.getSealPos()) == sealEntity;
	}

	// --- real client-side accessors, backing SealConfigScreen ---

	public int getPriority() {
		return data.get(DATA_PRIORITY);
	}

	public int getColor() {
		return data.get(DATA_COLOR);
	}

	public int getAreaX() {
		return data.get(DATA_AREA_X);
	}

	public int getAreaY() {
		return data.get(DATA_AREA_Y);
	}

	public int getAreaZ() {
		return data.get(DATA_AREA_Z);
	}

	public boolean isLocked() {
		return data.get(DATA_LOCKED) != 0;
	}

	public boolean isRedstoneSensitive() {
		return data.get(DATA_REDSTONE) != 0;
	}

	public boolean isBlacklist() {
		return data.get(DATA_BLACKLIST) != 0;
	}

	public boolean isToggleOn(int index) {
		return (data.get(DATA_TOGGLES) & (1 << index)) != 0;
	}

	/** The real target count for ghost filter slot {@code index} (0 = "any amount"/unset) - see the
	 * {@code DATA_FILTER_SIZE_BASE} field's own doc comment for why this needs its own sync. */
	public int getFilterSlotSize(int index) {
		return data.get(DATA_FILTER_SIZE_BASE + index);
	}

	public boolean hasFilter() {
		return hasFilter;
	}

	public boolean hasArea() {
		return hasArea;
	}

	public boolean hasStacksizeLimiters() {
		return hasStacksizeLimiters;
	}

	public int getFilterSize() {
		return filterSize;
	}

	public int[] getCategories() {
		return categories;
	}

	/** The open tab: as the original, the filter's ghost slots only exist on the filter tab. */
	public int getCategory() {
		return category;
	}

	public void setCategory(int category) {
		this.category = category;
	}

	public String[] getToggleKeys() {
		return toggleKeys;
	}

	public String[] getToggleNames() {
		return toggleNames;
	}

	public int[] getRequiredTagOrdinals() {
		return requiredTagOrdinals;
	}

	public int[] getForbiddenTagOrdinals() {
		return forbiddenTagOrdinals;
	}

	public String getOwner() {
		return owner;
	}

	/** Whether the ghost slot at {@code index} currently has an assigned filter item and, if this seal has
	 * real stacksize limiters, what its target count is - 0 means "any amount"/unset. */
	public ItemStack getGhostItem(int index) {
		return ghostInventory.getItem(index);
	}

	/** Ghost slot - always placeable, never directly pickup-able (matches the original's own {@code SlotGhost}:
	 * items only ever leave via {@link #handleGhostSlotClick}'s own real right-click/decrement handling, never
	 * vanilla's normal pick-up-a-stack gesture). */
	private static class GhostSlot extends Slot {
		GhostSlot(SimpleContainer container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return true;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public int getMaxStackSize() {
			return Integer.MAX_VALUE;
		}
	}
}

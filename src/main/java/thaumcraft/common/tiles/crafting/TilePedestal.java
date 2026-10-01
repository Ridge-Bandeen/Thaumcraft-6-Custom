package thaumcraft.common.tiles.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TilePedestal}. Holds a single item, placed/removed by right-clicking the
 * block above it - see {@link thaumcraft.common.blocks.devices.BlockPedestal}. The infusion altar
 * ({@link TileInfusionMatrix}) reads and writes this slot directly during crafting.
 *
 * As the original: hoppers and pipes reach its one slot ({@link #getItemHandler}); the held item floats above it
 * ({@code thaumcraft.client.render.TilePedestalRenderer}, which is why {@link #setChanged()} pushes updates to
 * clients); and {@link #findInstabilityMitigator} follows the Redstone Inlay charge to a stabilizer.
 */
public class TilePedestal extends BlockEntity {

	private ItemStack item = ItemStack.EMPTY;

	public TilePedestal(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.PEDESTAL.get(), pos, state);
	}

	public ItemStack getItem() {
		return item;
	}

	public void setItem(ItemStack stack) {
		this.item = stack;
		setChanged();
	}

	/** Drops the held item at this position and clears the slot - used when the pedestal itself breaks. */
	public void dropContents(Level level) {
		if (item.isEmpty()) return;
		ItemEntity out = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, item);
		level.addFreshEntity(out);
		item = ItemStack.EMPTY;
	}

	/** The original's slot rules for hoppers and pipes (TileThaumcraftInventory, size 1, stack limit 1): any side, an item
	 * goes in only when the pedestal is empty, and can always be taken out. */
	private final net.neoforged.neoforge.items.IItemHandler itemHandler = new net.neoforged.neoforge.items.IItemHandler() {
		@Override
		public int getSlots() {
			return 1;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return item;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if (stack.isEmpty() || !item.isEmpty()) return stack;
			if (!simulate) setItem(stack.copyWithCount(1));
			return stack.getCount() > 1 ? stack.copyWithCount(stack.getCount() - 1) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			if (amount <= 0 || item.isEmpty()) return ItemStack.EMPTY;
			ItemStack out = item.copy();
			if (!simulate) setItem(ItemStack.EMPTY);
			return out;
		}

		@Override
		public int getSlotLimit(int slot) {
			return 1;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return item.isEmpty();
		}
	};

	public net.neoforged.neoforge.items.IItemHandler getItemHandler() {
		return itemHandler;
	}

	/** The original's {@code findInstabilityMitigator}: follows the inlay charge to a stabilizer (see TileStabilizer). */
	public BlockPos findInstabilityMitigator() {
		return level == null ? null : thaumcraft.common.tiles.devices.TileStabilizer.findNearbyStabilizer(level, worldPosition);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		item = tag.contains("Item") ? ItemStack.parseOptional(registries, tag.getCompound("Item")) : ItemStack.EMPTY;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (!item.isEmpty()) {
			tag.put("Item", item.save(registries, new CompoundTag()));
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	/**
	 * Real, confirmed-necessary fix (found while scoping the Infusion Matrix renderer): plain
	 * {@link BlockEntity#setChanged()} only marks the containing chunk unsaved - it does <b>not</b> push a
	 * {@code ClientboundBlockEntityDataPacket} to nearby clients (confirmed by reading vanilla's own
	 * {@code BlockEntity#setChanged}/{@code Level#blockEntityChanged}). Without this override, a real,
	 * already-loaded client only ever sees this tile's item at the moment its chunk was first sent - not on
	 * any later {@link #setItem}. Matches vanilla's own real fix for the same problem (see
	 * {@code SignBlockEntity}, which pairs every {@code setChanged()} call with an explicit
	 * {@code level.sendBlockUpdated(...)}) - centralized here instead of at each call site.
	 */
	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}
}

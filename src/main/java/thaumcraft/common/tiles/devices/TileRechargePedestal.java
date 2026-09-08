package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.RechargeHelper;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileRechargePedestal} - and the first device this port has built since
 * the "shared inventory-tile base" was scoped and deferred several times (Infernal Furnace, Alembic).
 * Rather than porting a generic {@code TileThaumcraftInventory}/{@code BlockTCDevice} pair (both fully
 * superseded by modern machinery - see the port-tracking memory notes on why), this is a purpose-built
 * {@link BlockEntity} using NeoForge's own real {@link ItemStackHandler} directly, the same
 * "purpose-build it standalone" pattern {@link thaumcraft.common.tiles.crafting.TileCrucible} already
 * established. A second, differently-shaped device would still need its own inventory wiring - there's
 * been no second real consumer yet to justify factoring out a shared abstract base speculatively.
 *
 * Core loop unchanged from the original: holds one {@link IRechargable} item, and every 10 ticks calls
 * {@link RechargeHelper#rechargeItem} to drain vis from the local aura into it - now genuinely functional,
 * since {@code AuraHelper} (which {@code RechargeHelper} calls through) stopped being a stub earlier this
 * session.
 *
 * Simplified vs the original:
 * <ul>
 * <li>No {@code IAspectContainer} implementation (goggles-of-revealing display) - goggles aren't ported,
 * so there's no consumer for it yet.</li>
 * <li>No particle/sound FX on a successful recharge tick, same as every other device this session.</li>
 * <li>Custom packet-based partial-slot sync ({@code TileThaumcraftInventory#syncSlots}) is replaced by
 * vanilla's own full block-entity sync ({@code getUpdateTag}/{@code handleUpdateTag}, inherited unchanged
 * from {@link BlockEntity}) - the original's whole-tile sync-splitting existed to reduce 1.12.2 network
 * traffic and has no real modern equivalent (nor is one needed for a single-slot device).</li>
 * </ul>
 *
 * <b>Real bug fix (found while scoping the Infusion Matrix renderer):</b> {@link BlockEntity}'s own default
 * {@code getUpdateTag}/{@code getUpdatePacket} return an empty tag / {@code null} - a plain {@code BlockEntity}
 * subclass that never overrides them (as this one didn't, until now) never syncs <i>any</i> of its data to
 * clients, not even on initial chunk load. That meant {@code TileRechargePedestalRenderer}'s floating item
 * could never actually render - it was always reading an empty client-side copy. Fixed below the same way
 * {@link thaumcraft.common.tiles.crafting.TilePedestal} and {@link thaumcraft.common.tiles.crafting.TileThaumatorium}
 * already do it, plus a {@link #setChanged()} override so later item changes push live instead of only
 * showing up after the chunk is reloaded (see that override's own doc comment).
 */
public class TileRechargePedestal extends BlockEntity {

	private final ItemStackHandler items = new ItemStackHandler(1) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return stack.getItem() instanceof IRechargable;
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};

	private int counter;

	public TileRechargePedestal(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.RECHARGE_PEDESTAL.get(), pos, state);
	}

	public ItemStack getItem() {
		return items.getStackInSlot(0);
	}

	public void setItem(ItemStack stack) {
		items.setStackInSlot(0, stack);
	}

	public boolean accepts(ItemStack stack) {
		return items.isItemValid(0, stack);
	}

	public IItemHandler getItemHandler() {
		return items;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileRechargePedestal tile) {
		if (level.isClientSide) return;
		if (tile.counter++ % 10 != 0) return;
		ItemStack stack = tile.getItem();
		if (!stack.isEmpty() && RechargeHelper.rechargeItem(level, stack, pos, null, 5) > 0.0f) {
			tile.setChanged();
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("Items", items.serializeNBT(registries));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.deserializeNBT(registries, tag.getCompound("Items"));
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	/** See {@link thaumcraft.common.tiles.crafting.TilePedestal#setChanged()}'s own doc comment - same real
	 * fix, same reason. */
	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
	}
}

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
 * Simplified for this pass (all real features, just not built yet):
 * <ul>
 * <li>No floating-item {@code BlockEntityRenderer} - the original showed the held item hovering/spinning
 * above the pedestal via a TESR. No custom renderer has been ported this whole session (see the port-tracking
 * memory notes); the held item is only visible via the item's own drop when the pedestal breaks, or by
 * right-clicking it back out.</li>
 * <li>No hopper/container capability - a single plain {@code ItemStack} field, not a real {@code Container}.
 * Automation would need to interact through a wand/player right-click, same as the original's "pedestals
 * aren't hoppers" design.</li>
 * <li>No "instability mitigator" charge network ({@code BlockInlay}/{@code TileStabilizer} chain) - that
 * whole subsystem isn't ported; see {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix}'s doc
 * comment for what else got cut alongside it.</li>
 * </ul>
 *
 * Now has a real floating-item renderer ({@code thaumcraft.client.render.TilePedestalRenderer}), so
 * {@link #setChanged()} is overridden to actually push the update to nearby clients - see that override's
 * own doc comment for why plain {@code setChanged()} alone isn't enough.
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

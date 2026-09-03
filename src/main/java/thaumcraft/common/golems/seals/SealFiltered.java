package thaumcraft.common.golems.seals;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealConfigFilter;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.ISealGui;

/**
 * Replaces the old (1.12.2) {@code SealFiltered} - the shared item-filter base {@link SealBreaker} extends.
 * Real, but stores the filter by real {@link Item} identity rather than full {@link ItemStack}s (unlike the
 * original's own {@code NonNullList<ItemStack>} + {@code ItemStackHelper} NBT round trip): a modern
 * {@code ItemStack}'s full NBT serialization needs a {@code HolderLookup.Provider} to (de)serialize its
 * data components, which {@link ISeal#readCustomNBT}/{@link ISeal#writeCustomNBT} have no way to supply
 * (the interface takes only a bare {@link CompoundTag}, matching the original exactly). Storing plain item
 * ids sidesteps that entirely - it's also a better fit for what a block/item TYPE filter conceptually wants
 * anyway (matching item identity, not stack count or NBT/components). {@link #getInv()} still returns real
 * {@link ItemStack}s (one per filter slot, freshly resolved from the stored id) to satisfy
 * {@link ISealConfigFilter}'s own contract.
 *
 * Real, documented simplification: no ore-dictionary-style fuzzy matching or metadata-aware matching (both
 * gone in modern Minecraft anyway - metadata subtypes don't exist, and tags are the nearest modern
 * replacement for ore-dictionary-style grouping, not wired in here) - filter entries match by exact
 * {@link Item} identity only.
 */
public abstract class SealFiltered implements ISeal, ISealGui, ISealConfigFilter {

	protected final ResourceLocation[] filterItems = new ResourceLocation[getFilterSize()];
	protected final NonNullList<Integer> filterSize = NonNullList.withSize(getFilterSize(), 0);
	protected boolean blacklist = true;

	@Override
	public void readCustomNBT(CompoundTag nbt) {
		for (int i = 0; i < filterItems.length; i++) {
			String key = "filter" + i;
			filterItems[i] = nbt.contains(key) ? ResourceLocation.tryParse(nbt.getString(key)) : null;
			filterSize.set(i, nbt.getInt("filterSize" + i));
		}
		blacklist = nbt.getBoolean("bl");
	}

	@Override
	public void writeCustomNBT(CompoundTag nbt) {
		for (int i = 0; i < filterItems.length; i++) {
			if (filterItems[i] != null) {
				nbt.putString("filter" + i, filterItems[i].toString());
			}
			nbt.putInt("filterSize" + i, filterSize.get(i));
		}
		nbt.putBoolean("bl", blacklist);
	}

	@Override
	public Object returnContainer(Level world, Player player, BlockPos pos, Direction side, ISealEntity seal) {
		return null;
	}

	@Override
	public Object returnGui(Level world, Player player, BlockPos pos, Direction side, ISealEntity seal) {
		return null;
	}

	@Override
	public int getFilterSize() {
		return 1;
	}

	@Override
	public NonNullList<ItemStack> getInv() {
		NonNullList<ItemStack> out = NonNullList.withSize(filterItems.length, ItemStack.EMPTY);
		for (int i = 0; i < filterItems.length; i++) {
			if (filterItems[i] != null && BuiltInRegistries.ITEM.containsKey(filterItems[i])) {
				out.set(i, new ItemStack(BuiltInRegistries.ITEM.get(filterItems[i])));
			}
		}
		return out;
	}

	@Override
	public NonNullList<Integer> getSizes() {
		return filterSize;
	}

	@Override
	public ItemStack getFilterSlot(int i) {
		return getInv().get(i);
	}

	@Override
	public int getFilterSlotSize(int i) {
		return filterSize.get(i);
	}

	@Override
	public void setFilterSlot(int i, ItemStack stack) {
		filterItems[i] = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
	}

	@Override
	public void setFilterSlotSize(int i, int size) {
		filterSize.set(i, size);
	}

	@Override
	public boolean isBlacklist() {
		return blacklist;
	}

	@Override
	public void setBlacklist(boolean black) {
		blacklist = black;
	}

	@Override
	public boolean hasStacksizeLimiters() {
		return false;
	}

	/** Real matching helper every subclass shares: does the given item appear in any real (non-empty)
	 * filter slot? */
	protected boolean matchesFilter(Item item) {
		for (ResourceLocation id : filterItems) {
			if (id != null && BuiltInRegistries.ITEM.get(id) == item) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_FILTER };
	}
}

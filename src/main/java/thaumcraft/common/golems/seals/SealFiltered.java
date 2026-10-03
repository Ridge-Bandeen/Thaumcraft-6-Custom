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
 * Replaces the old (1.12.2) {@code SealFiltered} - the shared item-filter base of the logistics and breaker seals. As
 * the original, the filter holds whole stacks (their components too), with a size limit per slot and a
 * blacklist/whitelist switch; each seal matches against it with its own toggles ({@link #match},
 * {@link SealFilters}). The stacks are saved with the server's registries (the seal NBT hooks get none of their
 * own); a filter saved before this kept only item ids, which still load.
 */
public abstract class SealFiltered implements ISeal, ISealGui, ISealConfigFilter {

	protected final NonNullList<ItemStack> filter = NonNullList.withSize(getFilterSize(), ItemStack.EMPTY);
	protected final NonNullList<Integer> filterSize = NonNullList.withSize(getFilterSize(), 0);
	protected boolean blacklist = true;

	/** How this seal compares stacks with its filter (its meta/NBT/ore/mod toggles); exact by default. */
	protected SealFilters.Match match() {
		return SealFilters.Match.STRICT;
	}

	/** Whether {@code stack} passes this seal's filter. */
	protected boolean passes(ItemStack stack) {
		return SealFilters.matches(filter, blacklist, stack, match());
	}

	@javax.annotation.Nullable
	private static net.minecraft.core.HolderLookup.Provider registries() {
		var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
		return server != null ? server.registryAccess() : null;
	}

	@Override
	public void readCustomNBT(CompoundTag nbt) {
		var provider = registries();
		for (int i = 0; i < filter.size(); i++) {
			String key = "filter" + i;
			ItemStack stack = ItemStack.EMPTY;
			if (nbt.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND) && provider != null) {
				stack = ItemStack.parseOptional(provider, nbt.getCompound(key));
			} else if (nbt.contains(key, net.minecraft.nbt.Tag.TAG_STRING)) {
				ResourceLocation id = ResourceLocation.tryParse(nbt.getString(key));
				if (id != null && BuiltInRegistries.ITEM.containsKey(id)) stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
			}
			filter.set(i, stack);
			filterSize.set(i, nbt.getInt("filterSize" + i));
		}
		blacklist = nbt.getBoolean("bl");
	}

	@Override
	public void writeCustomNBT(CompoundTag nbt) {
		var provider = registries();
		for (int i = 0; i < filter.size(); i++) {
			ItemStack stack = filter.get(i);
			if (!stack.isEmpty()) {
				if (provider != null) {
					nbt.put("filter" + i, stack.save(provider));
				} else {
					nbt.putString("filter" + i, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
				}
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
		return filter;
	}

	@Override
	public NonNullList<Integer> getSizes() {
		return filterSize;
	}

	@Override
	public ItemStack getFilterSlot(int i) {
		return filter.get(i);
	}

	@Override
	public int getFilterSlotSize(int i) {
		return filterSize.get(i);
	}

	@Override
	public void setFilterSlot(int i, ItemStack stack) {
		filter.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
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

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_FILTER };
	}
}

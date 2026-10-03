package thaumcraft.common.golems.seals;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The original's seal item matching ({@code ThaumcraftInvHelper.InvFilter} and the filter helpers of
 * {@code InventoryUtils}): every filtered seal's "match damage", "match NBT", "use ore dictionary" and "match mod"
 * toggles. Today damage is a data component and the ore dictionary's names are item tags, so "NBT" means every other
 * component, and "ore" means sharing any item tag.
 */
public final class SealFilters {

	/** The original's InvFilter: what to ignore and what to widen. */
	public record Match(boolean ignoreDamage, boolean ignoreNbt, boolean useOre, boolean useMod) {
		public static final Match STRICT = new Match(false, false, false, false);

		/** The seals' usual four toggles: match damage, match NBT, ore, mod. */
		public static Match fromToggles(boolean meta, boolean nbt, boolean ore, boolean mod) {
			return new Match(!meta, !nbt, ore, mod);
		}
	}

	private SealFilters() {
	}

	/** The original's areItemStacksEqual. */
	public static boolean areEqual(ItemStack a, ItemStack b, Match m) {
		if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
		if (m.useMod()) {
			return BuiltInRegistries.ITEM.getKey(a.getItem()).getNamespace().equals(BuiltInRegistries.ITEM.getKey(b.getItem()).getNamespace());
		}
		if (m.useOre() && a.getTags().anyMatch(b::is)) {
			return true;
		}
		boolean nbt = m.ignoreNbt() || withoutDamage(a.getComponentsPatch()).equals(withoutDamage(b.getComponentsPatch()));
		boolean damage = m.ignoreDamage() || a.getDamageValue() == b.getDamageValue();
		return a.getItem() == b.getItem() && damage && nbt;
	}

	private static DataComponentPatch withoutDamage(DataComponentPatch patch) {
		return patch.forget(type -> type == DataComponents.DAMAGE);
	}

	/** The original's matchesFilters: whether {@code stack} passes the filter (a blacklist passes everything not
	 * listed, an empty blacklist everything). */
	public static boolean matches(NonNullList<ItemStack> filter, boolean blacklist, ItemStack stack, Match m) {
		if (stack.isEmpty()) return false;
		boolean allow = false;
		boolean allEmpty = true;
		for (ItemStack fs : filter) {
			if (fs.isEmpty()) continue;
			allEmpty = false;
			boolean r = areEqual(fs, stack, m);
			if (blacklist) {
				if (r) return false;
				allow = true;
			} else if (r) {
				return true;
			}
		}
		return blacklist && (allow || allEmpty);
	}

	/** The original's findFirstMatchFromFilterTuple: the first of {@code stacks} that passes, and the size limit of the
	 * filter slot it matched (0 for a blacklist). Empty when none does. */
	public static Hit findFirst(NonNullList<ItemStack> filter, List<Integer> sizes, boolean blacklist, List<ItemStack> stacks, Match m) {
		outer:
		for (ItemStack is : stacks) {
			if (is.isEmpty()) continue;
			boolean allow = false;
			boolean allEmpty = true;
			for (int idx = 0; idx < filter.size(); idx++) {
				ItemStack fs = filter.get(idx);
				if (fs.isEmpty()) continue;
				allEmpty = false;
				boolean r = areEqual(fs, is, m);
				if (blacklist) {
					if (r) continue outer;
					allow = true;
				} else if (r) {
					return new Hit(is, idx < sizes.size() ? sizes.get(idx) : 0);
				}
			}
			if (blacklist && (allow || allEmpty)) return new Hit(is, 0);
		}
		return new Hit(ItemStack.EMPTY, 0);
	}

	public record Hit(ItemStack stack, int limit) {
	}

	/** The original's inventory findFirstMatchFromFilter: the first stack in {@code inv} that passes (with
	 * {@code leaveOne}, only ones the inventory holds two or more of). */
	public static ItemStack findFirstIn(NonNullList<ItemStack> filter, boolean blacklist, IItemHandler inv, Match m, boolean leaveOne) {
		for (int a = 0; a < inv.getSlots(); a++) {
			ItemStack is = inv.getStackInSlot(a);
			if (is.isEmpty()) continue;
			if (leaveOne && countIn(inv, is, m) < 2) continue;
			if (matches(filter, blacklist, is, m)) return is;
		}
		return ItemStack.EMPTY;
	}

	/** The original's countTotalItemsIn. */
	public static int countIn(IItemHandler inv, ItemStack stack, Match m) {
		int count = 0;
		if (inv == null) return 0;
		for (int a = 0; a < inv.getSlots(); a++) {
			if (areEqual(stack, inv.getStackInSlot(a), m)) count += inv.getStackInSlot(a).getCount();
		}
		return count;
	}

	/** The original's countStackInWorld: matching items lying within {@code range} of {@code pos}. (The original's
	 * own test counted only empty stacks, so it always came to nothing.) */
	public static int countInWorld(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, ItemStack stack, double range, Match m) {
		int count = 0;
		var box = new net.minecraft.world.phys.AABB(pos).inflate(range);
		for (var ei : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
			if (ei.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= range * range && areEqual(stack, ei.getItem(), m)) {
				count += ei.getItem().getCount();
			}
		}
		return count;
	}

	/** The original's removeStackFrom: takes up to {@code stack}'s count of matching items; what came out (the
	 * requested kind at the removed count). */
	public static ItemStack removeFrom(IItemHandler inv, ItemStack stack, Match m, boolean simulate) {
		int amount = stack.getCount();
		int removed = 0;
		if (inv != null) {
			for (int a = 0; a < inv.getSlots(); a++) {
				if (areEqual(stack, inv.getStackInSlot(a), m)) {
					int s = Math.min(amount - removed, inv.getStackInSlot(a).getCount());
					ItemStack es = inv.extractItem(a, s, simulate);
					if (!es.isEmpty()) removed += es.getCount();
				}
				if (removed >= amount) break;
			}
		}
		if (removed == 0) return ItemStack.EMPTY;
		return stack.copyWithCount(removed);
	}
}

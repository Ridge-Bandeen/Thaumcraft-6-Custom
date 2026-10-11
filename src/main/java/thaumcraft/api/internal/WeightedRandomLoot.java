package thaumcraft.api.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.item.ItemStack;

/** One weighted entry in a loot bag's pool (common, uncommon, rare) - filled through {@code ThaumcraftApi#addLootBagItem}. */
public class WeightedRandomLoot extends WeightedEntry.IntrusiveBase {

	/** What this entry gives. */
	public final ItemStack item;

	public WeightedRandomLoot(ItemStack stack, int weight) {
		super(weight);
		item = stack;
	}

	public static final List<WeightedRandomLoot> lootBagCommon = new CopyOnWriteArrayList<>();
	public static final List<WeightedRandomLoot> lootBagUncommon = new CopyOnWriteArrayList<>();
	public static final List<WeightedRandomLoot> lootBagRare = new CopyOnWriteArrayList<>();

	/** Gear ladders replacing the built-in ones, by slot (0 weapon, 1-4 boots to helmet); null keeps the built-in one.
	 * Set through {@code ThaumcraftApi#setLootBagGear}. */
	public static final net.minecraft.world.item.Item[][] lootBagGear = new net.minecraft.world.item.Item[5][];
}

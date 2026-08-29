package thaumcraft.api.items;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.blocks.BlocksTC;

/**
 * See {@link BlocksTC} for why this only has a couple of fields so far instead of the ~150 the original
 * had - built out incrementally, one vertical slice at a time.
 */
public final class ItemsTC {

	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("thaumcraft");

	public static final DeferredItem<BlockItem> arcaneWorkbench =
			ITEMS.registerSimpleBlockItem("arcane_workbench", BlocksTC.arcaneWorkbench);

	/** Not yet wired up to the real scanning/research system - see the port-tracking memory notes. */
	public static final DeferredItem<Item> thaumometer =
			ITEMS.registerSimpleItem("thaumometer", new Item.Properties().stacksTo(1));

	private ItemsTC() {
	}
}

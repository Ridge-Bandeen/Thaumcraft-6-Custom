package thaumcraft.common.golems.seals;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISealConfigToggles;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;

/**
 * Replaces the old (1.12.2) {@code SealStock} - a passive requester, not a task source: as the original, every 20
 * ticks it counts each filter slot's item in the container it is on (matched with its toggles: damage, NBT, ore,
 * mod) and, where there are fewer than the slot's size, asks for the difference (as much of it as the container has
 * room for) through {@code GolemHelper.requestProvisioning}; {@link SealProvide} answers. Always a whitelist.
 */
public class SealStock extends SealFiltered implements ISealConfigToggles {

	private int delay = new Random().nextInt(50);
	protected final SealToggle[] props = {
			new SealToggle(true, "pmeta", "golem.prop.meta"),
			new SealToggle(true, "pnbt", "golem.prop.nbt"),
			new SealToggle(false, "pore", "golem.prop.ore"),
			new SealToggle(false, "pmod", "golem.prop.mod"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_stock");

	@Override
	protected SealFilters.Match match() {
		return SealFilters.Match.fromToggles(props[0].getValue(), props[1].getValue(), props[2].getValue(), props[3].getValue());
	}

	@Override
	public String getKey() {
		return "thaumcraft:stock";
	}

	@Override
	public int getFilterSize() {
		return 9;
	}

	@Override
	public boolean isBlacklist() {
		return false;
	}

	@Override
	public boolean hasStacksizeLimiters() {
		return true;
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay++ % 20 != 0) return;
		IItemHandler inv = world.getCapability(Capabilities.ItemHandler.BLOCK, seal.getSealPos().pos, seal.getSealPos().face);
		if (inv == null) return;
		for (int i = 0; i < getFilterSize(); i++) {
			ItemStack filterStack = getFilterSlot(i);
			if (filterStack.isEmpty()) continue;
			int amt = SealFilters.countIn(inv, filterStack, match());
			if (amt < getFilterSlotSize(i)) {
				ItemStack fs = filterStack.copyWithCount(Math.min(filterStack.getMaxStackSize(), getFilterSlotSize(i) - amt));
				// the original's hasRoomFor: as much as the container would take
				ItemStack rest = ItemHandlerHelper.insertItemStacked(inv, fs.copy(), true);
				fs.shrink(rest.getCount());
				if (!fs.isEmpty()) {
					GolemHelper.requestProvisioning(world, seal.getSealPos().pos, seal.getSealPos().face, fs);
				}
			}
		}
	}

	@Override
	public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		return false;
	}

	@Override
	public void onTaskSuspension(Level world, Task task) {
	}

	@Override
	public boolean canPlaceAt(Level world, BlockPos pos, Direction side) {
		return world.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null;
	}

	@Override
	public ResourceLocation getSealIcon() {
		return ICON;
	}

	@Override
	public void onRemoval(Level world, BlockPos pos, Direction side) {
	}

	@Override
	public EnumGolemTrait[] getRequiredTags() {
		return null;
	}

	@Override
	public EnumGolemTrait[] getForbiddenTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.CLUMSY };
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_FILTER, CAT_TOGGLES, CAT_PRIORITY, CAT_TAGS };
	}

	@Override
	public SealToggle[] getToggles() {
		return props;
	}

	@Override
	public void setToggle(int index, boolean value) {
		props[index].setValue(value);
	}
}

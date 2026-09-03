package thaumcraft.common.golems.seals;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;

/**
 * Replaces the old (1.12.2) {@code SealStock} - a passive requester, not a task source: it never creates
 * a {@link Task} of its own (matches the original's own {@code canGolemPerformTask} always returning
 * {@code false}). Every 20 ticks it checks the real {@link IItemHandler} at its own position/face
 * (NeoForge's own block capability lookup, {@code Capabilities.ItemHandler.BLOCK} - the modern replacement
 * for the original's still-1.12.2 {@code ThaumcraftInvHelper.getItemHandlerAt}) against its 9-slot filter's
 * target counts, and calls the already-real {@code GolemHelper.requestProvisioning} for whatever's short -
 * {@link SealProvide} is the real consumer that fulfills those requests.
 *
 * Real, documented simplification: no metadata/NBT/ore-dictionary/same-mod matching toggles (moot - see
 * {@code SealFiltered}'s own doc comment, real item-identity-only filtering). Always whitelist mode (matches
 * the original's own hardcoded {@code isBlacklist() -> false} override - a stock request list only ever
 * makes sense as "these specific items, this many of each").
 */
public class SealStock extends SealFiltered {

	private int delay = new Random().nextInt(50);
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_stock");

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
			int want = getFilterSlotSize(i);
			if (filterStack.isEmpty() || want <= 0) continue;
			int have = countMatching(inv, filterStack.getItem());
			if (have < want) {
				ItemStack request = filterStack.copy();
				request.setCount(Math.min(filterStack.getMaxStackSize(), want - have));
				GolemHelper.requestProvisioning(world, seal.getSealPos().pos, seal.getSealPos().face, request);
			}
		}
	}

	static int countMatching(IItemHandler inv, Item item) {
		int total = 0;
		for (int i = 0; i < inv.getSlots(); i++) {
			ItemStack stack = inv.getStackInSlot(i);
			if (stack.getItem() == item) total += stack.getCount();
		}
		return total;
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
		return new int[] { CAT_FILTER, CAT_TAGS };
	}
}

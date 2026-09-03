package thaumcraft.common.golems.seals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.tasks.Task;

/**
 * Replaces the old (1.12.2) {@code SealEmptyAdvanced} - the one Advanced variant that implements genuinely
 * new behavior rather than just exposing an already-real base toggle: real round-robin cycling through the
 * filter's own matching items (gated on the base {@link SealEmpty}'s own real-but-inert {@code pcycle}
 * toggle, whitelist mode only, matching the original's own {@code getToggles()[4].value && !isBlacklist()}
 * gate exactly), so a golem doesn't fixate on always emptying whichever filtered item happens to sort first.
 * {@link #cycleIndex} advances once per completed task, same as the original's own {@code filterInc}.
 */
public class SealEmptyAdvanced extends SealEmpty {

	private int cycleIndex;
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_empty_advanced");

	@Override
	public String getKey() {
		return "thaumcraft:empty_advanced";
	}

	@Override
	public int getFilterSize() {
		return 9;
	}

	@Override
	public ResourceLocation getSealIcon() {
		return ICON;
	}

	@Override
	protected ItemStack findFirstMatching(IItemHandler inv) {
		if (props[1].getValue() && !isBlacklist()) {
			List<ItemStack> real = new ArrayList<>();
			for (ItemStack s : getInv()) {
				if (!s.isEmpty()) real.add(s);
			}
			if (!real.isEmpty()) {
				ItemStack want = real.get(Math.floorMod(cycleIndex, real.size()));
				for (int i = 0; i < inv.getSlots(); i++) {
					ItemStack stack = inv.getStackInSlot(i);
					if (!stack.isEmpty() && stack.getItem() == want.getItem()) return stack.copy();
				}
				return ItemStack.EMPTY;
			}
		}
		return super.findFirstMatching(inv);
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		boolean result = super.onTaskCompletion(world, golem, task);
		cycleIndex++;
		return result;
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_FILTER, CAT_TOGGLES, CAT_PRIORITY, CAT_TAGS };
	}

	@Override
	public EnumGolemTrait[] getRequiredTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.SMART };
	}
}

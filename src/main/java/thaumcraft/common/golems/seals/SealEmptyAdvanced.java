package thaumcraft.common.golems.seals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.golems.EnumGolemTrait;

/**
 * Replaces the old (1.12.2) {@code SealEmptyAdvanced}: a 9-slot filter, the toggles shown, a smart golem - and with
 * "cycle" on a whitelist, it takes one whitelisted kind at a time in turn instead of whatever comes first.
 */
public class SealEmptyAdvanced extends SealEmpty {

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
	protected net.minecraft.core.NonNullList<ItemStack> getInv(int c) {
		if (props[4].getValue() && !isBlacklist()) {
			List<ItemStack> w = new ArrayList<>();
			for (ItemStack s : getInv()) {
				if (!s.isEmpty()) w.add(s);
			}
			if (!w.isEmpty()) return net.minecraft.core.NonNullList.withSize(1, w.get(Math.abs(c % w.size())));
		}
		return getInv();
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

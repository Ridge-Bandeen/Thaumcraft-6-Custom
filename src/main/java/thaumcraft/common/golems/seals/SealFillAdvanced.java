package thaumcraft.common.golems.seals;

import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.golems.EnumGolemTrait;

/**
 * Replaces the old (1.12.2) {@code SealFillAdvanced}. {@link SealFill} already has a real {@code pexist}
 * toggle (only ever reachable via NBT/creative tools at that tier) - this Advanced tier is what exposes it
 * in the real config GUI, same "Advanced unlocks the base's own real toggles" relationship
 * {@link SealGuardAdvanced} already established. Real, bigger (9-slot) filter and an added {@code SMART}
 * trait requirement.
 */
public class SealFillAdvanced extends SealFill {

	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_fill_advanced");

	@Override
	public String getKey() {
		return "thaumcraft:fill_advanced";
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
	public int[] getGuiCategories() {
		return new int[] { CAT_FILTER, CAT_TOGGLES, CAT_PRIORITY, CAT_TAGS };
	}

	@Override
	public EnumGolemTrait[] getRequiredTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.SMART };
	}
}

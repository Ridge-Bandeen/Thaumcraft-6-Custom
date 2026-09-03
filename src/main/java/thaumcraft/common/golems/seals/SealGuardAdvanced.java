package thaumcraft.common.golems.seals;

import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.golems.EnumGolemTrait;

/**
 * Replaces the old (1.12.2) {@code SealGuardAdvanced}. {@link SealGuard} already has real, working
 * {@code pmob}/{@code panimal}/{@code pplayer} toggles (only ever reachable via NBT/creative tools at that
 * tier, since {@code SealGuard#getGuiCategories} doesn't expose {@code CAT_TOGGLES}) - this "Advanced" tier
 * adds nothing functionally new, it's what unlocks the SAME inherited toggles in the real config GUI,
 * requiring a smarter golem ({@code SMART}, on top of the base's own {@code FIGHTER}) to justify it - a real
 * translation of the original's own identical relationship between the two classes.
 */
public class SealGuardAdvanced extends SealGuard {

	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_guard_advanced");

	@Override
	public String getKey() {
		return "thaumcraft:guard_advanced";
	}

	@Override
	public ResourceLocation getSealIcon() {
		return ICON;
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_AREA, CAT_TOGGLES, CAT_PRIORITY, CAT_TAGS };
	}

	@Override
	public EnumGolemTrait[] getRequiredTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.FIGHTER, EnumGolemTrait.SMART };
	}
}

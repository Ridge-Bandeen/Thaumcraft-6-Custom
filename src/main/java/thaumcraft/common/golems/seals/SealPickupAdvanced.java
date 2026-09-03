package thaumcraft.common.golems.seals;

import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.golems.EnumGolemTrait;

/**
 * Replaces the old (1.12.2) {@code SealPickupAdvanced}. The original's own toggles here (confirmed by
 * reading the still-1.12.2 {@code SealPickup} the original {@code SealPickupAdvanced} extended, via
 * {@code git show master:...}) were exactly the same meta/NBT/ore/mod matching set every {@code SealFiltered}
 * subclass in this port has already dropped as superseded - nothing real survives to expose here, so unlike
 * {@link SealGuardAdvanced}/{@link SealBreakerAdvanced}/{@link SealFillAdvanced}/{@link SealEmptyAdvanced}
 * this one has no {@code CAT_TOGGLES} panel at all (a real, deliberate deviation from the original's own
 * category list, which included one for those since-dropped toggles). Real, bigger (9-slot) filter and an
 * added {@code SMART} trait requirement, same "Advanced tier needs a smarter golem" pattern the others use.
 */
public class SealPickupAdvanced extends SealPickup {

	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_pickup_advanced");

	@Override
	public String getKey() {
		return "thaumcraft:pickup_advanced";
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
	public EnumGolemTrait[] getRequiredTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.SMART };
	}
}

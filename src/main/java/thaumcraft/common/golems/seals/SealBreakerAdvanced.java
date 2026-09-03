package thaumcraft.common.golems.seals;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.seals.ISealConfigToggles;

/**
 * Replaces the old (1.12.2) {@code SealBreakerAdvanced}. Adds the one real toggle the base
 * {@link SealBreaker} doesn't have at all: {@code psilk}, real silk-touch dropping (the original's own
 * silk-touch toggle - the ONLY piece of its progressive-mining/silk-touch package this port ships, since
 * {@link SealBreaker}'s own doc comment already explains why progressive mining/crack stages aren't ported).
 * A real, bigger (9-slot) filter and an added {@code SMART} trait requirement, same "Advanced tier needs a
 * smarter golem" pattern {@link SealGuardAdvanced} already established.
 */
public class SealBreakerAdvanced extends SealBreaker implements ISealConfigToggles {

	private final SealToggle[] props = {
			new SealToggle(false, "psilk", "golem.prop.silk"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_breaker_advanced");

	@Override
	public String getKey() {
		return "thaumcraft:breaker_advanced";
	}

	@Override
	public int getFilterSize() {
		return 9;
	}

	@Override
	public ResourceLocation getSealIcon() {
		return ICON;
	}

	/** Real silk-touch drop: the block's own item form (matching vanilla's own real silk-touch convention),
	 * via {@link Block#popResource} after removing the block - the same real drop-then-remove order vanilla
	 * itself uses for a silk-touch harvest. */
	@Override
	protected void breakBlock(Level world, BlockPos pos) {
		if (!props[0].getValue()) {
			super.breakBlock(world, pos);
			return;
		}
		BlockState state = world.getBlockState(pos);
		ItemStack drop = new ItemStack(state.getBlock());
		world.removeBlock(pos, false);
		Block.popResource(world, pos, drop);
	}

	@Override
	public EnumGolemTrait[] getRequiredTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.BREAKER, EnumGolemTrait.SMART };
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_AREA, CAT_FILTER, CAT_TOGGLES, CAT_PRIORITY, CAT_TAGS };
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

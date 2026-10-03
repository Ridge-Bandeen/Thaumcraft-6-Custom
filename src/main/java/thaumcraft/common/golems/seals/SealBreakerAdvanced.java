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
 * {@link SealBreaker} doesn't have: {@code psilk}, silk-touch breaking (slower going, see {@link SealBreaker}).
 * A real, bigger (9-slot) filter and an added {@code SMART} trait requirement, same "Advanced tier needs a
 * smarter golem" pattern {@link SealGuardAdvanced} already established.
 */
public class SealBreakerAdvanced extends SealBreaker {

	{
		props = new SealToggle[] { new SealToggle(true, "pmeta", "golem.prop.meta"), new SealToggle(false, "psilk", "golem.prop.silk") };
	}
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

	@Override
	protected boolean isSilky() {
		return props[1].getValue();
	}

	/** Silky: the block drops what a silk-touch tool would get from it (its loot table), then goes. */
	@Override
	protected void breakBlock(Level world, BlockPos pos) {
		if (!props[1].getValue() || !(world instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
			super.breakBlock(world, pos);
			return;
		}
		BlockState state = world.getBlockState(pos);
		ItemStack tool = new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE);
		serverLevel.registryAccess().lookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.flatMap(r -> r.get(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH))
				.ifPresent(silk -> tool.enchant(silk, 1));
		Block.dropResources(state, world, pos, world.getBlockEntity(pos), null, tool);
		world.removeBlock(pos, false);
	}

	@Override
	public EnumGolemTrait[] getRequiredTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.BREAKER, EnumGolemTrait.SMART };
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_AREA, CAT_FILTER, CAT_TOGGLES, CAT_PRIORITY, CAT_TAGS };
	}


}

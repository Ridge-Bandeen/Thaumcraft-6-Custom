package thaumcraft.common.items.consumables;

import net.minecraft.world.item.Item;

/**
 * Replaces the old (1.12.2) {@code ItemBathSalts}: the Arcane Spa's ingredient, and, as the original, a dropped one only
 * lasts 10 seconds - expiring in still water, it turns that water into Purifying Fluid
 * ({@code EntityEvents#itemExpire} in the original; {@link #onExpire} here).
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = thaumcraft.Thaumcraft.MODID)
public class ItemBathSalts extends Item {

	public ItemBathSalts(Properties properties) {
		super(properties);
	}

	@Override
	public int getEntityLifespan(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level) {
		return 200;
	}

	/** Dropped salts expiring in a still water source turn it into Purifying Fluid. */
	@net.neoforged.bus.api.SubscribeEvent
	public static void onExpire(net.neoforged.neoforge.event.entity.item.ItemExpireEvent event) {
		var item = event.getEntity();
		if (!(item.getItem().getItem() instanceof ItemBathSalts) || item.level().isClientSide) return;
		net.minecraft.core.BlockPos bp = item.blockPosition();
		var state = item.level().getBlockState(bp);
		if (state.is(net.minecraft.world.level.block.Blocks.WATER) && state.getFluidState().isSource()) {
			item.level().setBlockAndUpdate(bp, thaumcraft.api.blocks.BlocksTC.purifyingFluid.get().defaultBlockState());
		}
	}
}

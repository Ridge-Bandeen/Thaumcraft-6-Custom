package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;
import thaumcraft.common.tiles.crafting.TileCrucible;
import thaumcraft.common.tiles.devices.TileRechargePedestal;

public final class ThaumcraftBlockEntities {

	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "thaumcraft");

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileArcaneWorkbench>> ARCANE_WORKBENCH = BLOCK_ENTITIES.register("arcane_workbench",
			() -> BlockEntityType.Builder.of(TileArcaneWorkbench::new, BlocksTC.arcaneWorkbench.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileCrucible>> CRUCIBLE = BLOCK_ENTITIES.register("crucible",
			() -> BlockEntityType.Builder.of(TileCrucible::new, BlocksTC.crucible.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileRechargePedestal>> RECHARGE_PEDESTAL = BLOCK_ENTITIES.register("recharge_pedestal",
			() -> BlockEntityType.Builder.of(TileRechargePedestal::new, BlocksTC.rechargePedestal.get()).build(null));

	private ThaumcraftBlockEntities() {
	}
}

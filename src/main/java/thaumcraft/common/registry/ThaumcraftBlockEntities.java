package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;
import thaumcraft.common.tiles.crafting.TileCrucible;
import thaumcraft.common.tiles.devices.TileRechargePedestal;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public final class ThaumcraftBlockEntities {

	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "thaumcraft");

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileArcaneWorkbench>> ARCANE_WORKBENCH = BLOCK_ENTITIES.register("arcane_workbench",
			() -> BlockEntityType.Builder.of(TileArcaneWorkbench::new, BlocksTC.arcaneWorkbench.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileCrucible>> CRUCIBLE = BLOCK_ENTITIES.register("crucible",
			() -> BlockEntityType.Builder.of(TileCrucible::new, BlocksTC.crucible.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileRechargePedestal>> RECHARGE_PEDESTAL = BLOCK_ENTITIES.register("recharge_pedestal",
			() -> BlockEntityType.Builder.of(TileRechargePedestal::new, BlocksTC.rechargePedestal.get()).build(null));

	/** See {@link thaumcraft.common.tiles.devices.TileHungryChest}'s own doc comment for why a thin
	 * subclass of vanilla's {@link ChestBlockEntity} is needed here after all, despite adding no real
	 * behavior of its own. */
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.devices.TileHungryChest>> HUNGRY_CHEST = BLOCK_ENTITIES.register("hungry_chest",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.devices.TileHungryChest::new, BlocksTC.hungryChest.get()).build(null));

	private ThaumcraftBlockEntities() {
	}
}

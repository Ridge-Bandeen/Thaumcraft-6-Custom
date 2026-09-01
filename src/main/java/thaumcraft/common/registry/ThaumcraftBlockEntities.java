package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;
import thaumcraft.common.tiles.crafting.TileCrucible;
import thaumcraft.common.tiles.devices.TileRechargePedestal;
import thaumcraft.common.tiles.devices.TileLampArcane;
import thaumcraft.common.tiles.devices.TileVisGenerator;
import thaumcraft.common.tiles.devices.TileWaterJug;
import thaumcraft.common.tiles.devices.TileArcaneEar;
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

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileLampArcane>> LAMP_ARCANE = BLOCK_ENTITIES.register("lamp_arcane",
			() -> BlockEntityType.Builder.of(TileLampArcane::new, BlocksTC.lampArcane.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileVisGenerator>> VIS_GENERATOR = BLOCK_ENTITIES.register("vis_generator",
			() -> BlockEntityType.Builder.of(TileVisGenerator::new, BlocksTC.visGenerator.get()).build(null));

	/** Registry id kept as "everfull_urn" (not "water_jug") to match the original's own in-game name and
	 * the existing, untouched 1.12.2 texture/model/blockstate assets. */
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileWaterJug>> WATER_JUG = BLOCK_ENTITIES.register("everfull_urn",
			() -> BlockEntityType.Builder.of(TileWaterJug::new, BlocksTC.everfullUrn.get()).build(null));

	/** Shared by both {@code arcane_ear} and {@code arcane_ear_toggle} - {@link BlockEntityType.Builder#of}
	 * takes a varargs list of valid blocks for exactly this "same tile, two block variants" case. */
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileArcaneEar>> ARCANE_EAR = BLOCK_ENTITIES.register("arcane_ear",
			() -> BlockEntityType.Builder.of(TileArcaneEar::new, BlocksTC.arcaneEar.get(), BlocksTC.arcaneEarToggle.get()).build(null));

	private ThaumcraftBlockEntities() {
	}
}

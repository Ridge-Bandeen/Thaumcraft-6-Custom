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
import thaumcraft.common.tiles.devices.TileLampGrowth;
import thaumcraft.common.tiles.devices.TileLampFertility;
import thaumcraft.common.tiles.essentia.TileJarFillable;
import thaumcraft.common.tiles.essentia.TileTube;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public final class ThaumcraftBlockEntities {

	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "thaumcraft");

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileArcaneWorkbench>> ARCANE_WORKBENCH = BLOCK_ENTITIES.register("arcane_workbench",
			() -> BlockEntityType.Builder.of(TileArcaneWorkbench::new, BlocksTC.arcaneWorkbench.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileCrucible>> CRUCIBLE = BLOCK_ENTITIES.register("crucible",
			() -> BlockEntityType.Builder.of(TileCrucible::new, BlocksTC.crucible.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.crafting.TileFocalManipulator>> FOCAL_MANIPULATOR = BLOCK_ENTITIES.register("focal_manipulator",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.crafting.TileFocalManipulator::new, BlocksTC.focalManipulator.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.crafting.TileResearchTable>> RESEARCH_TABLE = BLOCK_ENTITIES.register("research_table",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.crafting.TileResearchTable::new, BlocksTC.researchTable.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.crafting.TileGolemBuilder>> GOLEM_BUILDER = BLOCK_ENTITIES.register("golem_builder",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.crafting.TileGolemBuilder::new, BlocksTC.golemBuilder.get()).build(null));

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

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.devices.TileLevitator>> LEVITATOR = BLOCK_ENTITIES.register("levitator",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.devices.TileLevitator::new, BlocksTC.levitator.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.misc.TileHole>> HOLE = BLOCK_ENTITIES.register("hole",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.misc.TileHole::new, BlocksTC.hole.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.devices.TileInfernalFurnace>> INFERNAL_FURNACE = BLOCK_ENTITIES.register("infernal_furnace",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.devices.TileInfernalFurnace::new, BlocksTC.infernalFurnace.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.essentia.TileCentrifuge>> CENTRIFUGE = BLOCK_ENTITIES.register("centrifuge",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.essentia.TileCentrifuge::new, BlocksTC.centrifuge.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileJarFillable>> JAR = BLOCK_ENTITIES.register("jar_normal",
			() -> BlockEntityType.Builder.of(TileJarFillable::new, BlocksTC.jarNormal.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.essentia.TileJarFillableVoid>> JAR_VOID = BLOCK_ENTITIES.register("jar_void",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.essentia.TileJarFillableVoid::new, BlocksTC.jarVoid.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileLampGrowth>> LAMP_GROWTH = BLOCK_ENTITIES.register("lamp_growth",
			() -> BlockEntityType.Builder.of(TileLampGrowth::new, BlocksTC.lampGrowth.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileLampFertility>> LAMP_FERTILITY = BLOCK_ENTITIES.register("lamp_fertility",
			() -> BlockEntityType.Builder.of(TileLampFertility::new, BlocksTC.lampFertility.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileTube>> TUBE = BLOCK_ENTITIES.register("tube",
			() -> BlockEntityType.Builder.of(TileTube::new, BlocksTC.tube.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.essentia.TileTubeFilter>> TUBE_FILTER = BLOCK_ENTITIES.register("tube_filter",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.essentia.TileTubeFilter::new, BlocksTC.tubeFilter.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.essentia.TileTubeRestrict>> TUBE_RESTRICT = BLOCK_ENTITIES.register("tube_restrict",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.essentia.TileTubeRestrict::new, BlocksTC.tubeRestrict.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.essentia.TileTubeValve>> TUBE_VALVE = BLOCK_ENTITIES.register("tube_valve",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.essentia.TileTubeValve::new, BlocksTC.tubeValve.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.devices.TileDioptra>> DIOPTRA = BLOCK_ENTITIES.register("dioptra",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.devices.TileDioptra::new, BlocksTC.dioptra.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.devices.TileCondenser>> CONDENSER = BLOCK_ENTITIES.register("condenser",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.devices.TileCondenser::new, BlocksTC.condenser.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.devices.TilePotionSprayer>> POTION_SPRAYER = BLOCK_ENTITIES.register("potion_sprayer",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.devices.TilePotionSprayer::new, BlocksTC.potionSprayer.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.crafting.TileVoidSiphon>> VOID_SIPHON = BLOCK_ENTITIES.register("void_siphon",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.crafting.TileVoidSiphon::new, BlocksTC.voidSiphon.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.crafting.TileThaumatorium>> THAUMATORIUM = BLOCK_ENTITIES.register("thaumatorium",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.crafting.TileThaumatorium::new, BlocksTC.thaumatorium.get()).build(null));

	/** Also backs {@link BlocksTC#pedestalEldritch} - see that field's own doc comment. Same tile, same
	 * behavior, purely a themed reskin (matches the original's own "Eldritch" pedestal tier). */
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.crafting.TilePedestal>> PEDESTAL = BLOCK_ENTITIES.register("pedestal",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.crafting.TilePedestal::new, BlocksTC.pedestal.get(), BlocksTC.pedestalEldritch.get()).build(null));

	/** See {@link thaumcraft.common.tiles.misc.TileBanner}'s own doc comment. */
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.misc.TileBanner>> BANNER = BLOCK_ENTITIES.register("banner",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.misc.TileBanner::new, BlocksTC.bannerCrimsonCult.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.crafting.TileInfusionMatrix>> INFUSION_MATRIX = BLOCK_ENTITIES.register("infusion_matrix",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.crafting.TileInfusionMatrix::new, BlocksTC.infusionMatrix.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.essentia.TileAlembic>> ALEMBIC = BLOCK_ENTITIES.register("alembic",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.essentia.TileAlembic::new, BlocksTC.alembic.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.essentia.TileSmelter>> SMELTER = BLOCK_ENTITIES.register("smelter",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.essentia.TileSmelter::new, BlocksTC.smelter.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.devices.TileBellows>> BELLOWS = BLOCK_ENTITIES.register("bellows",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.devices.TileBellows::new, BlocksTC.bellows.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.devices.TileMirror>> MIRROR = BLOCK_ENTITIES.register("mirror",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.devices.TileMirror::new, BlocksTC.mirror.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.misc.TileBarrierStone>> BARRIER_STONE = BLOCK_ENTITIES.register("barrier_stone",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.misc.TileBarrierStone::new, BlocksTC.pavingStoneBarrier.get()).build(null));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<thaumcraft.common.tiles.devices.TileSpa>> SPA = BLOCK_ENTITIES.register("spa",
			() -> BlockEntityType.Builder.of(thaumcraft.common.tiles.devices.TileSpa::new, BlocksTC.spa.get()).build(null));

	private ThaumcraftBlockEntities() {
	}
}

package thaumcraft.api.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.common.blocks.crafting.BlockArcaneWorkbench;
import thaumcraft.common.blocks.crafting.BlockCrucible;
import thaumcraft.common.registry.ThaumcraftTreeGrowers;

/**
 * Registered block references, addon-facing the same way the old (1.12.2) {@code BlocksTC} was - fields
 * are assigned via registration rather than left null, only now via a real {@link DeferredRegister} instead
 * of Registrar assigning static fields after the fact.
 *
 * This is being built out incrementally, one vertical slice at a time, rather than declaring all ~350
 * fields from the original up front with nothing behind most of them - see the port-tracking memory notes
 * for why. Add a field here only once the block it names is actually registered below.
 */
public final class BlocksTC {

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("thaumcraft");

	public static final DeferredBlock<BlockArcaneWorkbench> arcaneWorkbench = BLOCKS.registerBlock("arcane_workbench",
			BlockArcaneWorkbench::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));

	public static final DeferredBlock<BlockCrucible> crucible = BLOCKS.registerBlock("crucible",
			BlockCrucible::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).sound(SoundType.METAL).noOcclusion());

	/**
	 * First step into world generation - the original's overworld quartz ore variant. No custom Block
	 * subclass needed (unlike {@code BlockOreTC}, which only special-cased amber's bonus curio drop and
	 * variable quantities - neither of which apply to quartz); tool requirement (stone pickaxe) and drops
	 * (vanilla quartz, fortune-scaled, self-dropping on silk touch) are both handled by data
	 * (tags/loot table), matching this project's "full data-driven" preference. See
	 * {@code data/thaumcraft/worldgen/**} and {@code data/thaumcraft/neoforge/biome_modifier/ore_quartz.json}
	 * for the generation side.
	 */
	public static final DeferredBlock<Block> oreQuartz = BLOCKS.registerBlock("ore_quartz",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f).sound(SoundType.STONE).requiresCorrectToolForDrops());

	/** Drops itself (smelts into quicksilver - see {@code data/thaumcraft/recipe/quicksilver_smelting.json}),
	 * unlike quartz ore which drops a separate item - so its loot table is a plain self-drop, no fortune
	 * scaling, matching the original {@code BlockOreTC.quantityDroppedWithBonus}'s rule that fortune only
	 * applies when the dropped item differs from the block itself. */
	public static final DeferredBlock<Block> oreCinnabar = BLOCKS.registerBlock("ore_cinnabar",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f).sound(SoundType.STONE).requiresCorrectToolForDrops());

	/** Third ore. The original ({@code BlockOreTC}) special-cased amber alone with a variable 1-2 quantity
	 * and a rare (6.6%) bonus {@code ItemCurio} drop replacing one of the amber items - both reproduced in
	 * {@code data/thaumcraft/loot_table/blocks/ore_amber.json} rather than in a custom Block subclass,
	 * consistent with this project's data-driven-drops preference. Harvest level (stone pickaxe) matches
	 * the original's {@code setHarvestLevel("pickaxe", 1)}. */
	public static final DeferredBlock<Block> oreAmber = BLOCKS.registerBlock("ore_amber",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.STONE).requiresCorrectToolForDrops());

	/** Decorative amber storage blocks (compressed from the raw gem, and its brick variant). The original
	 * {@code BlockTranslucent} (glass material, translucent render layer, valid beacon base, culls faces
	 * against itself) maps directly onto vanilla's own {@link TransparentBlock} - the same base class
	 * vanilla's stained glass uses - rather than needing a custom subclass. */
	public static final DeferredBlock<TransparentBlock> amberBlock = BLOCKS.registerBlock("amber_block",
			TransparentBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.GLASS).noOcclusion());

	public static final DeferredBlock<TransparentBlock> amberBrick = BLOCKS.registerBlock("amber_brick",
			TransparentBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.GLASS).noOcclusion());

	/** Decorative crafted from stone + Crystal Essence ({@code data/thaumcraft/recipe/stone_arcane.json}) -
	 * used as the base material for several not-yet-ported structures/devices (stairs, slabs, the Wand
	 * Workbench), ported alone for now. No custom Block subclass needed - the original ({@code BlockStoneTC})
	 * only special-cased "is a valid beacon base", reproduced with the {@code minecraft:beacon_base_blocks}
	 * tag instead (see {@code data/minecraft/tags/block/beacon_base_blocks.json}). */
	public static final DeferredBlock<Block> stoneArcane = BLOCKS.registerBlock("stone_arcane",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 10.0f).sound(SoundType.STONE));

	/** Vanilla's own {@link StairBlock}/{@link SlabBlock} - no custom subclass needed. Note modern slabs
	 * are a single block (a {@code type} blockstate property covers bottom/top/double), unlike 1.12.2's
	 * separate half/double-slab block pair - {@code BlocksTC.slabArcaneStone}'s old "double slab" sibling
	 * isn't needed here. */
	public static final DeferredBlock<StairBlock> stoneArcaneStairs = BLOCKS.registerBlock("stone_arcane_stairs",
			props -> new StairBlock(stoneArcane.get().defaultBlockState(), props),
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 10.0f).sound(SoundType.STONE));

	public static final DeferredBlock<SlabBlock> stoneArcaneSlab = BLOCKS.registerBlock("stone_arcane_slab",
			SlabBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 10.0f).sound(SoundType.STONE));

	/** Brick-cut variant of Arcane Stone, plus its own stairs/slab pair - same shape as the plain stone's
	 * three blocks above, one texture set swapped in. */
	public static final DeferredBlock<Block> stoneArcaneBrick = BLOCKS.registerBlock("stone_arcane_brick",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 10.0f).sound(SoundType.STONE));

	public static final DeferredBlock<StairBlock> stoneArcaneBrickStairs = BLOCKS.registerBlock("stone_arcane_brick_stairs",
			props -> new StairBlock(stoneArcaneBrick.get().defaultBlockState(), props),
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 10.0f).sound(SoundType.STONE));

	public static final DeferredBlock<SlabBlock> stoneArcaneBrickSlab = BLOCKS.registerBlock("stone_arcane_brick_slab",
			SlabBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 10.0f).sound(SoundType.STONE));

	/**
	 * The Silverwood wood set - gives {@code Filter} (and everything else that needs {@code plankSilverwood})
	 * a reachable ingredient. Log/planks/leaves use vanilla base classes directly ({@link RotatedPillarBlock}/
	 * plain {@link Block}/{@link LeavesBlock} - no custom subclasses needed). The sapling is a real
	 * {@link SaplingBlock} now, using a mod-owned {@link net.minecraft.world.level.block.grower.TreeGrower}
	 * (see {@link ThaumcraftTreeGrowers}) that resolves against
	 * {@code data/thaumcraft/worldgen/configured_feature/silverwood_tree.json} at grow time - vanilla's own
	 * {@code minecraft:tree} feature type with a fancy trunk+foliage placer combo, not a reproduction of the
	 * original's hand-rolled multi-trunk clustered-log shape ({@code WorldGenSilverwoodTrees}, which has no
	 * equivalent in vanilla's modern TrunkPlacer/FoliagePlacer framework and would need a real custom
	 * TrunkPlacer to reproduce faithfully - out of scope here, same "use vanilla's own feature/placer
	 * instead of the hand-rolled original" precedent as the ore world-gen). Natural placement is a rare
	 * overworld scatter (see the placed_feature/biome_modifier next to the configured_feature) rather than
	 * the original's "only near magical nodes" restriction, since the node system isn't ported.
	 */
	public static final DeferredBlock<RotatedPillarBlock> logSilverwood = BLOCKS.registerBlock("log_silverwood",
			RotatedPillarBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 5.0f).sound(SoundType.WOOD).lightLevel(state -> 5).ignitedByLava());

	public static final DeferredBlock<Block> plankSilverwood = BLOCKS.registerBlock("plank_silverwood",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).ignitedByLava());

	public static final DeferredBlock<LeavesBlock> leavesSilverwood = BLOCKS.registerBlock("leaves_silverwood",
			LeavesBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2f).sound(SoundType.GRASS)
					.randomTicks().noOcclusion().isValidSpawn((state, level, pos, type) -> false)
					.isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
					.ignitedByLava().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

	public static final DeferredBlock<SaplingBlock> saplingSilverwood = BLOCKS.registerBlock("sapling_silverwood",
			props -> new SaplingBlock(ThaumcraftTreeGrowers.SILVERWOOD, props),
			BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak().sound(SoundType.GRASS)
					.pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

	/** Silverwood's own stairs/slab, same shape as the Arcane Stone family's - vanilla's {@link StairBlock}/
	 * {@link SlabBlock}, no custom subclasses. */
	public static final DeferredBlock<StairBlock> silverwoodStairs = BLOCKS.registerBlock("silverwood_stairs",
			props -> new StairBlock(plankSilverwood.get().defaultBlockState(), props),
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).ignitedByLava());

	public static final DeferredBlock<SlabBlock> silverwoodSlab = BLOCKS.registerBlock("silverwood_slab",
			SlabBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).ignitedByLava());

	/**
	 * Greatwood - the second magical wood set, same shape as Silverwood (see that block's own doc comment
	 * above for the general pattern: vanilla base classes throughout, a mod-owned
	 * {@link net.minecraft.world.level.block.grower.TreeGrower} instead of the original's hand-rolled
	 * {@code WorldGenGreatwoodTrees}). One real difference from the original,
	 * deliberately dropped: {@code WorldGenGreatwoodTrees}'s "giant" variant (a 2x2 sapling grid consumed to
	 * grow an oversized tree, with cave spiders and loot spawned inside) - vanilla's own 2x2-sapling-to-giant-
	 * tree logic is hardcoded to specific vanilla sapling blocks (dark oak/spruce) in {@code SaplingBlock}
	 * itself, not a generic hook a modded sapling can opt into, and reproducing it custom would mean
	 * rebuilding a real mechanic, not just porting data - out of scope here. A single Greatwood sapling still
	 * grows into a real (if not giant) tree, using {@code minecraft:fancy_trunk_placer} with taller/wider
	 * parameters than Silverwood's for a visually bigger canopy instead.
	 */
	public static final DeferredBlock<RotatedPillarBlock> logGreatwood = BLOCKS.registerBlock("log_greatwood",
			RotatedPillarBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 5.0f).sound(SoundType.WOOD).ignitedByLava());

	public static final DeferredBlock<Block> plankGreatwood = BLOCKS.registerBlock("plank_greatwood",
			Block::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).ignitedByLava());

	public static final DeferredBlock<LeavesBlock> leavesGreatwood = BLOCKS.registerBlock("leaves_greatwood",
			LeavesBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2f).sound(SoundType.GRASS)
					.randomTicks().noOcclusion().isValidSpawn((state, level, pos, type) -> false)
					.isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
					.ignitedByLava().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

	public static final DeferredBlock<SaplingBlock> saplingGreatwood = BLOCKS.registerBlock("sapling_greatwood",
			props -> new SaplingBlock(ThaumcraftTreeGrowers.GREATWOOD, props),
			BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak().sound(SoundType.GRASS)
					.pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

	public static final DeferredBlock<StairBlock> greatwoodStairs = BLOCKS.registerBlock("greatwood_stairs",
			props -> new StairBlock(plankGreatwood.get().defaultBlockState(), props),
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).ignitedByLava());

	public static final DeferredBlock<SlabBlock> greatwoodSlab = BLOCKS.registerBlock("greatwood_slab",
			SlabBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).ignitedByLava());

	/** First device since the "shared inventory-tile base" was scoped and repeatedly deferred - see
	 * {@link thaumcraft.common.tiles.devices.TileRechargePedestal}'s own doc comment for why this is a
	 * purpose-built {@code BlockEntity} rather than a ported generic base. */
	public static final DeferredBlock<thaumcraft.common.blocks.devices.BlockRechargePedestal> rechargePedestal = BLOCKS.registerBlock("recharge_pedestal",
			thaumcraft.common.blocks.devices.BlockRechargePedestal::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE).noOcclusion());

	/** A chest that never links into a double chest and eats dropped items walking over it - see
	 * {@link thaumcraft.common.blocks.devices.BlockHungryChest}'s own doc comment for why it extends
	 * vanilla's {@link net.minecraft.world.level.block.ChestBlock} directly. */
	public static final DeferredBlock<thaumcraft.common.blocks.devices.BlockHungryChest> hungryChest = BLOCKS.registerBlock("hungry_chest",
			props -> new thaumcraft.common.blocks.devices.BlockHungryChest(props, () -> thaumcraft.common.registry.ThaumcraftBlockEntities.HUNGRY_CHEST.get()),
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());

	/** Scatters real light sources around itself while lit - see
	 * {@link thaumcraft.common.tiles.devices.TileLampArcane}'s own doc comment. */
	public static final DeferredBlock<thaumcraft.common.blocks.devices.BlockLampArcane> lampArcane = BLOCKS.registerBlock("lamp_arcane",
			thaumcraft.common.blocks.devices.BlockLampArcane::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.METAL).noOcclusion()
					.lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED) ? 15 : 0));

	/** Drains vis from the local aura and pushes it out as real RF/FE energy - see
	 * {@link thaumcraft.common.tiles.devices.TileVisGenerator}'s own doc comment for why
	 * {@code IEnergyStorage} ports over almost mechanically here, unlike most devices this session. */
	public static final DeferredBlock<thaumcraft.common.blocks.devices.BlockVisGenerator> visGenerator = BLOCKS.registerBlock("vis_generator",
			thaumcraft.common.blocks.devices.BlockVisGenerator::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());

	/** "Everfull Urn" - see {@link thaumcraft.common.tiles.devices.TileWaterJug}'s own doc comment for what's
	 * simplified. Registry id kept as the original's own "everfull_urn", not "water_jug". */
	public static final DeferredBlock<thaumcraft.common.blocks.devices.BlockWaterJug> everfullUrn = BLOCKS.registerBlock("everfull_urn",
			thaumcraft.common.blocks.devices.BlockWaterJug::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.STONE).noOcclusion());

	/** A note-block-like redstone sensor - see {@link thaumcraft.common.tiles.devices.TileArcaneEar}'s own
	 * doc comment for the listening mechanic, and {@link thaumcraft.common.blocks.devices.BlockArcaneEar}'s
	 * for why both variants below share one Java class. */
	public static final DeferredBlock<thaumcraft.common.blocks.devices.BlockArcaneEar> arcaneEar = BLOCKS.registerBlock("arcane_ear",
			props -> new thaumcraft.common.blocks.devices.BlockArcaneEar(props, false),
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion());

	public static final DeferredBlock<thaumcraft.common.blocks.devices.BlockArcaneEar> arcaneEarToggle = BLOCKS.registerBlock("arcane_ear_toggle",
			props -> new thaumcraft.common.blocks.devices.BlockArcaneEar(props, true),
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion());

	/** The first real {@link thaumcraft.api.aspects.IAspectSource}/{@link thaumcraft.api.aspects.IEssentiaTransport}
	 * device this port has built - see {@link thaumcraft.common.tiles.essentia.TileJarFillable}'s own doc
	 * comment for why no physical tube network was needed to make it functional. */
	public static final DeferredBlock<thaumcraft.common.blocks.essentia.BlockJar> jarNormal = BLOCKS.registerBlock("jar_normal",
			thaumcraft.common.blocks.essentia.BlockJar::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.3f).sound(SoundType.GLASS).noOcclusion());

	/** First real consumer of the {@code IEssentiaTransport} draw mechanic {@link #jarNormal} proved out -
	 * see {@link thaumcraft.common.tiles.devices.TileLampGrowth}'s own doc comment. */
	public static final DeferredBlock<thaumcraft.common.blocks.devices.BlockLampGrowth> lampGrowth = BLOCKS.registerBlock("lamp_growth",
			thaumcraft.common.blocks.devices.BlockLampGrowth::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.METAL).noOcclusion()
					.lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED) ? 15 : 0));

	/** Same charge/essentia shell as {@link #lampGrowth} - see
	 * {@link thaumcraft.common.tiles.devices.TileLampFertility}'s own doc comment for the breeding mechanic. */
	public static final DeferredBlock<thaumcraft.common.blocks.devices.BlockLampFertility> lampFertility = BLOCKS.registerBlock("lamp_fertility",
			thaumcraft.common.blocks.devices.BlockLampFertility::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.METAL).noOcclusion()
					.lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED) ? 15 : 0));

	/** A single-unit essentia relay - see {@link thaumcraft.common.tiles.essentia.TileTube}'s own doc
	 * comment for the mechanic and what's simplified (no per-face wand toggling). */
	public static final DeferredBlock<thaumcraft.common.blocks.essentia.BlockTube> tube = BLOCKS.registerBlock("tube",
			thaumcraft.common.blocks.essentia.BlockTube::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion());

	private BlocksTC() {
	}
}

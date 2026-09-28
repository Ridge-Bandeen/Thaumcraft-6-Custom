package thaumcraft.api.items;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.ThaumcraftArmorMaterials;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.items.casters.ItemCaster;
import thaumcraft.common.items.consumables.ItemAlumentum;
import thaumcraft.common.items.resources.ItemMagicDust;
import thaumcraft.common.items.casters.ItemFocusAir;
import thaumcraft.common.items.casters.ItemFocusBreak;
import thaumcraft.common.items.casters.ItemFocusCurse;
import thaumcraft.common.items.casters.ItemFocusEarth;
import thaumcraft.common.items.casters.ItemFocusFire;
import thaumcraft.common.items.casters.ItemFocusFireball;
import thaumcraft.common.items.casters.ItemFocusFlux;
import thaumcraft.common.items.casters.ItemFocusFireBolt;
import thaumcraft.common.items.casters.ItemFocusFrost;
import thaumcraft.common.items.casters.ItemFocusHeal;
import thaumcraft.common.items.casters.ItemFocusSanctuary;
import thaumcraft.common.items.casters.ItemFocusTrap;
import thaumcraft.common.items.curios.ItemCurio;
import thaumcraft.common.items.curios.ItemPechWand;

/**
 * See {@link BlocksTC} for why this only has a couple of fields so far instead of the ~150 the original
 * had - built out incrementally, one vertical slice at a time.
 */
public final class ItemsTC {

	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("thaumcraft");

	public static final DeferredItem<BlockItem> arcaneWorkbench =
			ITEMS.registerSimpleBlockItem("arcane_workbench", BlocksTC.arcaneWorkbench);

	/** See {@link thaumcraft.common.items.tools.ItemThaumometer}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemThaumometer> thaumometer =
			ITEMS.registerItem("thaumometer", thaumcraft.common.items.tools.ItemThaumometer::new);

	/** The Research Table's "ink" slot item - see {@code TileResearchTable}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemScribingTools> scribingTools =
			ITEMS.registerItem("scribing_tools", thaumcraft.common.items.tools.ItemScribingTools::new);

	public static final DeferredItem<ItemCaster> casterBasic =
			ITEMS.registerItem("caster_basic", ItemCaster::new);

	public static final DeferredItem<ItemFocusFire> focus1 =
			ITEMS.registerItem("focus_1", ItemFocusFire::new);

	public static final DeferredItem<BlockItem> crucible =
			ITEMS.registerSimpleBlockItem("crucible", BlocksTC.crucible);

	public static final DeferredItem<BlockItem> focalManipulator =
			ITEMS.registerSimpleBlockItem("focal_manipulator", BlocksTC.focalManipulator);

	public static final DeferredItem<BlockItem> researchTable =
			ITEMS.registerSimpleBlockItem("research_table", BlocksTC.researchTable);

	public static final DeferredItem<BlockItem> golemBuilder =
			ITEMS.registerSimpleBlockItem("golem_builder", BlocksTC.golemBuilder);

	public static final DeferredItem<BlockItem> tableWood =
			ITEMS.registerSimpleBlockItem("table_wood", BlocksTC.tableWood);

	public static final DeferredItem<BlockItem> tableStone =
			ITEMS.registerSimpleBlockItem("table_stone", BlocksTC.tableStone);

	public static final DeferredItem<BlockItem> oreQuartz =
			ITEMS.registerSimpleBlockItem("ore_quartz", BlocksTC.oreQuartz);

	public static final DeferredItem<BlockItem> oreCinnabar =
			ITEMS.registerSimpleBlockItem("ore_cinnabar", BlocksTC.oreCinnabar);

	public static final DeferredItem<Item> quicksilver =
			ITEMS.registerSimpleItem("quicksilver");

	/** Ore purification alt-path via the already-shipped Crucible - smelts into 2 iron ingots instead of
	 * the usual 1. Only the iron variant is ported this pass; see {@code data/thaumcraft/recipe/cluster_iron.json}. */
	public static final DeferredItem<Item> clusterIron =
			ITEMS.registerSimpleItem("cluster_iron");

	/** Same shape as {@link #clusterIron} - smelts into 2 quicksilver instead of the usual 1 from
	 * {@code thaumcraft:ore_cinnabar}. See {@code data/thaumcraft/recipe/cluster_cinnabar.json}. */
	public static final DeferredItem<Item> clusterCinnabar =
			ITEMS.registerSimpleItem("cluster_cinnabar");

	/** Same shape as {@link #clusterIron} - smelts into 2 gold ingots instead of the usual 1. See
	 * {@code data/thaumcraft/recipe/cluster_gold.json}. */
	public static final DeferredItem<Item> clusterGold =
			ITEMS.registerSimpleItem("cluster_gold");

	/** The original's 4th complete-loop cluster - unlike iron/cinnabar/gold, it has no Crucible recipe at
	 * all in the original, only reachable there via the pickaxe {@code REFINING} infusion enchantment (a
	 * fortune-like chance to get a cluster back instead of the plain ore drop). Now that {@code ToolEvents}
	 * ports that effect, this is real too - see {@link thaumcraft.common.lib.events.ToolEvents}'s own doc
	 * comment. Smelts into 2 quartz instead of the usual 1 (same shape as the others). */
	public static final DeferredItem<Item> clusterQuartz =
			ITEMS.registerSimpleItem("cluster_quartz");

	public static final DeferredItem<thaumcraft.common.items.resources.ItemCrystalEssence> crystalEssence =
			ITEMS.registerItem("crystal_essence", thaumcraft.common.items.resources.ItemCrystalEssence::new);

	/** Real bucket for {@link thaumcraft.api.blocks.BlocksTC#liquidDeath} - the true original's own real
	 * {@code FluidRegistry.addBucketForFluid} equivalent. {@code .craftRemainder(Items.BUCKET)} matches every
	 * vanilla fluid bucket's own real "using it up returns an empty bucket" behavior. */
	public static final DeferredItem<BucketItem> bucketDeath = ITEMS.registerItem("bucket_death",
			props -> new BucketItem(thaumcraft.common.registry.ThaumcraftFluids.LIQUID_DEATH.get(), props.craftRemainder(Items.BUCKET).stacksTo(1)));

	public static final DeferredItem<ItemFocusFrost> focusFrost =
			ITEMS.registerItem("focus_frost", ItemFocusFrost::new);

	public static final DeferredItem<ItemFocusBreak> focusBreak =
			ITEMS.registerItem("focus_break", ItemFocusBreak::new);

	public static final DeferredItem<ItemFocusFireBolt> focusFireBolt =
			ITEMS.registerItem("focus_fire_bolt", ItemFocusFireBolt::new);

	public static final DeferredItem<ItemFocusHeal> focusHeal =
			ITEMS.registerItem("focus_heal", ItemFocusHeal::new);

	public static final DeferredItem<ItemFocusCurse> focusCurse =
			ITEMS.registerItem("focus_curse", ItemFocusCurse::new);

	public static final DeferredItem<ItemFocusEarth> focusEarth =
			ITEMS.registerItem("focus_earth", ItemFocusEarth::new);

	public static final DeferredItem<ItemFocusFireball> focusFireball =
			ITEMS.registerItem("focus_fireball", ItemFocusFireball::new);

	public static final DeferredItem<ItemFocusFlux> focusFlux =
			ITEMS.registerItem("focus_flux", ItemFocusFlux::new);

	public static final DeferredItem<ItemFocusAir> focusAir =
			ITEMS.registerItem("focus_air", ItemFocusAir::new);

	public static final DeferredItem<ItemFocusTrap> focusTrap =
			ITEMS.registerItem("focus_trap", ItemFocusTrap::new);

	public static final DeferredItem<ItemFocusSanctuary> focusSanctuary =
			ITEMS.registerItem("focus_sanctuary", ItemFocusSanctuary::new);

	/** The original's real, genuinely blank foci - see {@link thaumcraft.common.items.casters.ItemFocusBlank}'s
	 * own doc comment for why these coexist with the 10 fixed-chain foci above instead of replacing them. */
	public static final DeferredItem<thaumcraft.common.items.casters.ItemFocusBlank> focusBlank1 =
			ITEMS.registerItem("focus_blank_1", props -> new thaumcraft.common.items.casters.ItemFocusBlank(props, 15));

	public static final DeferredItem<thaumcraft.common.items.casters.ItemFocusBlank> focusBlank2 =
			ITEMS.registerItem("focus_blank_2", props -> new thaumcraft.common.items.casters.ItemFocusBlank(props, 25));

	public static final DeferredItem<thaumcraft.common.items.casters.ItemFocusBlank> focusBlank3 =
			ITEMS.registerItem("focus_blank_3", props -> new thaumcraft.common.items.casters.ItemFocusBlank(props, 50));

	/** The real 18-slot portable focus container - see {@code ItemFocusPouch}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.casters.ItemFocusPouch> focusPouch =
			ITEMS.registerItem("focus_pouch", props -> new thaumcraft.common.items.casters.ItemFocusPouch(props.stacksTo(1)));

	public static final DeferredItem<BlockItem> oreAmber =
			ITEMS.registerSimpleBlockItem("ore_amber", BlocksTC.oreAmber);

	/** The raw gem itself - inert for now (the original used it in several not-yet-ported recipes:
	 * warded glass, arcane ear, various golemancy items). */
	public static final DeferredItem<Item> amber =
			ITEMS.registerSimpleItem("amber");

	/** Golem construction components - see {@code GolemProperties}' own doc comment for the parts system
	 * they craft into. Plain items, same as the original ({@code ItemTCBase} with metadata subtypes, split
	 * into separate items here matching every other metadata-variant item this port has ported).
	 * {@link #plateBrass} closed its own real raw-material chain (ingot/nugget/block/plate) earlier this
	 * session, see {@link #ingotBrass}'s own doc comment; {@code mechanismSimple} (this item) and
	 * {@link #mechanismComplex} both got their own real recipes this pass too (see
	 * {@code data/thaumcraft/recipe/mechanism_simple.json} - plate_brass+plate_iron+a stick - and
	 * {@code mechanism_complex.json} - mechanism_simple wrapped in plate_thaumium around a vanilla piston).
	 * {@link #plateIron}'s own real recipe closed right after, so the whole chain is genuinely reachable end
	 * to end now. {@link #plateVoid} got its own real recipe too, once {@link #ingotVoid}'s chain existed to
	 * feed it (see its own doc comment). {@link #mindClockwork}/{@link #mindBiothaumic}/{@link #moduleVision}/
	 * {@link #moduleAggression} all got their own real recipes too, once {@code mechanismSimple}/
	 * {@code mechanismComplex}/{@code plateBrass} existed to feed them (see each item's own doc comment). */
	public static final DeferredItem<Item> mechanismSimple =
			ITEMS.registerSimpleItem("mechanism_simple");

	/** See {@link #mechanismSimple}'s own doc comment - both got real recipes this pass. Unblocks the
	 * Condenser's own real recipe (see {@code data/thaumcraft/recipe/condenser.json}), the Stabilizer's
	 * (see {@code data/thaumcraft/recipe/stabilizer.json}), the Void Siphon's (see
	 * {@code data/thaumcraft/recipe/void_siphon.json}), and - once the golem {@code mind} item family got its
	 * own real recipes a couple chunks later - the whole turret-placer family too (see
	 * {@code data/thaumcraft/recipe/turret_placer_bore.json}). */
	public static final DeferredItem<Item> mechanismComplex =
			ITEMS.registerSimpleItem("mechanism_complex");

	/** Brass - real raw-material chain closed this pass (ingot/nugget/block/plate, matching
	 * {@link #ingotThaumium}'s own real precedent exactly). Real, load-bearing gap this closes: the already-
	 * real {@code data/thaumcraft/research/alchemy.json}'s own METALLURGY research node already references a
	 * real {@code thaumcraft:brassingot} recipe (its own stage-1 recipe list) - that recipe never existed
	 * until now, so {@link #plateBrass} (needed by several already-shipped devices' own real recipes -
	 * Potion Sprayer, Void Siphon, the Charm of Undying infusion, Tube Filter/Restrict, Arcane Ear) was
	 * genuinely unobtainable in survival before this. See {@code data/thaumcraft/recipe/brassingot.json} -
	 * a real Crucible recipe (iron ingot + 5 real Instrumentum, matching the original's own real formula)
	 * gated on the real METALLURGY research, same shape {@code ingot_thaumium.json} already established. */
	public static final DeferredItem<Item> ingotBrass =
			ITEMS.registerSimpleItem("ingot_brass");

	public static final DeferredItem<Item> nuggetBrass =
			ITEMS.registerSimpleItem("nugget_brass");

	public static final DeferredItem<Item> plateBrass =
			ITEMS.registerSimpleItem("plate_brass");

	/** Real recipe now (see {@code data/thaumcraft/recipe/plate_iron.json}) - a plain vanilla 3-ingot row
	 * batching into 3 plates, the exact same shape {@link #plateBrass}/{@code plateThaumium} already use,
	 * matching the true original's own real convention for every plate tier. This was the single most-cited
	 * remaining gap in {@link #mechanismSimple}'s own doc comment - a dozen-plus already-shipped recipes
	 * across this whole session (mechanismSimple/Complex, Levitator, Dioptra, tube_valve/metal_alchemical,
	 * Potion Sprayer, Smelter Aux/Vent, and more) bottom out on it, all now genuinely reachable end to end. */
	public static final DeferredItem<Item> plateIron =
			ITEMS.registerSimpleItem("plate_iron");
	// plateThaumium already registered above (see ingotThaumium's own section) - reused here as-is.

	/** Real recipe now (see {@code data/thaumcraft/recipe/plate_void.json}) - same plain 3-ingot-row shape as
	 * every other plate tier, batching {@link #ingotVoid}. */
	public static final DeferredItem<Item> plateVoid =
			ITEMS.registerSimpleItem("plate_void");
	/** Real recipe now (see {@code data/thaumcraft/recipe/mind_clockwork.json}) - glass panes, plate_brass,
	 * mechanism_simple and a vanilla comparator, all real. The base golem "mind" - {@link #mindBiothaumic}
	 * is its own real Infusion upgrade. */
	public static final DeferredItem<Item> mindClockwork =
			ITEMS.registerSimpleItem("mind_clockwork");

	/** Real recipe now (see {@code data/thaumcraft/recipe/mind_biothaumic.json}) - a real Infusion Altar
	 * upgrade over {@link #mindClockwork}, using a real {@link #brain} and {@link #mechanismComplex}. */
	public static final DeferredItem<Item> mindBiothaumic =
			ITEMS.registerSimpleItem("mind_biothaumic");

	/** Real recipe now (see {@code data/thaumcraft/recipe/module_vision.json}) - a glass bottle, fermented
	 * spider eyes, plate_brass and mechanism_simple, all real. */
	public static final DeferredItem<Item> moduleVision =
			ITEMS.registerSimpleItem("module_vision");

	/** Real recipe now (see {@code data/thaumcraft/recipe/module_aggression.json}) - glass panes, blaze
	 * powder, plate_brass and mechanism_simple, all real. */
	public static final DeferredItem<Item> moduleAggression =
			ITEMS.registerSimpleItem("module_aggression");

	/** Places a real, tamed {@code EntityThaumcraftGolem} - see that item's own doc comment. Registered as
	 * "golem" (not "golem_placer") to reuse the original's own already-present {@code golem.json}/
	 * {@code golem.png} item assets as-is. */
	public static final DeferredItem<thaumcraft.common.golems.ItemGolemPlacer> golemPlacer =
			ITEMS.registerItem("golem", thaumcraft.common.golems.ItemGolemPlacer::new);

	/** Places a real {@code SealGuard} - see {@code ItemSealPlacer}'s own doc comment for why this is one
	 * dedicated item per seal type rather than the original's shared metadata-variant item. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealGuard =
			ITEMS.registerItem("seal_guard", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealGuard::new));

	/** Places a real {@code SealLumber} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealLumber =
			ITEMS.registerItem("seal_lumber", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealLumber::new));

	/** Sneak-right-click a sealed block face to remove that seal - see {@code ItemGolemBell}'s own doc
	 * comment for the real, documented cuts (no seal-config or golem-overview GUI yet). */
	public static final DeferredItem<thaumcraft.common.golems.ItemGolemBell> golemBell =
			ITEMS.registerItem("golem_bell", thaumcraft.common.golems.ItemGolemBell::new);

	/** Places a real {@code SealHarvest} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealHarvest =
			ITEMS.registerItem("seal_harvest", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealHarvest::new));

	/** Places a real {@code SealButcher} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealButcher =
			ITEMS.registerItem("seal_butcher", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealButcher::new));

	/** Places a real {@code SealBreaker} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealBreaker =
			ITEMS.registerItem("seal_breaker", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealBreaker::new));

	/** Places a real {@code SealPickup} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealPickup =
			ITEMS.registerItem("seal_pickup", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealPickup::new));

	/** Places a real {@code SealStock} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealStock =
			ITEMS.registerItem("seal_stock", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealStock::new));

	/** Places a real {@code SealProvide} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealProvide =
			ITEMS.registerItem("seal_provider", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealProvide::new));

	/** Places a real {@code SealFill} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealFill =
			ITEMS.registerItem("seal_fill", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealFill::new));

	/** Places a real {@code SealEmpty} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealEmpty =
			ITEMS.registerItem("seal_empty", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealEmpty::new));

	/** Places a real {@code SealUse} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealUse =
			ITEMS.registerItem("seal_use", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealUse::new));

	/** Places a real {@code SealGuardAdvanced} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealGuardAdvanced =
			ITEMS.registerItem("seal_guard_advanced", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealGuardAdvanced::new));

	/** Places a real {@code SealBreakerAdvanced} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealBreakerAdvanced =
			ITEMS.registerItem("seal_breaker_advanced", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealBreakerAdvanced::new));

	/** Places a real {@code SealPickupAdvanced} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealPickupAdvanced =
			ITEMS.registerItem("seal_pickup_advanced", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealPickupAdvanced::new));

	/** Places a real {@code SealFillAdvanced} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealFillAdvanced =
			ITEMS.registerItem("seal_fill_advanced", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealFillAdvanced::new));

	/** Places a real {@code SealEmptyAdvanced} - see that class's own doc comment. */
	public static final DeferredItem<thaumcraft.common.golems.ItemSealPlacer> sealEmptyAdvanced =
			ITEMS.registerItem("seal_empty_advanced", props -> new thaumcraft.common.golems.ItemSealPlacer(props, thaumcraft.common.golems.seals.SealEmptyAdvanced::new));

	public static final DeferredItem<ItemCurio> curio =
			ITEMS.registerItem("curio", ItemCurio::new);

	/** The real Thaumonomicon research browser's item - see {@code ItemThaumonomicon}'s own doc comment. One
	 * registry entry, not the original's two damage-value variants (normal/creative "cheat sheet" - modern
	 * items don't have metadata subtypes, same simplification {@code ItemCurio}'s own doc comment already
	 * used for its 8 dropped variants). */
	public static final DeferredItem<thaumcraft.common.items.curios.ItemThaumonomicon> thaumonomicon =
			ITEMS.registerItem("thaumonomicon", props -> new thaumcraft.common.items.curios.ItemThaumonomicon(props.stacksTo(1)));

	public static final DeferredItem<ItemPechWand> pechWand =
			ITEMS.registerItem("pech_wand", ItemPechWand::new);

	/** Restored 2026-09-27 - see {@link thaumcraft.common.items.curios.ItemEnchantmentPlaceholder}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.curios.ItemEnchantmentPlaceholder> enchantedPlaceholder =
			ITEMS.registerItem("enchanted_placeholder", thaumcraft.common.items.curios.ItemEnchantmentPlaceholder::new);

	/** A collapsed {@link thaumcraft.common.entities.EntityFluxRift}'s degraded fragment - see that item's
	 * own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.curios.ItemPrimordialPearl> primordialPearl =
			ITEMS.registerItem("primordial_pearl", thaumcraft.common.items.curios.ItemPrimordialPearl::new);

	/** Another {@code EntityFluxRift} collapse drop - a plain item in the original too (no custom class), no
	 * real use yet beyond that drop until the deeper Void-tier content it originally fed exists. */
	public static final DeferredItem<Item> voidSeed =
			ITEMS.registerSimpleItem("void_seed");

	/** Quicksilver's first real use - a reflective glass pane. Real consumer now: the central item of
	 * {@code mirror.json}'s infusion recipe - see {@link thaumcraft.common.blocks.devices.BlockMirror}'s own
	 * doc comment. The original's other consumers (hand mirror/scrying mirror/golem sight devices) still
	 * aren't ported. */
	public static final DeferredItem<Item> mirroredGlass =
			ITEMS.registerSimpleItem("mirrored_glass");

	public static final DeferredItem<BlockItem> amberBlock =
			ITEMS.registerSimpleBlockItem("amber_block", BlocksTC.amberBlock);

	public static final DeferredItem<BlockItem> amberBrick =
			ITEMS.registerSimpleBlockItem("amber_brick", BlocksTC.amberBrick);

	public static final DeferredItem<BlockItem> stoneArcane =
			ITEMS.registerSimpleBlockItem("stone_arcane", BlocksTC.stoneArcane);

	public static final DeferredItem<BlockItem> stoneArcaneStairs =
			ITEMS.registerSimpleBlockItem("stone_arcane_stairs", BlocksTC.stoneArcaneStairs);

	public static final DeferredItem<BlockItem> stoneArcaneSlab =
			ITEMS.registerSimpleBlockItem("stone_arcane_slab", BlocksTC.stoneArcaneSlab);

	public static final DeferredItem<BlockItem> stoneArcaneBrick =
			ITEMS.registerSimpleBlockItem("stone_arcane_brick", BlocksTC.stoneArcaneBrick);

	public static final DeferredItem<BlockItem> stoneArcaneBrickStairs =
			ITEMS.registerSimpleBlockItem("stone_arcane_brick_stairs", BlocksTC.stoneArcaneBrickStairs);

	public static final DeferredItem<BlockItem> stoneArcaneBrickSlab =
			ITEMS.registerSimpleBlockItem("stone_arcane_brick_slab", BlocksTC.stoneArcaneBrickSlab);

	/** See {@link BlocksTC#stoneAncient}'s own doc comment for why this whole family is real but genuinely
	 * unreachable, matching the true original exactly. */
	public static final DeferredItem<BlockItem> stoneAncient =
			ITEMS.registerSimpleBlockItem("stone_ancient", BlocksTC.stoneAncient);
	public static final DeferredItem<BlockItem> stoneAncientTile =
			ITEMS.registerSimpleBlockItem("stone_ancient_tile", BlocksTC.stoneAncientTile);
	public static final DeferredItem<BlockItem> stoneAncientRock =
			ITEMS.registerSimpleBlockItem("stone_ancient_rock", BlocksTC.stoneAncientRock);
	public static final DeferredItem<BlockItem> stoneAncientGlyphed =
			ITEMS.registerSimpleBlockItem("stone_ancient_glyphed", BlocksTC.stoneAncientGlyphed);
	public static final DeferredItem<BlockItem> stoneAncientDoorway =
			ITEMS.registerSimpleBlockItem("stone_ancient_doorway", BlocksTC.stoneAncientDoorway);
	public static final DeferredItem<BlockItem> stoneEldritchTile =
			ITEMS.registerSimpleBlockItem("stone_eldritch_tile", BlocksTC.stoneEldritchTile);
	public static final DeferredItem<BlockItem> stairsAncient =
			ITEMS.registerSimpleBlockItem("stairs_ancient", BlocksTC.stairsAncient);
	public static final DeferredItem<BlockItem> slabAncient =
			ITEMS.registerSimpleBlockItem("slab_ancient", BlocksTC.slabAncient);
	public static final DeferredItem<BlockItem> slabEldritch =
			ITEMS.registerSimpleBlockItem("slab_eldritch", BlocksTC.slabEldritch);
	public static final DeferredItem<BlockItem> pedestalAncient =
			ITEMS.registerSimpleBlockItem("pedestal_ancient", BlocksTC.pedestalAncient);
	public static final DeferredItem<BlockItem> pedestalEldritch =
			ITEMS.registerSimpleBlockItem("pedestal_eldritch", BlocksTC.pedestalEldritch);

	public static final DeferredItem<BlockItem> logSilverwood =
			ITEMS.registerSimpleBlockItem("log_silverwood", BlocksTC.logSilverwood);

	public static final DeferredItem<BlockItem> plankSilverwood =
			ITEMS.registerSimpleBlockItem("plank_silverwood", BlocksTC.plankSilverwood);

	public static final DeferredItem<BlockItem> leavesSilverwood =
			ITEMS.registerSimpleBlockItem("leaves_silverwood", BlocksTC.leavesSilverwood);

	public static final DeferredItem<BlockItem> saplingSilverwood =
			ITEMS.registerSimpleBlockItem("sapling_silverwood", BlocksTC.saplingSilverwood);

	public static final DeferredItem<BlockItem> silverwoodStairs =
			ITEMS.registerSimpleBlockItem("silverwood_stairs", BlocksTC.silverwoodStairs);

	public static final DeferredItem<BlockItem> silverwoodSlab =
			ITEMS.registerSimpleBlockItem("silverwood_slab", BlocksTC.silverwoodSlab);

	public static final DeferredItem<BlockItem> logGreatwood =
			ITEMS.registerSimpleBlockItem("log_greatwood", BlocksTC.logGreatwood);

	public static final DeferredItem<BlockItem> plankGreatwood =
			ITEMS.registerSimpleBlockItem("plank_greatwood", BlocksTC.plankGreatwood);

	public static final DeferredItem<BlockItem> leavesGreatwood =
			ITEMS.registerSimpleBlockItem("leaves_greatwood", BlocksTC.leavesGreatwood);

	public static final DeferredItem<BlockItem> saplingGreatwood =
			ITEMS.registerSimpleBlockItem("sapling_greatwood", BlocksTC.saplingGreatwood);

	public static final DeferredItem<BlockItem> greatwoodStairs =
			ITEMS.registerSimpleBlockItem("greatwood_stairs", BlocksTC.greatwoodStairs);

	public static final DeferredItem<BlockItem> greatwoodSlab =
			ITEMS.registerSimpleBlockItem("greatwood_slab", BlocksTC.greatwoodSlab);

	/** A common crafting component across several devices (Wand Workbench, Vis Battery, Recharge Pedestal,
	 * etc. - all still unported). Its own recipe had been blocked all session on the Crucible not existing
	 * yet; now that it does, this also lets caster_basic's recipe go back to its authentic pattern (see
	 * data/thaumcraft/recipe/caster_basic.json) instead of the invented substitute used since the wand was
	 * first built. */
	public static final DeferredItem<Item> visResonator =
			ITEMS.registerSimpleItem("vis_resonator");

	public static final DeferredItem<ItemAlumentum> alumentum =
			ITEMS.registerItem("alumentum", ItemAlumentum::new);

	/** See {@link thaumcraft.common.items.consumables.ItemBottleTaint}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.consumables.ItemBottleTaint> bottleTaint =
			ITEMS.registerItem("bottle_taint", thaumcraft.common.items.consumables.ItemBottleTaint::new);

	/** See {@link thaumcraft.common.items.consumables.ItemCausalityCollapser}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.consumables.ItemCausalityCollapser> causalityCollapser =
			ITEMS.registerItem("causality_collapser", thaumcraft.common.items.consumables.ItemCausalityCollapser::new);

	/** The 11th and last {@code ItemNuggets} metadata variant from the original ("rareearth", index 10) -
	 * needed for {@link #morphicResonator}'s own real crafting recipe. <b>Correction:</b> previously (wrongly)
	 * called a genuine upstream dead end - the user recalled real survival access and asked for a wiki check,
	 * which surfaced two real original sources this port's first, too-narrow search had both missed (it only
	 * grepped for the literal string {@code "rareearth"}, missing every reference that used the nugget's
	 * metadata index instead): a real ore-break bonus
	 * ({@link thaumcraft.common.lib.events.ToolEvents#RAREEARTH_ORE_CHANCE}, unconditional, no multiblock
	 * needed) and a real Infernal Furnace smelting bonus (see
	 * {@link thaumcraft.common.tiles.devices.TileInfernalFurnace}). Both restored - see the port-tracking
	 * memory for the fuller correction. */
	public static final DeferredItem<Item> nuggetRareearth =
			ITEMS.registerSimpleItem("nugget_rareearth");

	/** Real Infernal Furnace smelting-bonus outputs (see {@code TileInfernalFurnace}'s own doc comment) - the
	 * rest of the original's 11-variant nugget family, beyond {@code nuggetThaumium}/{@code nuggetBrass}/
	 * {@code nuggetRareearth} (real content already) and {@code nuggetVoid} (not needed by anything real yet,
	 * left unported). Vanilla already has real iron/gold nuggets, covering 2 of the remaining 8. */
	public static final DeferredItem<Item> nuggetCopper =
			ITEMS.registerSimpleItem("nugget_copper");
	public static final DeferredItem<Item> nuggetTin =
			ITEMS.registerSimpleItem("nugget_tin");
	public static final DeferredItem<Item> nuggetSilver =
			ITEMS.registerSimpleItem("nugget_silver");
	public static final DeferredItem<Item> nuggetLead =
			ITEMS.registerSimpleItem("nugget_lead");
	public static final DeferredItem<Item> nuggetQuicksilver =
			ITEMS.registerSimpleItem("nugget_quicksilver");
	public static final DeferredItem<Item> nuggetQuartz =
			ITEMS.registerSimpleItem("nugget_quartz");

	/** A real, previously-missing {@code BlockItem} for {@code infernalFurnace} - its own stale
	 * {@code data/thaumcraft/recipe/infernal_furnace.json} (an invented direct-crafting substitute for the
	 * real multiblock ritual, removed now that {@code DustTriggerMultiblock} is real - see
	 * {@link thaumcraft.common.registry.ThaumcraftDustTriggers}) referenced this exact item id and had been
	 * silently failing to load with no real item behind it (confirmed via a real
	 * {@code JsonParseException: Unknown registry key ... thaumcraft:infernal_furnace} at every past GameTest
	 * boot this whole session - noted, not chased down, until now). Still needed regardless of acquisition
	 * method, for basic break/drop/creative-tab functionality. */
	public static final DeferredItem<BlockItem> infernalFurnace =
			ITEMS.registerSimpleBlockItem("infernal_furnace", BlocksTC.infernalFurnace);

	public static final DeferredItem<BlockItem> placeholderNetherbrick =
			ITEMS.registerSimpleBlockItem("placeholder_netherbrick", BlocksTC.placeholderNetherbrick);

	public static final DeferredItem<BlockItem> placeholderObsidian =
			ITEMS.registerSimpleBlockItem("placeholder_obsidian", BlocksTC.placeholderObsidian);

	public static final DeferredItem<BlockItem> placeholderBars =
			ITEMS.registerSimpleBlockItem("placeholder_bars", BlocksTC.placeholderBars);

	public static final DeferredItem<BlockItem> placeholderCauldron =
			ITEMS.registerSimpleBlockItem("placeholder_cauldron", BlocksTC.placeholderCauldron);

	public static final DeferredItem<BlockItem> placeholderAnvil =
			ITEMS.registerSimpleBlockItem("placeholder_anvil", BlocksTC.placeholderAnvil);

	public static final DeferredItem<BlockItem> placeholderTable =
			ITEMS.registerSimpleBlockItem("placeholder_table", BlocksTC.placeholderTable);

	/** A crafting component for the Centrifuge, Condenser, Arcane Bore and Causality Collapser (see
	 * {@code data/thaumcraft/recipe/morphic_resonator.json} for its own real recipe - a glass pane, a brass
	 * plate and a {@link #nuggetRareearth}). The Causality Collapser's Infusion recipe (see
	 * {@code data/thaumcraft/recipe/infusion_causality_collapser.json}) and, since {@code metalAlchemical}
	 * became real too, the Centrifuge's own recipe ({@code data/thaumcraft/recipe/centrifuge.json}) both use
	 * the real ingredient now. Condenser and Arcane Bore's own real recipes both still need
	 * {@code mechanismComplex} as well - a real item never ported this session - so neither can be fully
	 * restored yet even though this ingredient alone is no longer the blocker for either. */
	public static final DeferredItem<Item> morphicResonator =
			ITEMS.registerSimpleItem("morphic_resonator");

	public static final DeferredItem<BlockItem> rechargePedestal =
			ITEMS.registerSimpleBlockItem("recharge_pedestal", BlocksTC.rechargePedestal);

	public static final DeferredItem<BlockItem> hungryChest =
			ITEMS.registerSimpleBlockItem("hungry_chest", BlocksTC.hungryChest);

	public static final DeferredItem<ItemMagicDust> salisMundus =
			ITEMS.registerItem("salis_mundus", ItemMagicDust::new);

	public static final DeferredItem<BlockItem> lampArcane =
			ITEMS.registerSimpleBlockItem("lamp_arcane", BlocksTC.lampArcane);

	public static final DeferredItem<BlockItem> visGenerator =
			ITEMS.registerSimpleBlockItem("vis_generator", BlocksTC.visGenerator);

	public static final DeferredItem<BlockItem> everfullUrn =
			ITEMS.registerSimpleBlockItem("everfull_urn", BlocksTC.everfullUrn);

	public static final DeferredItem<BlockItem> arcaneEar =
			ITEMS.registerSimpleBlockItem("arcane_ear", BlocksTC.arcaneEar);

	public static final DeferredItem<BlockItem> arcaneEarToggle =
			ITEMS.registerSimpleBlockItem("arcane_ear_toggle", BlocksTC.arcaneEarToggle);

	/** Placed through {@code JarBlockItem} so a broken jar keeps its held essentia and label - see that class's
	 * own doc comment. */
	public static final DeferredItem<thaumcraft.common.blocks.essentia.JarBlockItem> jarNormal =
			ITEMS.register("jar_normal", () -> new thaumcraft.common.blocks.essentia.JarBlockItem(BlocksTC.jarNormal.get(), new Item.Properties()));

	public static final DeferredItem<thaumcraft.common.blocks.essentia.JarBlockItem> jarVoid =
			ITEMS.register("jar_void", () -> new thaumcraft.common.blocks.essentia.JarBlockItem(BlocksTC.jarVoid.get(), new Item.Properties()));

	/** Placed through {@code JarBrainBlockItem} so a broken jar keeps its stored XP - see that class's own doc
	 * comment. */
	public static final DeferredItem<thaumcraft.common.blocks.essentia.JarBrainBlockItem> jarBrain =
			ITEMS.register("jar_brain", () -> new thaumcraft.common.blocks.essentia.JarBrainBlockItem(BlocksTC.jarBrain.get(), new Item.Properties()));

	/** See {@link thaumcraft.common.blocks.essentia.BlockJar}'s own doc comment - locks a jar out of
	 * pull-based {@code IAspectSource} scans via {@link thaumcraft.common.tiles.essentia.TileJarFillable#applyBrace}.
	 * A plain item - all the real behavior lives in {@code BlockJar#useItemOn} reacting to the held stack,
	 * same pattern the essentia-container-item branch right above it already uses. */
	public static final DeferredItem<Item> jarBrace =
			ITEMS.registerSimpleItem("jar_brace");

	public static final DeferredItem<BlockItem> lampGrowth =
			ITEMS.registerSimpleBlockItem("lamp_growth", BlocksTC.lampGrowth);

	public static final DeferredItem<BlockItem> lampFertility =
			ITEMS.registerSimpleBlockItem("lamp_fertility", BlocksTC.lampFertility);

	public static final DeferredItem<BlockItem> tube =
			ITEMS.registerSimpleBlockItem("tube", BlocksTC.tube);

	public static final DeferredItem<BlockItem> tubeFilter =
			ITEMS.registerSimpleBlockItem("tube_filter", BlocksTC.tubeFilter);

	public static final DeferredItem<BlockItem> tubeRestrict =
			ITEMS.registerSimpleBlockItem("tube_restrict", BlocksTC.tubeRestrict);

	public static final DeferredItem<BlockItem> tubeOneway =
			ITEMS.registerSimpleBlockItem("tube_oneway", BlocksTC.tubeOneway);

	public static final DeferredItem<BlockItem> tubeValve =
			ITEMS.registerSimpleBlockItem("tube_valve", BlocksTC.tubeValve);

	public static final DeferredItem<BlockItem> tubeBuffer =
			ITEMS.registerSimpleBlockItem("tube_buffer", BlocksTC.tubeBuffer);

	public static final DeferredItem<BlockItem> essentiaTransportInput =
			ITEMS.registerSimpleBlockItem("essentia_input", BlocksTC.essentiaTransportInput);

	public static final DeferredItem<BlockItem> essentiaTransportOutput =
			ITEMS.registerSimpleBlockItem("essentia_output", BlocksTC.essentiaTransportOutput);

	/** Thaumium - the mod's own base metal tier, obtained by dissolving an iron ingot in a Crucible with
	 * magic+earth aspects (already fully reachable: the Crucible and all 37 Crystal Essence aspects have
	 * been real since earlier this session). See {@code data/thaumcraft/recipe/ingot_thaumium.json}. Just
	 * the raw material chain this pass (ingot/nugget/block/plate) - tools and armor need a real modern
	 * {@code Tier}/{@code ArmorMaterial} registration (a genuinely different API surface, not touched yet
	 * this session) and are deferred to a follow-up. */
	public static final DeferredItem<Item> ingotThaumium =
			ITEMS.registerSimpleItem("ingot_thaumium");

	public static final DeferredItem<Item> nuggetThaumium =
			ITEMS.registerSimpleItem("nugget_thaumium");

	public static final DeferredItem<Item> plateThaumium =
			ITEMS.registerSimpleItem("plate_thaumium");

	public static final DeferredItem<BlockItem> metalBlockThaumium =
			ITEMS.registerSimpleBlockItem("metal_block_thaumium", BlocksTC.metalBlockThaumium);

	public static final DeferredItem<BlockItem> metalBlockBrass =
			ITEMS.registerSimpleBlockItem("metal_block_brass", BlocksTC.metalBlockBrass);

	/** Void - the mod's Eldritch-tier metal, obtained by dissolving a {@link #voidSeed} (a real, already-
	 * obtainable {@code EntityFluxRift} collapse drop) in a Crucible with metal+flux aspects, gated on the
	 * real {@code BASEELDRITCH} research. See {@code data/thaumcraft/recipe/ingot_void.json}. Just the raw
	 * material chain this pass (ingot/block/plate, matching {@link #ingotThaumium}'s own precedent - no
	 * nugget, same "not needed by anything real yet" call already made for {@code nuggetVoid}). Unblocks the
	 * Void Siphon's own real recipe ({@code data/thaumcraft/recipe/void_siphon.json}); tools/armor and the
	 * Essentia Smelter's own Void tier both still need their own separate, larger work. */
	public static final DeferredItem<Item> ingotVoid =
			ITEMS.registerSimpleItem("ingot_void");

	public static final DeferredItem<BlockItem> metalBlockVoid =
			ITEMS.registerSimpleBlockItem("metal_block_void", BlocksTC.metalBlockVoid);

	/** {@code ThaumcraftMaterials.TOOLMAT_THAUMIUM} from the original: harvest level 3 (diamond-equivalent -
	 * modern Minecraft has no numeric harvest level any more, blocks are gated by a "can't be harvested by"
	 * tag instead, so this maps to the same tag diamond tools are exempt from), 500 uses, 7.0 mining speed,
	 * +2.5 attack damage, 22 enchantability, repairable with a Thaumium ingot. {@link SimpleTier} is
	 * NeoForge's own helper for exactly this - a custom {@link Tier} with no enum entry. Not registered with
	 * {@code TierSortingRegistry} (that only matters for cross-mod tier ordering in smithing-table repair
	 * predicates and villager trades, neither of which exist here yet). */
	private static final Tier TIER_THAUMIUM = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 500, 7.0F, 2.5F, 22,
			() -> Ingredient.of(ItemsTC.ingotThaumium));

	public static final DeferredItem<Item> thaumiumPick = ITEMS.registerItem("thaumium_pick",
			props -> new PickaxeItem(TIER_THAUMIUM, props.attributes(PickaxeItem.createAttributes(TIER_THAUMIUM, 1.0F, -2.8F))));

	public static final DeferredItem<Item> thaumiumAxe = ITEMS.registerItem("thaumium_axe",
			props -> new AxeItem(TIER_THAUMIUM, props.attributes(AxeItem.createAttributes(TIER_THAUMIUM, 5.0F, -3.0F))));

	public static final DeferredItem<Item> thaumiumShovel = ITEMS.registerItem("thaumium_shovel",
			props -> new ShovelItem(TIER_THAUMIUM, props.attributes(ShovelItem.createAttributes(TIER_THAUMIUM, 1.5F, -3.0F))));

	public static final DeferredItem<Item> thaumiumHoe = ITEMS.registerItem("thaumium_hoe",
			props -> new HoeItem(TIER_THAUMIUM, props.attributes(HoeItem.createAttributes(TIER_THAUMIUM, -3.0F, 0.0F))));

	public static final DeferredItem<Item> thaumiumSword = ITEMS.registerItem("thaumium_sword",
			props -> new SwordItem(TIER_THAUMIUM, props.attributes(SwordItem.createAttributes(TIER_THAUMIUM, 3, -2.4F))));

	/** {@code ThaumcraftMaterials.TOOLMAT_VOID} from the original: harvest level 4 (diamond-tier-and-up),
	 * 150 uses, 8.0 mining speed, +3 attack damage, 10 enchantability. Repairs with a real {@link #ingotVoid}
	 * now, matching the true original exactly - the void-metal chain closed a few chunks ago (see
	 * {@code ingotVoid}'s own doc comment); this repair ingredient and the whole Void tool/armor set's own
	 * recipes (previously shipped with an {@code ingotThaumium} placeholder) both got fixed to the real
	 * ingredient in the same pass. See {@link thaumcraft.common.items.tools.ItemVoidSword}'s own doc comment
	 * for this item's other simplifications. */
	private static final Tier TIER_VOID = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 150, 8.0F, 3.0F, 10,
			() -> Ingredient.of(ItemsTC.ingotVoid));

	/** See {@link thaumcraft.common.items.tools.ItemVoidSword}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemVoidSword> voidSword = ITEMS.registerItem("void_sword",
			props -> new thaumcraft.common.items.tools.ItemVoidSword(TIER_VOID, props.attributes(SwordItem.createAttributes(TIER_VOID, 3, -2.4F))));

	/** The rest of the real Void tool set - {@link thaumcraft.common.items.tools.ItemVoidPickaxe}'s own doc
	 * comment covers the shared mechanics (self-repair, on-hit Weakness, +1 warp) every one of these has. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemVoidPickaxe> voidPick = ITEMS.registerItem("void_pick",
			props -> new thaumcraft.common.items.tools.ItemVoidPickaxe(TIER_VOID, props.attributes(PickaxeItem.createAttributes(TIER_VOID, 1.0F, -2.8F))));

	public static final DeferredItem<thaumcraft.common.items.tools.ItemVoidAxe> voidAxe = ITEMS.registerItem("void_axe",
			props -> new thaumcraft.common.items.tools.ItemVoidAxe(TIER_VOID, props.attributes(AxeItem.createAttributes(TIER_VOID, 5.0F, -3.0F))));

	public static final DeferredItem<thaumcraft.common.items.tools.ItemVoidShovel> voidShovel = ITEMS.registerItem("void_shovel",
			props -> new thaumcraft.common.items.tools.ItemVoidShovel(TIER_VOID, props.attributes(ShovelItem.createAttributes(TIER_VOID, 1.5F, -3.0F))));

	public static final DeferredItem<thaumcraft.common.items.tools.ItemVoidHoe> voidHoe = ITEMS.registerItem("void_hoe",
			props -> new thaumcraft.common.items.tools.ItemVoidHoe(TIER_VOID, props.attributes(HoeItem.createAttributes(TIER_VOID, -3.0F, 0.0F))));

	/** {@code ItemCrimsonBlade.toolMatCrimsonVoid} from the original ("CVOID": 200 uses, 8.0 mining speed,
	 * +3.5 attack damage, 20 enchantability) - repairs with a real {@link #ingotVoid} now too, same fix as
	 * {@link #TIER_VOID} above. */
	private static final Tier TIER_CRIMSON_BLADE = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 200, 8.0F, 3.5F, 20,
			() -> Ingredient.of(ItemsTC.ingotVoid));

	/** See {@link thaumcraft.common.items.tools.ItemCrimsonBlade}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemCrimsonBlade> crimsonBlade = ITEMS.registerItem("crimson_blade",
			props -> new thaumcraft.common.items.tools.ItemCrimsonBlade(TIER_CRIMSON_BLADE,
					props.attributes(SwordItem.createAttributes(TIER_CRIMSON_BLADE, 3, -2.4F)).rarity(net.minecraft.world.item.Rarity.EPIC)));

	/** See {@link thaumcraft.common.items.tools.ItemGrappleGun}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemGrappleGun> grappleGun =
			ITEMS.registerItem("grapple_gun", thaumcraft.common.items.tools.ItemGrappleGun::new);

	/** {@code ItemPrimalCrusher.material} ("PRIMALVOID") from the original: 500 uses, 8.0 speed, +4.0 attack
	 * damage, 20 enchantability - repairs with a real {@link #ingotVoid} now too, matching {@link #TIER_VOID}/
	 * {@link #TIER_CRIMSON_BLADE} above exactly (this one was stale - both of those already got the real
	 * fix when the void-metal chain closed, this tier's own doc comment just never got updated to match). */
	private static final Tier TIER_PRIMAL = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 500, 8.0F, 4.0F, 20,
			() -> Ingredient.of(ItemsTC.ingotVoid));

	/** See {@link thaumcraft.common.items.tools.ItemPrimalCrusher}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemPrimalCrusher> primalCrusher = ITEMS.registerItem("primal_crusher",
			props -> new thaumcraft.common.items.tools.ItemPrimalCrusher(TIER_PRIMAL,
					props.attributes(net.minecraft.world.item.DiggerItem.createAttributes(TIER_PRIMAL, 3.5F, -2.8F)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	/** See {@link thaumcraft.common.items.tools.ItemSanityChecker}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemSanityChecker> sanityChecker =
			ITEMS.registerItem("sanity_checker", thaumcraft.common.items.tools.ItemSanityChecker::new);

	/** See {@link thaumcraft.common.items.tools.ItemHandMirror}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemHandMirror> handMirror =
			ITEMS.registerItem("hand_mirror", thaumcraft.common.items.tools.ItemHandMirror::new);

	/** {@code ThaumcraftMaterials.ARMORMAT_THAUMIUM}, via {@link ThaumcraftArmorMaterials#THAUMIUM} - see that
	 * class for why armor needs a real registry entry instead of a plain {@code Tier}-style object. Durability
	 * is {@code Type.getDurability(25)} (the original's durability multiplier), same convention vanilla's own
	 * armor registration uses (e.g. {@code DIAMOND_HELMET} passes 33). No custom Item subclass needed -
	 * {@code ArmorItem} auto-derives its ARMOR/ARMOR_TOUGHNESS/KNOCKBACK_RESISTANCE attributes and
	 * repair-ingredient check straight from the material. */
	public static final DeferredItem<BlockItem> visBattery =
			ITEMS.registerSimpleBlockItem("vis_battery", BlocksTC.visBattery);

	public static final DeferredItem<BlockItem> dioptra =
			ITEMS.registerSimpleBlockItem("dioptra", BlocksTC.dioptra);

	/** A simple crafted component (gold + silverwood plank) reused by several still-unbuilt devices in the
	 * original (Tube Filter, Alembic, Smelter Vent, Condenser Lattice) - worth porting now as a real base
	 * item rather than improvising a one-off substitute for each device separately later. */
	public static final DeferredItem<Item> filter =
			ITEMS.registerSimpleItem("filter");

	public static final DeferredItem<BlockItem> condenser =
			ITEMS.registerSimpleBlockItem("condenser", BlocksTC.condenser);

	public static final DeferredItem<BlockItem> condenserLattice =
			ITEMS.registerSimpleBlockItem("condenser_lattice", BlocksTC.condenserLattice);

	public static final DeferredItem<BlockItem> potionSprayer =
			ITEMS.registerSimpleBlockItem("potion_sprayer", BlocksTC.potionSprayer);

	public static final DeferredItem<BlockItem> voidSiphon =
			ITEMS.registerSimpleBlockItem("void_siphon", BlocksTC.voidSiphon);

	public static final DeferredItem<BlockItem> patternCrafter =
			ITEMS.registerSimpleBlockItem("pattern_crafter", BlocksTC.patternCrafter);

	public static final DeferredItem<BlockItem> thaumatorium =
			ITEMS.registerSimpleBlockItem("thaumatorium", BlocksTC.thaumatorium);

	public static final DeferredItem<BlockItem> thaumatoriumTop =
			ITEMS.registerSimpleBlockItem("thaumatorium_top", BlocksTC.thaumatoriumTop);

	public static final DeferredItem<BlockItem> metalAlchemical =
			ITEMS.registerSimpleBlockItem("metal_alchemical", BlocksTC.metalAlchemical);

	/** Real recipe now (see {@code data/thaumcraft/recipe/metal_alchemical_advanced.json}) - the true
	 * original's own real "AdvAlchemyConstruct" (metalAlchemical + plate_void + a real primordialPearl). */
	public static final DeferredItem<BlockItem> metalAlchemicalAdvanced =
			ITEMS.registerSimpleBlockItem("metal_alchemical_advanced", BlocksTC.metalAlchemicalAdvanced);

	public static final DeferredItem<BlockItem> brainBox =
			ITEMS.registerSimpleBlockItem("brain_box", BlocksTC.brainBox);

	public static final DeferredItem<BlockItem> pavingStoneTravel =
			ITEMS.registerSimpleBlockItem("paving_stone_travel", BlocksTC.pavingStoneTravel);

	/** No item for {@code BlocksTC.barrier} - matches the original's own real {@code getSubBlocks} (does
	 * nothing) and {@code getPickBlock}/{@code getItemDropped} (both empty) - it's never player-placed or
	 * player-obtained, only ever auto-placed/healed by a real {@code TileBarrierStone}. */
	public static final DeferredItem<BlockItem> pavingStoneBarrier =
			ITEMS.registerSimpleBlockItem("paving_stone_barrier", BlocksTC.pavingStoneBarrier);

	/** A Crucible byproduct (rotten flesh + 1 fire) - the precursor for {@link BlocksTC#candles}. */
	public static final DeferredItem<Item> tallow =
			ITEMS.registerSimpleItem("tallow");

	/** A small essentia bottle, filled/emptied at a Warded Jar - see
	 * {@link thaumcraft.common.items.consumables.ItemPhial}'s own doc comment for what's simplified
	 * (no Alembic fill path yet). */
	public static final DeferredItem<thaumcraft.common.items.consumables.ItemPhial> phial = ITEMS.registerItem("phial",
			props -> new thaumcraft.common.items.consumables.ItemPhial(props.stacksTo(64)));

	/** Applies to a Warded Jar to lock it to one aspect - see
	 * {@link thaumcraft.common.items.consumables.ItemLabel}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.consumables.ItemLabel> label = ITEMS.registerItem("label",
			props -> new thaumcraft.common.items.consumables.ItemLabel(props.stacksTo(64)));

	/** A random vanilla Zombie kill has a chance to drop one - see
	 * {@link thaumcraft.common.items.consumables.ItemZombieBrain}'s own doc comment for the warp risk/reward. */
	public static final DeferredItem<BlockItem> arcaneWorkbenchCharger =
			ITEMS.registerSimpleBlockItem("arcane_workbench_charger", BlocksTC.arcaneWorkbenchCharger);

	public static final DeferredItem<BlockItem> fleshBlock =
			ITEMS.registerSimpleBlockItem("flesh_block", BlocksTC.fleshBlock);

	/** Hold right-click to consume and clear Warp - see
	 * {@link thaumcraft.common.items.consumables.ItemSanitySoap}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.consumables.ItemSanitySoap> sanitySoap = ITEMS.registerItem("sanity_soap",
			thaumcraft.common.items.consumables.ItemSanitySoap::new);

	/** Right-click to spill random loot - see {@link thaumcraft.common.items.curios.ItemLootBag}'s own doc
	 * comment. A recently-hit vanilla hostile mob has a chance to drop one, tiered by looting level. */
	public static final DeferredItem<thaumcraft.common.items.curios.ItemLootBag> lootBagCommon = ITEMS.registerItem("loot_bag_common",
			props -> new thaumcraft.common.items.curios.ItemLootBag(props.stacksTo(16).rarity(net.minecraft.world.item.Rarity.COMMON),
					ResourceLocation.fromNamespaceAndPath("thaumcraft", "gameplay/loot_bag_common")));

	public static final DeferredItem<thaumcraft.common.items.curios.ItemLootBag> lootBagUncommon = ITEMS.registerItem("loot_bag_uncommon",
			props -> new thaumcraft.common.items.curios.ItemLootBag(props.stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON),
					ResourceLocation.fromNamespaceAndPath("thaumcraft", "gameplay/loot_bag_uncommon")));

	public static final DeferredItem<thaumcraft.common.items.curios.ItemLootBag> lootBagRare = ITEMS.registerItem("loot_bag_rare",
			props -> new thaumcraft.common.items.curios.ItemLootBag(props.stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE),
					ResourceLocation.fromNamespaceAndPath("thaumcraft", "gameplay/loot_bag_rare")));

	public static final DeferredItem<thaumcraft.common.items.consumables.ItemZombieBrain> brain = ITEMS.registerItem("brain",
			props -> new thaumcraft.common.items.consumables.ItemZombieBrain(props.food(
					new net.minecraft.world.food.FoodProperties.Builder()
							.nutrition(4).saturationModifier(0.2f)
							.effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HUNGER, 30, 0), 0.8f)
							.build())));

	/** Real Infernal Furnace smelting-bonus outputs (raw meat -> a chance of the matching chunk, 33% - see
	 * {@code TileInfernalFurnace#SMELTING_BONUSES}'s own doc comment) - the true original's own single
	 * metadata-variant {@code ItemsTC.chunks} field, split into this port's own established per-variant
	 * fields, same convention {@code nuggetBrass}/etc. already set. 1 nutrition / 0.3 raw saturation
	 * (modifier {@code 0.3/(1*2)}), not always edible, matching the true original's own real
	 * {@code ItemChunksEdible} constructor exactly. Real ingredient for {@link #tripleMeatTreat}. */
	public static final DeferredItem<Item> chunkBeef = ITEMS.registerItem("chunk_beef",
			props -> new Item(props.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.15f).build())));
	public static final DeferredItem<Item> chunkChicken = ITEMS.registerItem("chunk_chicken",
			props -> new Item(props.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.15f).build())));
	public static final DeferredItem<Item> chunkPork = ITEMS.registerItem("chunk_pork",
			props -> new Item(props.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.15f).build())));
	public static final DeferredItem<Item> chunkFish = ITEMS.registerItem("chunk_fish",
			props -> new Item(props.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.15f).build())));
	public static final DeferredItem<Item> chunkRabbit = ITEMS.registerItem("chunk_rabbit",
			props -> new Item(props.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.15f).build())));
	public static final DeferredItem<Item> chunkMutton = ITEMS.registerItem("chunk_mutton",
			props -> new Item(props.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.15f).build())));

	/** See {@link thaumcraft.common.lib.crafting.RecipeTripleMeatTreat}'s own doc comment - a real custom
	 * {@code Recipe} (3 distinct chunk types, no duplicates, + sugar), not expressible as a plain vanilla
	 * shapeless recipe. 6 nutrition / 0.8 raw saturation (modifier {@code 0.8/(6*2)}), always edible, 66%
	 * chance of Regeneration (100 ticks, amplifier 0) on eat - matching the true original's own real
	 * {@code ItemTripleMeatTreat} exactly. */
	public static final DeferredItem<Item> tripleMeatTreat = ITEMS.registerItem("triple_meat_treat",
			props -> new Item(props.food(new net.minecraft.world.food.FoodProperties.Builder()
					.nutrition(6).saturationModifier(0.0667f).alwaysEdible()
					.effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100, 0), 0.66f)
					.build())));

	public static final java.util.Map<net.minecraft.world.item.DyeColor, DeferredItem<BlockItem>> candles = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
	static {
		BlocksTC.candles.forEach((dye, block) -> candles.put(dye, ITEMS.registerSimpleBlockItem("candle_" + BlocksTC.candleDyeName(dye), block)));
	}

	/** See {@link BlocksTC#nitor}'s own doc comment. */
	public static final java.util.Map<net.minecraft.world.item.DyeColor, DeferredItem<BlockItem>> nitor = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
	static {
		BlocksTC.nitor.forEach((dye, block) -> nitor.put(dye, ITEMS.registerSimpleBlockItem("nitor_" + BlocksTC.candleDyeName(dye), block)));
	}

	public static final DeferredItem<Item> thaumiumHelm = ITEMS.registerItem("thaumium_helm",
			props -> new ArmorItem(ThaumcraftArmorMaterials.THAUMIUM, ArmorItem.Type.HELMET,
					props.durability(ArmorItem.Type.HELMET.getDurability(25))));

	public static final DeferredItem<Item> thaumiumChest = ITEMS.registerItem("thaumium_chest",
			props -> new ArmorItem(ThaumcraftArmorMaterials.THAUMIUM, ArmorItem.Type.CHESTPLATE,
					props.durability(ArmorItem.Type.CHESTPLATE.getDurability(25))));

	public static final DeferredItem<Item> thaumiumLegs = ITEMS.registerItem("thaumium_legs",
			props -> new ArmorItem(ThaumcraftArmorMaterials.THAUMIUM, ArmorItem.Type.LEGGINGS,
					props.durability(ArmorItem.Type.LEGGINGS.getDurability(25))));

	public static final DeferredItem<Item> thaumiumBoots = ITEMS.registerItem("thaumium_boots",
			props -> new ArmorItem(ThaumcraftArmorMaterials.THAUMIUM, ArmorItem.Type.BOOTS,
					props.durability(ArmorItem.Type.BOOTS.getDurability(25))));

	public static final DeferredItem<BlockItem> pedestal =
			ITEMS.registerSimpleBlockItem("pedestal", BlocksTC.pedestal);

	/** See {@link BlocksTC#cultistRitualAltar}'s own doc comment. */
	public static final DeferredItem<BlockItem> cultistRitualAltar =
			ITEMS.registerSimpleBlockItem("cultist_ritual_altar", BlocksTC.cultistRitualAltar);

	/** See {@link BlocksTC#bannerCrimsonCult}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.blocks.misc.BannerBlockItem> bannerCrimsonCult =
			ITEMS.register("banner_crimson_cult", () -> new thaumcraft.common.blocks.misc.BannerBlockItem(BlocksTC.bannerCrimsonCult.get(), new Item.Properties()));

	/** See {@link BlocksTC#banners}'s own doc comment. Placed through {@code BannerBlockItem} so they orient
	 * (floor: to the player's yaw, wall: to the wall) as the original's banner item did. */
	public static final java.util.Map<net.minecraft.world.item.DyeColor, DeferredItem<thaumcraft.common.blocks.misc.BannerBlockItem>> banners = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
	static {
		BlocksTC.banners.forEach((dye, block) -> banners.put(dye, ITEMS.register("banner_" + BlocksTC.candleDyeName(dye),
				() -> new thaumcraft.common.blocks.misc.BannerBlockItem(block.get(), new Item.Properties()))));
	}

	/** See {@link BlocksTC#lootCrateCommon}/{@link thaumcraft.common.blocks.misc.BlockLoot}'s own doc
	 * comment. */
	public static final DeferredItem<BlockItem> lootCrateCommon =
			ITEMS.registerSimpleBlockItem("loot_crate_common", BlocksTC.lootCrateCommon);

	public static final DeferredItem<BlockItem> lootCrateUncommon =
			ITEMS.registerSimpleBlockItem("loot_crate_uncommon", BlocksTC.lootCrateUncommon);

	public static final DeferredItem<BlockItem> lootCrateRare =
			ITEMS.registerSimpleBlockItem("loot_crate_rare", BlocksTC.lootCrateRare);

	/** See {@link BlocksTC#lootUrnCommon}/{@link thaumcraft.common.blocks.misc.BlockLoot}'s own doc
	 * comment. */
	public static final DeferredItem<BlockItem> lootUrnCommon =
			ITEMS.registerSimpleBlockItem("loot_urn_common", BlocksTC.lootUrnCommon);

	public static final DeferredItem<BlockItem> lootUrnUncommon =
			ITEMS.registerSimpleBlockItem("loot_urn_uncommon", BlocksTC.lootUrnUncommon);

	public static final DeferredItem<BlockItem> lootUrnRare =
			ITEMS.registerSimpleBlockItem("loot_urn_rare", BlocksTC.lootUrnRare);

	public static final DeferredItem<BlockItem> infusionMatrix =
			ITEMS.registerSimpleBlockItem("infusion_matrix", BlocksTC.infusionMatrix);

	public static final DeferredItem<BlockItem> pillarArcane =
			ITEMS.registerSimpleBlockItem("pillar_arcane", BlocksTC.pillarArcane);

	public static final DeferredItem<BlockItem> pillarAncient =
			ITEMS.registerSimpleBlockItem("pillar_ancient", BlocksTC.pillarAncient);

	public static final DeferredItem<BlockItem> pillarEldritch =
			ITEMS.registerSimpleBlockItem("pillar_eldritch", BlocksTC.pillarEldritch);

	public static final DeferredItem<BlockItem> matrixSpeed =
			ITEMS.registerSimpleBlockItem("matrix_speed", BlocksTC.matrixSpeed);

	public static final DeferredItem<BlockItem> matrixCost =
			ITEMS.registerSimpleBlockItem("matrix_cost", BlocksTC.matrixCost);

	public static final DeferredItem<BlockItem> stabilizer =
			ITEMS.registerSimpleBlockItem("stabilizer", BlocksTC.stabilizer);

	public static final DeferredItem<BlockItem> inlay =
			ITEMS.registerSimpleBlockItem("inlay", BlocksTC.inlay);

	public static final DeferredItem<BlockItem> alembic =
			ITEMS.registerSimpleBlockItem("alembic", BlocksTC.alembic);

	public static final DeferredItem<BlockItem> smelter =
			ITEMS.registerSimpleBlockItem("smelter", BlocksTC.smelter);

	/** Real recipe now (see {@code data/thaumcraft/recipe/essentia_smelter_thaumium.json}). */
	public static final DeferredItem<BlockItem> smelterThaumium =
			ITEMS.registerSimpleBlockItem("smelter_thaumium", BlocksTC.smelterThaumium);

	/** Real recipe now (see {@code data/thaumcraft/recipe/essentia_smelter_void.json}). */
	public static final DeferredItem<BlockItem> smelterVoid =
			ITEMS.registerSimpleBlockItem("smelter_void", BlocksTC.smelterVoid);

	public static final DeferredItem<BlockItem> bellows =
			ITEMS.registerSimpleBlockItem("bellows", BlocksTC.bellows);

	public static final DeferredItem<BlockItem> smelterAux =
			ITEMS.registerSimpleBlockItem("smelter_aux", BlocksTC.smelterAux);

	public static final DeferredItem<BlockItem> smelterVent =
			ITEMS.registerSimpleBlockItem("smelter_vent", BlocksTC.smelterVent);

	/** See {@link thaumcraft.common.blocks.devices.BlockMirrorItem}'s own doc comment - a custom
	 * {@code BlockItem} rather than {@code registerSimpleBlockItem}, since it doubles as its own linking
	 * tool. */
	public static final DeferredItem<thaumcraft.common.blocks.devices.BlockMirrorItem> mirror =
			ITEMS.registerItem("mirror", props -> new thaumcraft.common.blocks.devices.BlockMirrorItem(BlocksTC.mirror.get(), props));

	/** See {@link thaumcraft.common.blocks.devices.BlockMirrorEssentiaItem}'s own doc comment - {@link #mirror}'s
	 * essentia-transport sibling, same self-linking pattern. */
	public static final DeferredItem<thaumcraft.common.blocks.devices.BlockMirrorEssentiaItem> mirrorEssentia =
			ITEMS.registerItem("mirror_essentia", props -> new thaumcraft.common.blocks.devices.BlockMirrorEssentiaItem(BlocksTC.mirrorEssentia.get(), props));

	public static final DeferredItem<Item> fabric =
			ITEMS.registerSimpleItem("fabric");

	/** See {@link thaumcraft.common.items.armor.ItemBootsTraveller}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.armor.ItemBootsTraveller> travellerBoots =
			ITEMS.registerItem("traveller_boots", props -> new thaumcraft.common.items.armor.ItemBootsTraveller(
					props.durability(350).rarity(net.minecraft.world.item.Rarity.RARE)));

	/** See {@link thaumcraft.common.items.armor.ItemRobeArmor}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.armor.ItemRobeArmor> clothChest = ITEMS.registerItem("cloth_chest",
			props -> new thaumcraft.common.items.armor.ItemRobeArmor(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
					props.durability(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE.getDurability(25))));

	public static final DeferredItem<thaumcraft.common.items.armor.ItemRobeArmor> clothLegs = ITEMS.registerItem("cloth_legs",
			props -> new thaumcraft.common.items.armor.ItemRobeArmor(net.minecraft.world.item.ArmorItem.Type.LEGGINGS,
					props.durability(net.minecraft.world.item.ArmorItem.Type.LEGGINGS.getDurability(25))));

	public static final DeferredItem<BlockItem> cinderpearl =
			ITEMS.registerSimpleBlockItem("cinderpearl", BlocksTC.cinderpearl);

	public static final DeferredItem<BlockItem> shimmerleaf =
			ITEMS.registerSimpleBlockItem("shimmerleaf", BlocksTC.shimmerleaf);

	public static final DeferredItem<BlockItem> vishroom =
			ITEMS.registerSimpleBlockItem("vishroom", BlocksTC.vishroom);

	public static final DeferredItem<thaumcraft.common.items.armor.ItemRobeArmor> clothBoots = ITEMS.registerItem("cloth_boots",
			props -> new thaumcraft.common.items.armor.ItemRobeArmor(net.minecraft.world.item.ArmorItem.Type.BOOTS,
					props.durability(net.minecraft.world.item.ArmorItem.Type.BOOTS.getDurability(25))));

	/** Diagnostic tool - see {@link thaumcraft.common.items.tools.ItemResonator}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemResonator> resonator =
			ITEMS.registerItem("resonator", props -> new thaumcraft.common.items.tools.ItemResonator(props.rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	/** The Crimson Plate armor set - {@link thaumcraft.common.entities.monster.cult.EntityCultistKnight}'s
	 * real equipment drops (5% drop chance each, set on the entity itself, matching the original). Plain
	 * {@link ArmorItem} - no custom subclass needed, same as {@link #thaumiumHelm} and siblings. See
	 * {@link ThaumcraftArmorMaterials#CULTIST_PLATE} for the stat/texture rationale. */
	public static final DeferredItem<Item> crimsonPlateHelm = ITEMS.registerItem("crimson_plate_helm",
			props -> new ArmorItem(ThaumcraftArmorMaterials.CULTIST_PLATE, ArmorItem.Type.HELMET,
					props.durability(ArmorItem.Type.HELMET.getDurability(18)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	public static final DeferredItem<Item> crimsonPlateChest = ITEMS.registerItem("crimson_plate_chest",
			props -> new ArmorItem(ThaumcraftArmorMaterials.CULTIST_PLATE, ArmorItem.Type.CHESTPLATE,
					props.durability(ArmorItem.Type.CHESTPLATE.getDurability(18)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	public static final DeferredItem<Item> crimsonPlateLegs = ITEMS.registerItem("crimson_plate_legs",
			props -> new ArmorItem(ThaumcraftArmorMaterials.CULTIST_PLATE, ArmorItem.Type.LEGGINGS,
					props.durability(ArmorItem.Type.LEGGINGS.getDurability(18)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	/** See {@link thaumcraft.common.items.armor.ItemCultistBoots}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.armor.ItemCultistBoots> crimsonBoots = ITEMS.registerItem("crimson_boots",
			props -> new thaumcraft.common.items.armor.ItemCultistBoots(
					props.durability(ArmorItem.Type.BOOTS.getDurability(15)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	/** The Crimson Praetor armor set - {@link thaumcraft.common.entities.monster.boss.EntityCultistLeader}'s
	 * own equipment (always worn). Plain {@link ArmorItem}, same shape as {@link #crimsonPlateHelm} and
	 * siblings. See {@link ThaumcraftArmorMaterials#CULTIST_PRAETOR} for the stat/texture rationale. */
	public static final DeferredItem<Item> crimsonPraetorHelm = ITEMS.registerItem("crimson_praetor_helm",
			props -> new ArmorItem(ThaumcraftArmorMaterials.CULTIST_PRAETOR, ArmorItem.Type.HELMET,
					props.durability(ArmorItem.Type.HELMET.getDurability(30)).rarity(net.minecraft.world.item.Rarity.RARE)));

	public static final DeferredItem<Item> crimsonPraetorChest = ITEMS.registerItem("crimson_praetor_chest",
			props -> new ArmorItem(ThaumcraftArmorMaterials.CULTIST_PRAETOR, ArmorItem.Type.CHESTPLATE,
					props.durability(ArmorItem.Type.CHESTPLATE.getDurability(30)).rarity(net.minecraft.world.item.Rarity.RARE)));

	public static final DeferredItem<Item> crimsonPraetorLegs = ITEMS.registerItem("crimson_praetor_legs",
			props -> new ArmorItem(ThaumcraftArmorMaterials.CULTIST_PRAETOR, ArmorItem.Type.LEGGINGS,
					props.durability(ArmorItem.Type.LEGGINGS.getDurability(30)).rarity(net.minecraft.world.item.Rarity.RARE)));

	/** The Crimson Robe armor set - {@link thaumcraft.common.entities.monster.cult.EntityCultistCleric}'s
	 * real equipment (always worn, matching the original's own {@code setLoot}). Plain {@link ArmorItem},
	 * same shape as {@link #crimsonPlateHelm} and siblings. See {@link ThaumcraftArmorMaterials#CULTIST_ROBE}
	 * for the stat/texture rationale. */
	public static final DeferredItem<Item> crimsonRobeHelm = ITEMS.registerItem("crimson_robe_helm",
			props -> new ArmorItem(ThaumcraftArmorMaterials.CULTIST_ROBE, ArmorItem.Type.HELMET,
					props.durability(ArmorItem.Type.HELMET.getDurability(17)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	public static final DeferredItem<Item> crimsonRobeChest = ITEMS.registerItem("crimson_robe_chest",
			props -> new ArmorItem(ThaumcraftArmorMaterials.CULTIST_ROBE, ArmorItem.Type.CHESTPLATE,
					props.durability(ArmorItem.Type.CHESTPLATE.getDurability(17)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	public static final DeferredItem<Item> crimsonRobeLegs = ITEMS.registerItem("crimson_robe_legs",
			props -> new ArmorItem(ThaumcraftArmorMaterials.CULTIST_ROBE, ArmorItem.Type.LEGGINGS,
					props.durability(ArmorItem.Type.LEGGINGS.getDurability(17)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	/** See {@link BlocksTC#taintFibre}'s own doc comment. */
	public static final DeferredItem<BlockItem> taintFibre =
			ITEMS.registerSimpleBlockItem("taint_fibre", BlocksTC.taintFibre);

	/** See {@link BlocksTC#taintRock}/{@link thaumcraft.common.blocks.world.taint.BlockTaint}'s own doc
	 * comment. */
	public static final DeferredItem<BlockItem> taintRock =
			ITEMS.registerSimpleBlockItem("taint_rock", BlocksTC.taintRock);

	public static final DeferredItem<BlockItem> taintSoil =
			ITEMS.registerSimpleBlockItem("taint_soil", BlocksTC.taintSoil);

	public static final DeferredItem<BlockItem> taintCrust =
			ITEMS.registerSimpleBlockItem("taint_crust", BlocksTC.taintCrust);

	public static final DeferredItem<BlockItem> taintGeyser =
			ITEMS.registerSimpleBlockItem("taint_geyser", BlocksTC.taintGeyser);

	public static final DeferredItem<BlockItem> taintFeature =
			ITEMS.registerSimpleBlockItem("taint_feature", BlocksTC.taintFeature);

	public static final DeferredItem<BlockItem> taintLog =
			ITEMS.registerSimpleBlockItem("taint_log", BlocksTC.taintLog);

	public static final DeferredItem<BlockItem> fluxGoo =
			ITEMS.registerSimpleBlockItem("flux_goo", BlocksTC.fluxGoo);

	public static final DeferredItem<BlockItem> spa =
			ITEMS.registerSimpleBlockItem("spa", BlocksTC.spa);

	/** See {@link thaumcraft.common.items.consumables.ItemBathSalts}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.consumables.ItemBathSalts> bathSalts =
			ITEMS.registerItem("bath_salts", thaumcraft.common.items.consumables.ItemBathSalts::new);

	public static final DeferredItem<BlockItem> stonePorous =
			ITEMS.registerSimpleBlockItem("stone_porous", BlocksTC.stonePorous);

	public static final DeferredItem<BlockItem> activatorRail =
			ITEMS.registerSimpleBlockItem("activator_rail", BlocksTC.activatorRail);

	/** See {@link thaumcraft.common.entities.construct.ItemTurretPlacer}'s own doc comment for why these are
	 * plain, separate items rather than the original's own 3-subtype {@code "turret"} item. All 3 got their
	 * true original real recipes once the golem {@code mind} item family did too (see
	 * {@code data/thaumcraft/recipe/turret_placer_basic.json}/{@code _advanced.json}/{@code _bore.json}) - a
	 * real, confirmed gap (zero acquisition path of any kind) for the whole session before that. */
	public static final DeferredItem<thaumcraft.common.entities.construct.ItemTurretPlacer> turretPlacerBasic =
			ITEMS.registerItem("turret_placer_basic",
					props -> new thaumcraft.common.entities.construct.ItemTurretPlacer(props, () -> thaumcraft.common.registry.ThaumcraftEntities.TURRET_CROSSBOW.get()));
	public static final DeferredItem<thaumcraft.common.entities.construct.ItemTurretPlacer> turretPlacerAdvanced =
			ITEMS.registerItem("turret_placer_advanced",
					props -> new thaumcraft.common.entities.construct.ItemTurretPlacer(props, () -> thaumcraft.common.registry.ThaumcraftEntities.TURRET_CROSSBOW_ADVANCED.get()));
	public static final DeferredItem<thaumcraft.common.entities.construct.ItemTurretPlacer> turretPlacerBore =
			ITEMS.registerItem("turret_placer_bore",
					props -> new thaumcraft.common.entities.construct.ItemTurretPlacer(props, () -> thaumcraft.common.registry.ThaumcraftEntities.ARCANE_BORE.get()));

	/** See {@link thaumcraft.api.items.WornGearHelper}'s own doc comment for why these 7 "bauble" items - the
	 * original's real equipment-slot trinkets - are plain items here (hotbar/offhand instead of a dedicated
	 * bauble slot, per the user's explicit "move bauble items to normal item slots... unfuck it later" call).
	 * The 6 always-blank trinkets from the original's {@code ItemBaubles} (mundane trio + fancy trio) are
	 * shipped as real, obtainable components now even though nothing crafts with them yet - same "ship the
	 * item, the recipe chain is real follow-up work" precedent as {@link #amber} and siblings. */
	public static final DeferredItem<Item> amuletMundane = ITEMS.registerSimpleItem("amulet_mundane");
	public static final DeferredItem<Item> ringMundane = ITEMS.registerSimpleItem("ring_mundane");
	public static final DeferredItem<Item> girdleMundane = ITEMS.registerSimpleItem("girdle_mundane");
	public static final DeferredItem<Item> amuletFancy = ITEMS.registerItem("amulet_fancy",
			props -> new Item(props.rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
	public static final DeferredItem<Item> ringFancy = ITEMS.registerItem("ring_fancy",
			props -> new Item(props.rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
	public static final DeferredItem<Item> girdleFancy = ITEMS.registerItem("girdle_fancy",
			props -> new Item(props.rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	/** The one real subtype out of the original's 7-way {@code ItemBaubles} - see
	 * {@link thaumcraft.common.items.baubles.ItemRingApprentice}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.baubles.ItemRingApprentice> ringApprentice =
			ITEMS.registerItem("ring_apprentice", thaumcraft.common.items.baubles.ItemRingApprentice::new);

	/** See {@link thaumcraft.common.items.baubles.ItemAmuletVis}'s own doc comment. Two variants: "found"
	 * (slower recharge cadence, uncommon) and "crafted" (faster, rare) - same real difference the original's
	 * two damage-value subtypes had. */
	public static final DeferredItem<thaumcraft.common.items.baubles.ItemAmuletVis> amuletVisFound =
			ITEMS.registerItem("amulet_vis", props -> new thaumcraft.common.items.baubles.ItemAmuletVis(props, 40, false));
	public static final DeferredItem<thaumcraft.common.items.baubles.ItemAmuletVis> amuletVisCrafted =
			ITEMS.registerItem("amulet_vis_stone", props -> new thaumcraft.common.items.baubles.ItemAmuletVis(props, 5, true));

	/** See {@link thaumcraft.common.items.baubles.ItemCharmUndying}'s own doc comment - the real
	 * cancel-death/heal/Regen/Absorption effect lives in {@link thaumcraft.common.lib.events.WornGearEvents#onDeath}. */
	public static final DeferredItem<thaumcraft.common.items.baubles.ItemCharmUndying> charmUndying =
			ITEMS.registerItem("charm_undying", thaumcraft.common.items.baubles.ItemCharmUndying::new);

	/** See {@link thaumcraft.common.items.baubles.ItemCloudRing}'s own doc comment for the documented
	 * double-jump cut; the fall-damage reduction it DOES keep lives in
	 * {@link thaumcraft.common.lib.events.WornGearEvents#fallDamage}. */
	public static final DeferredItem<thaumcraft.common.items.baubles.ItemCloudRing> cloudRing =
			ITEMS.registerItem("cloud_ring", thaumcraft.common.items.baubles.ItemCloudRing::new);

	/** See {@link thaumcraft.common.items.baubles.ItemCuriosityBand}'s own doc comment - its real XP-split
	 * effect lives in {@link thaumcraft.common.lib.events.WornGearEvents#xpPickup}. */
	public static final DeferredItem<thaumcraft.common.items.baubles.ItemCuriosityBand> curiosityBand =
			ITEMS.registerItem("curiosity_band", thaumcraft.common.items.baubles.ItemCuriosityBand::new);

	/** See {@link thaumcraft.common.items.baubles.ItemVerdantCharm}'s own doc comment. 3 variants (base/life/
	 * sustain), replacing the original's single NBT-"type"-driven item. */
	public static final DeferredItem<thaumcraft.common.items.baubles.ItemVerdantCharm> verdantCharm =
			ITEMS.registerItem("verdant_charm", props -> new thaumcraft.common.items.baubles.ItemVerdantCharm(props, thaumcraft.common.items.baubles.ItemVerdantCharm.TYPE_BASE));
	public static final DeferredItem<thaumcraft.common.items.baubles.ItemVerdantCharm> verdantCharmLife =
			ITEMS.registerItem("verdant_charm_life", props -> new thaumcraft.common.items.baubles.ItemVerdantCharm(props, thaumcraft.common.items.baubles.ItemVerdantCharm.TYPE_LIFE));
	public static final DeferredItem<thaumcraft.common.items.baubles.ItemVerdantCharm> verdantCharmSustain =
			ITEMS.registerItem("verdant_charm_sustain", props -> new thaumcraft.common.items.baubles.ItemVerdantCharm(props, thaumcraft.common.items.baubles.ItemVerdantCharm.TYPE_SUSTAIN));

	/** See {@link thaumcraft.common.items.baubles.ItemVoidseerCharm}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.baubles.ItemVoidseerCharm> voidseerCharm =
			ITEMS.registerItem("voidseer_charm", thaumcraft.common.items.baubles.ItemVoidseerCharm::new);

	/** {@code ThaumcraftMaterials.TOOLMAT_ELEMENTAL} from the original ("THAUMIUM_ELEMENTAL": harvest level 3
	 * - same tag as {@link #TIER_THAUMIUM}, 1500 uses, 9.0 mining speed, +3 attack damage, 18 enchantability,
	 * repaired with a Thaumium ingot). A real upgrade path from the base Thaumium tools, crafted at the
	 * Infusion Altar - see {@code data/thaumcraft/recipe/infusion_elemental_*.json}. */
	private static final Tier TIER_ELEMENTAL = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1500, 9.0F, 3.0F, 18,
			() -> Ingredient.of(ItemsTC.ingotThaumium));

	/** See {@link thaumcraft.common.items.tools.ItemElementalAxe}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemElementalAxe> elementalAxe = ITEMS.registerItem("elemental_axe",
			props -> new thaumcraft.common.items.tools.ItemElementalAxe(TIER_ELEMENTAL, props.attributes(AxeItem.createAttributes(TIER_ELEMENTAL, 5.0F, -3.0F)).rarity(net.minecraft.world.item.Rarity.RARE)));

	/** A plain upgrade over {@link #thaumiumPick} - the original's own {@code ItemElementalPick} had no
	 * special ability of its own either, just the better {@link #TIER_ELEMENTAL} tier. */
	public static final DeferredItem<Item> elementalPick = ITEMS.registerItem("elemental_pick",
			props -> new PickaxeItem(TIER_ELEMENTAL, props.attributes(PickaxeItem.createAttributes(TIER_ELEMENTAL, 1.0F, -2.8F)).rarity(net.minecraft.world.item.Rarity.RARE)));

	/** See {@link thaumcraft.common.items.tools.ItemElementalSword}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemElementalSword> elementalSword = ITEMS.registerItem("elemental_sword",
			props -> new thaumcraft.common.items.tools.ItemElementalSword(TIER_ELEMENTAL, props.attributes(SwordItem.createAttributes(TIER_ELEMENTAL, 3, -2.4F)).rarity(net.minecraft.world.item.Rarity.RARE)));

	/** A plain upgrade over {@link #thaumiumShovel} - the original's own {@code ItemElementalShovel} real
	 * special ability (right-click to duplicate the clicked block into the surrounding 3x3 area from your
	 * own inventory, {@code IArchitect}-driven) is a real, deliberate cut, same reasoning {@link
	 * thaumcraft.common.items.casters.ItemCaster}'s own doc comment already gives for {@code IArchitect}/
	 * block-highlighting: a real, separate, not-yet-built subsystem, not part of this pass's minimal slice. */
	public static final DeferredItem<Item> elementalShovel = ITEMS.registerItem("elemental_shovel",
			props -> new ShovelItem(TIER_ELEMENTAL, props.attributes(ShovelItem.createAttributes(TIER_ELEMENTAL, 1.5F, -3.0F)).rarity(net.minecraft.world.item.Rarity.RARE)));

	/** See {@link thaumcraft.common.items.tools.ItemElementalHoe}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemElementalHoe> elementalHoe = ITEMS.registerItem("elemental_hoe",
			props -> new thaumcraft.common.items.tools.ItemElementalHoe(TIER_ELEMENTAL, props.attributes(HoeItem.createAttributes(TIER_ELEMENTAL, -3.0F, 0.0F)).rarity(net.minecraft.world.item.Rarity.RARE)));

	/** See {@link thaumcraft.common.items.armor.ItemVoidArmor}'s own doc comment - the base Void armor set,
	 * crafted at a plain crafting table (see {@code data/thaumcraft/recipe/void_helm.json} and siblings). */
	public static final DeferredItem<thaumcraft.common.items.armor.ItemVoidArmor> voidHelm = ITEMS.registerItem("void_helm",
			props -> new thaumcraft.common.items.armor.ItemVoidArmor(ThaumcraftArmorMaterials.VOID, ArmorItem.Type.HELMET,
					props.durability(ArmorItem.Type.HELMET.getDurability(10)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
	public static final DeferredItem<thaumcraft.common.items.armor.ItemVoidArmor> voidChest = ITEMS.registerItem("void_chest",
			props -> new thaumcraft.common.items.armor.ItemVoidArmor(ThaumcraftArmorMaterials.VOID, ArmorItem.Type.CHESTPLATE,
					props.durability(ArmorItem.Type.CHESTPLATE.getDurability(10)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
	public static final DeferredItem<thaumcraft.common.items.armor.ItemVoidArmor> voidLegs = ITEMS.registerItem("void_legs",
			props -> new thaumcraft.common.items.armor.ItemVoidArmor(ThaumcraftArmorMaterials.VOID, ArmorItem.Type.LEGGINGS,
					props.durability(ArmorItem.Type.LEGGINGS.getDurability(10)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
	public static final DeferredItem<thaumcraft.common.items.armor.ItemVoidArmor> voidBoots = ITEMS.registerItem("void_boots",
			props -> new thaumcraft.common.items.armor.ItemVoidArmor(ThaumcraftArmorMaterials.VOID, ArmorItem.Type.BOOTS,
					props.durability(ArmorItem.Type.BOOTS.getDurability(10)).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

	/** See {@link thaumcraft.common.items.armor.ItemVoidRobeArmor}'s own doc comment - the Void Robe upgrade
	 * tier, crafted at the Infusion Altar from a base {@link #voidHelm}/{@link #voidChest}/{@link #voidLegs}.
	 * No robe boots - the original's own recipe list never had one either. */
	public static final DeferredItem<thaumcraft.common.items.armor.ItemVoidRobeArmor> voidRobeHelm = ITEMS.registerItem("void_robe_helm",
			props -> new thaumcraft.common.items.armor.ItemVoidRobeArmor(ThaumcraftArmorMaterials.VOID_ROBE, ArmorItem.Type.HELMET,
					props.durability(ArmorItem.Type.HELMET.getDurability(18)).rarity(net.minecraft.world.item.Rarity.EPIC)));
	public static final DeferredItem<thaumcraft.common.items.armor.ItemVoidRobeArmor> voidRobeChest = ITEMS.registerItem("void_robe_chest",
			props -> new thaumcraft.common.items.armor.ItemVoidRobeArmor(ThaumcraftArmorMaterials.VOID_ROBE, ArmorItem.Type.CHESTPLATE,
					props.durability(ArmorItem.Type.CHESTPLATE.getDurability(18)).rarity(net.minecraft.world.item.Rarity.EPIC)));
	public static final DeferredItem<thaumcraft.common.items.armor.ItemVoidRobeArmor> voidRobeLegs = ITEMS.registerItem("void_robe_legs",
			props -> new thaumcraft.common.items.armor.ItemVoidRobeArmor(ThaumcraftArmorMaterials.VOID_ROBE, ArmorItem.Type.LEGGINGS,
					props.durability(ArmorItem.Type.LEGGINGS.getDurability(18)).rarity(net.minecraft.world.item.Rarity.EPIC)));

	/** See {@link thaumcraft.common.items.armor.ItemGoggles}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.armor.ItemGoggles> goggles = ITEMS.registerItem("goggles",
			props -> new thaumcraft.common.items.armor.ItemGoggles(props.durability(ArmorItem.Type.HELMET.getDurability(25)).rarity(net.minecraft.world.item.Rarity.RARE)));

	/** See {@link thaumcraft.common.items.armor.ItemFortressArmor}'s own doc comment - the Fortress armor
	 * tier, crafted at the Infusion Altar from the matching Thaumium armor piece. No boots piece, matching
	 * the original's own real recipe list. */
	public static final DeferredItem<thaumcraft.common.items.armor.ItemFortressArmor> fortressHelm = ITEMS.registerItem("fortress_helm",
			props -> new thaumcraft.common.items.armor.ItemFortressArmor(ThaumcraftArmorMaterials.FORTRESS, ArmorItem.Type.HELMET,
					props.durability(ArmorItem.Type.HELMET.getDurability(40)).rarity(net.minecraft.world.item.Rarity.RARE)));
	public static final DeferredItem<thaumcraft.common.items.armor.ItemFortressArmor> fortressChest = ITEMS.registerItem("fortress_chest",
			props -> new thaumcraft.common.items.armor.ItemFortressArmor(ThaumcraftArmorMaterials.FORTRESS, ArmorItem.Type.CHESTPLATE,
					props.durability(ArmorItem.Type.CHESTPLATE.getDurability(40)).rarity(net.minecraft.world.item.Rarity.RARE)));
	public static final DeferredItem<thaumcraft.common.items.armor.ItemFortressArmor> fortressLegs = ITEMS.registerItem("fortress_legs",
			props -> new thaumcraft.common.items.armor.ItemFortressArmor(ThaumcraftArmorMaterials.FORTRESS, ArmorItem.Type.LEGGINGS,
					props.durability(ArmorItem.Type.LEGGINGS.getDurability(40)).rarity(net.minecraft.world.item.Rarity.RARE)));

	public static final DeferredItem<BlockItem> levitator =
			ITEMS.registerSimpleBlockItem("levitator", BlocksTC.levitator);

	public static final DeferredItem<BlockItem> redstoneRelay =
			ITEMS.registerSimpleBlockItem("redstone_relay", BlocksTC.redstoneRelay);

	public static final DeferredItem<BlockItem> centrifuge =
			ITEMS.registerSimpleBlockItem("centrifuge", BlocksTC.centrifuge);

	public static final java.util.Map<thaumcraft.api.aspects.Aspect, DeferredItem<BlockItem>> crystals = new java.util.LinkedHashMap<>();
	static {
		registerCrystalItem("crystal_aer", thaumcraft.api.aspects.Aspect.AIR);
		registerCrystalItem("crystal_ignis", thaumcraft.api.aspects.Aspect.FIRE);
		registerCrystalItem("crystal_aqua", thaumcraft.api.aspects.Aspect.WATER);
		registerCrystalItem("crystal_terra", thaumcraft.api.aspects.Aspect.EARTH);
		registerCrystalItem("crystal_ordo", thaumcraft.api.aspects.Aspect.ORDER);
		registerCrystalItem("crystal_perditio", thaumcraft.api.aspects.Aspect.ENTROPY);
		registerCrystalItem("crystal_vitium", thaumcraft.api.aspects.Aspect.FLUX);
	}

	private static void registerCrystalItem(String id, thaumcraft.api.aspects.Aspect aspect) {
		crystals.put(aspect, ITEMS.registerSimpleBlockItem(id, BlocksTC.crystals.get(aspect)));
	}

	/** See {@link thaumcraft.common.items.curios.ItemCelestialNotes}'s own doc comment - the true original's
	 * single 13-metadata-variant item, split into 13 real separate items (matching this port's own
	 * established convention for old metadata-variant fields, e.g. {@code ingotBrass}/{@code ingotThaumium}).
	 * Kept as one index-ordered list rather than 13 individually-named fields since every real consumer
	 * ({@link thaumcraft.common.lib.research.ScanSky}, {@link
	 * thaumcraft.common.lib.research.theorycraft.CardCelestial}) only ever needs "the variant at index N"
	 * (0 = sun, 1-4 = stars quadrant, 5-12 = moon phase), matching the true original's own real damage-value
	 * indexing exactly. */
	public static final java.util.List<DeferredItem<thaumcraft.common.items.curios.ItemCelestialNotes>> celestialNotes = new java.util.ArrayList<>();
	static {
		String[] variants = { "sun", "stars_1", "stars_2", "stars_3", "stars_4",
				"moon_1", "moon_2", "moon_3", "moon_4", "moon_5", "moon_6", "moon_7", "moon_8" };
		for (String variant : variants) {
			String key = variant;
			celestialNotes.add(ITEMS.registerItem("celestial_notes_" + variant,
					props -> new thaumcraft.common.items.curios.ItemCelestialNotes(props, key)));
		}
	}

	/** See {@link thaumcraft.common.items.misc.ItemCreativeFluxSponge}'s own doc comment - a real, if
	 * low-priority, fresh-sweep find (a creative-only debug tool, not a real progression item). */
	public static final DeferredItem<thaumcraft.common.items.misc.ItemCreativeFluxSponge> creativeFluxSponge =
			ITEMS.registerItem("creative_flux_sponge", thaumcraft.common.items.misc.ItemCreativeFluxSponge::new);

	private ItemsTC() {
	}
}

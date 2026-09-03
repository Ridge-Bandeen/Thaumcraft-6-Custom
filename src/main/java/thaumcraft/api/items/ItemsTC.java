package thaumcraft.api.items;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
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

	public static final DeferredItem<BlockItem> oreAmber =
			ITEMS.registerSimpleBlockItem("ore_amber", BlocksTC.oreAmber);

	/** The raw gem itself - inert for now (the original used it in several not-yet-ported recipes:
	 * warded glass, arcane ear, various golemancy items). */
	public static final DeferredItem<Item> amber =
			ITEMS.registerSimpleItem("amber");

	/** Golem construction components - see {@code GolemProperties}' own doc comment for the parts system
	 * they craft into. Plain items, same as the original ({@code ItemTCBase} with metadata subtypes, split
	 * into separate items here matching every other metadata-variant item this port has ported). No real
	 * recipes yet - shipped as real, obtainable-in-creative components first, matching the precedent
	 * already set for {@link #amber} and others (ship the item, the recipe chain is real follow-up work). */
	public static final DeferredItem<Item> mechanismSimple =
			ITEMS.registerSimpleItem("mechanism_simple");
	public static final DeferredItem<Item> plateBrass =
			ITEMS.registerSimpleItem("plate_brass");
	public static final DeferredItem<Item> plateIron =
			ITEMS.registerSimpleItem("plate_iron");
	// plateThaumium already registered above (see ingotThaumium's own section) - reused here as-is.
	public static final DeferredItem<Item> plateVoid =
			ITEMS.registerSimpleItem("plate_void");
	public static final DeferredItem<Item> mindClockwork =
			ITEMS.registerSimpleItem("mind_clockwork");
	public static final DeferredItem<Item> mindBiothaumic =
			ITEMS.registerSimpleItem("mind_biothaumic");
	public static final DeferredItem<Item> moduleVision =
			ITEMS.registerSimpleItem("module_vision");
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

	public static final DeferredItem<ItemPechWand> pechWand =
			ITEMS.registerItem("pech_wand", ItemPechWand::new);

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

	public static final DeferredItem<BlockItem> jarNormal =
			ITEMS.registerSimpleBlockItem("jar_normal", BlocksTC.jarNormal);

	public static final DeferredItem<BlockItem> jarVoid =
			ITEMS.registerSimpleBlockItem("jar_void", BlocksTC.jarVoid);

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
	 * 150 uses, 8.0 mining speed, +3 attack damage, 10 enchantability. The original repaired with a "void
	 * ingot" ({@code ItemsTC.ingots} meta 1) - that whole void-metal ore chain isn't ported yet (see {@link
	 * thaumcraft.common.items.tools.ItemVoidSword}'s own doc comment for the rest of the simplifications on
	 * this item), so this repairs with a Thaumium ingot instead as a real, documented, low-stakes
	 * substitution - {@link thaumcraft.common.items.tools.ItemVoidSword} is a rare mob drop, not something
	 * players craft/repair often. */
	private static final Tier TIER_VOID = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 150, 8.0F, 3.0F, 10,
			() -> Ingredient.of(ItemsTC.ingotThaumium));

	/** See {@link thaumcraft.common.items.tools.ItemVoidSword}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemVoidSword> voidSword = ITEMS.registerItem("void_sword",
			props -> new thaumcraft.common.items.tools.ItemVoidSword(TIER_VOID, props.attributes(SwordItem.createAttributes(TIER_VOID, 3, -2.4F))));

	/** {@code ItemCrimsonBlade.toolMatCrimsonVoid} from the original ("CVOID": 200 uses, 8.0 mining speed,
	 * +3.5 attack damage, 20 enchantability) - same real repair-ingredient substitution as {@link
	 * #TIER_VOID} (a Thaumium ingot instead of the unported void-metal chain's own ingot), same rationale
	 * (a rare boss-only weapon, not something players repair often). */
	private static final Tier TIER_CRIMSON_BLADE = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 200, 8.0F, 3.5F, 20,
			() -> Ingredient.of(ItemsTC.ingotThaumium));

	/** See {@link thaumcraft.common.items.tools.ItemCrimsonBlade}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemCrimsonBlade> crimsonBlade = ITEMS.registerItem("crimson_blade",
			props -> new thaumcraft.common.items.tools.ItemCrimsonBlade(TIER_CRIMSON_BLADE,
					props.attributes(SwordItem.createAttributes(TIER_CRIMSON_BLADE, 3, -2.4F)).rarity(net.minecraft.world.item.Rarity.EPIC)));

	/** See {@link thaumcraft.common.items.tools.ItemGrappleGun}'s own doc comment. */
	public static final DeferredItem<thaumcraft.common.items.tools.ItemGrappleGun> grappleGun =
			ITEMS.registerItem("grapple_gun", thaumcraft.common.items.tools.ItemGrappleGun::new);

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

	public static final DeferredItem<BlockItem> pavingStoneTravel =
			ITEMS.registerSimpleBlockItem("paving_stone_travel", BlocksTC.pavingStoneTravel);

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

	public static final java.util.Map<net.minecraft.world.item.DyeColor, DeferredItem<BlockItem>> candles = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
	static {
		BlocksTC.candles.forEach((dye, block) -> candles.put(dye, ITEMS.registerSimpleBlockItem("candle_" + BlocksTC.candleDyeName(dye), block)));
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

	/** See {@link BlocksTC#pedestalEldritch}'s own doc comment. */
	public static final DeferredItem<BlockItem> pedestalEldritch =
			ITEMS.registerSimpleBlockItem("pedestal_eldritch", BlocksTC.pedestalEldritch);

	/** See {@link BlocksTC#bannerCrimsonCult}'s own doc comment. */
	public static final DeferredItem<BlockItem> bannerCrimsonCult =
			ITEMS.registerSimpleBlockItem("banner_crimson_cult", BlocksTC.bannerCrimsonCult);

	/** See {@link BlocksTC#lootCrateCommon}/{@link thaumcraft.common.blocks.misc.BlockLoot}'s own doc
	 * comment. */
	public static final DeferredItem<BlockItem> lootCrateCommon =
			ITEMS.registerSimpleBlockItem("loot_crate_common", BlocksTC.lootCrateCommon);

	public static final DeferredItem<BlockItem> lootCrateUncommon =
			ITEMS.registerSimpleBlockItem("loot_crate_uncommon", BlocksTC.lootCrateUncommon);

	public static final DeferredItem<BlockItem> lootCrateRare =
			ITEMS.registerSimpleBlockItem("loot_crate_rare", BlocksTC.lootCrateRare);

	public static final DeferredItem<BlockItem> infusionMatrix =
			ITEMS.registerSimpleBlockItem("infusion_matrix", BlocksTC.infusionMatrix);

	public static final DeferredItem<BlockItem> stabilizer =
			ITEMS.registerSimpleBlockItem("stabilizer", BlocksTC.stabilizer);

	public static final DeferredItem<BlockItem> inlay =
			ITEMS.registerSimpleBlockItem("inlay", BlocksTC.inlay);

	public static final DeferredItem<BlockItem> alembic =
			ITEMS.registerSimpleBlockItem("alembic", BlocksTC.alembic);

	public static final DeferredItem<BlockItem> smelter =
			ITEMS.registerSimpleBlockItem("smelter", BlocksTC.smelter);

	public static final DeferredItem<BlockItem> bellows =
			ITEMS.registerSimpleBlockItem("bellows", BlocksTC.bellows);

	/** See {@link thaumcraft.common.blocks.devices.BlockMirrorItem}'s own doc comment - a custom
	 * {@code BlockItem} rather than {@code registerSimpleBlockItem}, since it doubles as its own linking
	 * tool. */
	public static final DeferredItem<thaumcraft.common.blocks.devices.BlockMirrorItem> mirror =
			ITEMS.registerItem("mirror", props -> new thaumcraft.common.blocks.devices.BlockMirrorItem(BlocksTC.mirror.get(), props));

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

	private ItemsTC() {
	}
}

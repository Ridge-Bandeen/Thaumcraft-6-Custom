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

	public static final DeferredItem<ItemCaster> casterBasic =
			ITEMS.registerItem("caster_basic", ItemCaster::new);

	public static final DeferredItem<ItemFocusFire> focus1 =
			ITEMS.registerItem("focus_1", ItemFocusFire::new);

	public static final DeferredItem<BlockItem> crucible =
			ITEMS.registerSimpleBlockItem("crucible", BlocksTC.crucible);

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

	public static final DeferredItem<BlockItem> oreAmber =
			ITEMS.registerSimpleBlockItem("ore_amber", BlocksTC.oreAmber);

	/** The raw gem itself - inert for now (the original used it in several not-yet-ported recipes:
	 * warded glass, arcane ear, various golemancy items). */
	public static final DeferredItem<Item> amber =
			ITEMS.registerSimpleItem("amber");

	public static final DeferredItem<ItemCurio> curio =
			ITEMS.registerItem("curio", ItemCurio::new);

	/** Quicksilver's first real use - a reflective glass pane. Inert beyond that for now (the original's
	 * hand mirror/scrying mirror/golem sight devices that consume it aren't ported). */
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

	private ItemsTC() {
	}
}

package thaumcraft.common.lib.research;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The original's research recipe keys (its {@code ConfigRecipes} catalog names, recipe-group names and fake display
 * recipes) against this port's recipe ids - see {@link ResearchRecipeCatalog}. Built entry by entry from the original's
 * {@code ConfigRecipes}; a group lists its members in the original's registration order, which is the order the
 * book pages through them. Keys the original never registered ({@code JarLabelEssence}, {@code nitorcolor}) are left
 * out on purpose: they showed no bookmark there either. Keys are stored lower-cased - see ResearchRecipeCatalog.
 */
final class LegacyRecipeKeys {

	private LegacyRecipeKeys() {
	}

	static final Map<String, List<String>> TABLE = new HashMap<>();

	private static void put(String key, String... ids) {
		List<String> list = new ArrayList<>();
		for (String id : ids) list.add(id.contains(":") ? id : "thaumcraft:" + id);
		TABLE.put("thaumcraft:" + key.toLowerCase(java.util.Locale.ROOT), List.copyOf(list));
	}

	private static String[] colours(String prefix) {
		// EnumDyeColor order, which is the order the original registered its per-colour recipes in.
		String[] names = {"white", "orange", "magenta", "lightblue", "yellow", "lime", "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};
		String[] out = new String[names.length];
		for (int i = 0; i < names.length; i++) out[i] = prefix + names[i];
		return out;
	}

	private static String[] concat(String first, String[] rest) {
		String[] out = new String[rest.length + 1];
		out[0] = first;
		System.arraycopy(rest, 0, out, 1, rest.length);
		return out;
	}

	static {
		// multiblocks (ThaumcraftApi.addMultiblockRecipeToCatalog) - drawn from ResearchBlueprints, not recipes
		// crucible
		String[] aspects = {"aer", "terra", "ignis", "aqua", "ordo", "perditio", "vacuos", "lux", "motus", "gelum", "vitreus",
				"metallum", "victus", "mortuus", "potentia", "permutatio", "praecantatio", "auram", "alkimia", "vitium", "tenebrae",
				"alienis", "volatus", "herba", "instrumentum", "fabrico", "machina", "vinculum", "spiritus", "cognitio", "sensus",
				"aversio", "praemunio", "desiderium", "exanimis", "bestia", "humanus"};
		String[] crystals = new String[aspects.length];
		for (int i = 0; i < aspects.length; i++) crystals[i] = "crystal_essence_" + aspects[i];
		put("viscrystalgroup", crystals);
		put("nitor", "nitor_yellow");
		put("nitorgroup", colours("nitor_"));
		put("alumentum", "alumentum");
		put("brassingot", "brassingot");
		put("thaumiumingot", "ingot_thaumium");
		put("voidingot", "ingot_void");
		put("hedge_tallow", "tallow");
		put("hedge_leather", "hedge_leather");
		put("focus_1", "focus_blank_1");
		put("metal_purification_iron", "cluster_iron");
		put("metal_purification_gold", "cluster_gold");
		put("metal_purification_cinnabar", "cluster_cinnabar");
		put("metal_purification_copper", "cluster_copper");
		put("metal_purification_tin", "cluster_tin");
		put("metal_purification_silver", "cluster_silver");
		put("metal_purification_lead", "cluster_lead");
		put("LiquidDeath", "liquid_death");
		put("BottleTaint", "bottle_taint");
		put("BathSalts", "bath_salts");
		put("SaneSoap", "sanity_soap");
		put("SealCollect", "seal_pickup");
		put("SealCollectAdv", "seal_pickup_advanced");
		put("SealStore", "seal_fill");
		put("SealStoreAdv", "seal_fill_advanced");
		put("SealEmpty", "seal_empty");
		put("SealEmptyAdv", "seal_empty_advanced");
		put("SealProvide", "seal_provider");
		put("SealStock", "seal_stock");
		put("SealGuard", "seal_guard");
		put("SealGuardAdv", "seal_guard_advanced");
		put("SealLumber", "seal_lumber");
		put("SealUse", "seal_use");
		put("SealBreakAdv", "seal_breaker_advanced");
		put("EverfullUrn", "everfull_urn");
		put("hedge_gunpowder", "hedge_gunpowder");
		put("hedge_slime", "hedge_slime");
		put("hedge_glowstone", "hedge_glowstone");
		put("hedge_dye", "hedge_dye");
		put("hedge_clay", "hedge_clay");
		put("hedge_string", "hedge_string");
		put("hedge_web", "hedge_web");
		put("hedge_lava", "hedge_lava");

		// arcane
		put("mechanism_simple", "mechanism_simple");
		put("mechanism_complex", "mechanism_complex");
		put("vis_resonator", "vis_resonator");
		put("activatorrail", "activator_rail");
		put("thaumometer", "thaumometer");
		put("sanitychecker", "sanity_checker");
		put("rechargepedestal", "recharge_pedestal");
		put("workbenchcharger", "arcane_workbench_charger");
		put("wand_workbench", "focal_manipulator");
		put("caster_basic", "caster_basic");
		put("EnchantedFabric", "fabric");
		put("RobeChest", "cloth_chest");
		put("RobeLegs", "cloth_legs");
		put("RobeBoots", "cloth_boots");
		put("Goggles", "goggles");
		put("SealBlank", "seal_blank");
		put("modvision", "module_vision");
		put("modaggression", "module_aggression");
		put("mirrorglass", "mirrored_glass");
		put("ArcaneSpa", "spa");
		put("Tube", "tube");
		put("Resonator", "resonator");
		put("TubeValve", "tube_valve");
		put("TubeFilter", "tube_filter");
		put("TubeRestrict", "tube_restrict");
		put("TubeOneway", "tube_oneway");
		put("TubeBuffer", "tube_buffer");
		put("WardedJar", "jar_normal");
		put("JarVoid", "jar_void");
		put("PaveBarrier", "paving_stone_barrier");
		put("PaveTravel", "paving_stone_travel");
		put("ArcaneLamp", "lamp_arcane");
		put("Levitator", "levitator");
		put("RedstoneRelay", "redstone_relay");
		put("ArcaneEar", "arcane_ear");
		put("ArcaneEarToggle", "arcane_ear_toggle");
		put("InfusionMatrix", "infusion_matrix");
		put("MatrixMotion", "matrix_speed");
		put("MatrixCost", "matrix_cost");
		put("ArcanePedestal", "pedestal");
		put("AncientPedestal", "pedestal_ancient");
		put("EldritchPedestal", "pedestal_eldritch");
		put("FocusPouch", "focus_pouch");
		put("dioptra", "dioptra");
		put("HungryChest", "hungry_chest");
		put("Filter", "filter");
		put("MorphicResonator", "morphic_resonator");
		put("Alembic", "alembic");
		put("EssentiaSmelter", "smelter");
		put("EssentiaSmelterThaumium", "essentia_smelter_thaumium");
		put("EssentiaSmelterVoid", "essentia_smelter_void");
		put("AlchemicalConstruct", "metal_alchemical");
		put("AdvAlchemyConstruct", "metal_alchemical_advanced");
		put("PotionSprayer", "potion_sprayer");
		put("SmelterAux", "smelter_aux");
		put("SmelterVent", "smelter_vent");
		put("EssentiaTransportIn", "essentia_input");
		put("EssentiaTransportOut", "essentia_output");
		put("Bellows", "bellows");
		put("Centrifuge", "centrifuge");
		put("MnemonicMatrix", "brain_box");
		put("MindClockwork", "mind_clockwork");
		put("AutomatedCrossbow", "turret_placer_basic");
		put("AdvancedCrossbow", "turret_placer_advanced");
		put("patterncrafter", "pattern_crafter");
		put("GrappleGunTip", "grapple_gun_tip");
		put("GrappleGunSpool", "grapple_gun_spool");
		put("GrappleGun", "grapple_gun");
		put("VisBattery", "vis_battery");
		put("VisGenerator", "vis_generator");
		put("Condenser", "condenser");
		put("CondenserLattice", "condenser_lattice");
		put("Stabilizer", "stabilizer");
		put("RedstoneInlay", "inlay");

		// infusion
		put("SealHarvest", "seal_harvest");
		put("SealButcher", "seal_butcher");
		put("SealBreak", "seal_breaker");
		put("CrystalClusterAir", "infusion_crystal_cluster_air");
		put("CrystalClusterFire", "infusion_crystal_cluster_fire");
		put("CrystalClusterWater", "infusion_crystal_cluster_water");
		put("CrystalClusterEarth", "infusion_crystal_cluster_earth");
		put("CrystalClusterOrder", "infusion_crystal_cluster_order");
		put("CrystalClusterEntropy", "infusion_crystal_cluster_entropy");
		put("CrystalClusterFlux", "infusion_crystal_cluster_flux");
		put("focus_2", "infusion_focus_blank_2");
		put("focus_3", "infusion_focus_blank_3");
		put("JarBrain", "jar_brain");
		put("VisAmulet", "infusion_vis_amulet");
		put("RunicArmorFake0", "runic_armor_fake_0");
		put("RunicArmorFake1", "runic_armor_fake_1");
		put("RunicArmorFake2", "runic_armor_fake_2");
		put("Mirror", "mirror");
		put("MirrorHand", "hand_mirror");
		put("MirrorEssentia", "mirror_essentia");
		put("ElementalAxe", "infusion_elemental_axe");
		put("ElementalPick", "infusion_elemental_pick");
		put("ElementalSword", "infusion_elemental_sword");
		put("ElementalShovel", "infusion_elemental_shovel");
		put("ElementalHoe", "infusion_elemental_hoe");
		put("IEBURROWINGFAKE", "infusion_enchant_burrowing_fake");
		put("IECOLLECTORFAKE", "infusion_enchant_collector_fake");
		put("IEDESTRUCTIVEFAKE", "infusion_enchant_destructive_fake");
		put("IEREFININGFAKE", "infusion_enchant_refining_fake");
		put("IESOUNDINGFAKE", "infusion_enchant_sounding_fake");
		put("IEARCINGFAKE", "infusion_enchant_arcing_fake");
		put("IEESSENCEFAKE", "infusion_enchant_essence_fake");
		put("IELAMPLIGHTFAKE", "infusion_enchant_lamplight_fake");
		put("BootsTraveller", "traveller_boots");
		put("MindBiothaumic", "mind_biothaumic");
		put("ArcaneBore", "turret_placer_bore");
		put("LampGrowth", "infusion_lamp_growth");
		put("LampFertility", "infusion_lamp_fertility");
		put("ThaumiumFortressHelm", "infusion_fortress_helm");
		put("ThaumiumFortressChest", "infusion_fortress_chest");
		put("ThaumiumFortressLegs", "infusion_fortress_legs");
		put("VoidRobeHelm", "infusion_void_robe_helm");
		put("VoidRobeChest", "infusion_void_robe_chest");
		put("VoidRobeLegs", "infusion_void_robe_legs");
		put("HelmGoggles", "infusion_fortress_helm_goggles");
		put("MaskGrinningDevil", "infusion_fortress_mask_devil");
		put("MaskAngryGhost", "infusion_fortress_mask_ghost");
		put("MaskSippingFiend", "infusion_fortress_mask_fiend");
		put("PrimalCrusher", "primal_crusher");
		put("VerdantHeart", "infusion_verdant_heart");
		put("VerdantHeartLife", "infusion_verdant_heart_life");
		put("VerdantHeartSustain", "infusion_verdant_heart_sustain");
		put("CLOUDRING", "infusion_cloud_ring");
		put("CuriosityBand", "infusion_curiosity_band");
		put("CHARMUNDYING", "infusion_charm_undying");
		put("CausalityCollapser", "infusion_causality_collapser");
		put("VoidSiphon", "void_siphon");
		put("VoidseerPearl", "infusion_voidseer_pearl");

		// crafting, by name and by group
		put("thaumium_stuff", "metal_block_thaumium", "ingot_thaumium_from_block", "plate_thaumium", "thaumium_helm", "thaumium_chest",
				"thaumium_legs", "thaumium_boots", "thaumium_shovel", "thaumium_pick", "thaumium_axe", "thaumium_hoe", "thaumium_sword");
		put("void_stuff", "metal_block_void", "ingot_void_from_block", "plate_void", "void_helm", "void_chest", "void_legs", "void_boots",
				"void_shovel", "void_pick", "void_axe", "void_hoe", "void_sword");
		put("brass_stuff", "metal_block_brass", "ingot_brass_from_block", "plate_brass");
		put("baubles_stuff", "amulet_mundane", "ring_mundane", "girdle_mundane", "amulet_fancy", "ring_fancy", "girdle_fancy");
		put("ironplate", "plate_iron");
		put("triplemeattreatfake", "triple_meat_treat_display");
		put("salismundusfake", "salis_mundus_display");
		put("JarLabel", "label");
		put("StoneArcane", "stone_arcane");
		put("BrickArcane", "stone_arcane_brick");
		put("arcane_stone", "stone_arcane_stairs", "stone_arcane_slab");
		put("arcane_brick", "stone_arcane_brick_stairs", "stone_arcane_brick_slab");
		put("tablewood", "table_wood");
		put("tablestone", "table_stone");
		put("inkwell", "scribing_tools_craft1", "scribing_tools_craft2", "scribing_tools_refill");
		put("GolemBell", "golem_bell");
		String[] dyed = colours("candle_");
		dyed[0] = "candle_white_dye"; // the white-dye recolour; plain candle_white is the shaped tallow recipe
		put("tallowcandles", concat("candle_white", dyed));
		put("BrassBrace", "jar_brace");
		put("Banners", colours("banner_"));
	}
}

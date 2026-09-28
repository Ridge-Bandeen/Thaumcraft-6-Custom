package thaumcraft.common.config;

import java.util.Calendar;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every option the original's {@code ModConfig} had ({@code CONFIG_GRAPHICS}, {@code CONFIG_WORLD},
 * {@code CONFIG_MISC}), same names, defaults, ranges and comments, as NeoForge configs - so they show up in the mod
 * list's config screen (registered in {@link thaumcraft.ThaumcraftClient}).
 *
 * <p>Split by who reads them: {@link #CLIENT_SPEC} holds the graphics options only the player's own client uses (tag
 * text, shaders, the heartbeat, jar labels, the caster dial, aspect tooltips); {@link #SPEC} (common) holds the rest,
 * including the two "graphics" options the original read on the server ({@code blueBiome} colours the biome the
 * server sends, {@code showGolemEmotes} decides whether the server sends the emote at all).
 *
 * <p>Options that do nothing in this version, kept so every original option is present, each say so in their
 * comment: {@code dimensionOuterId} (the Outer Lands dimension isn't in this version), {@code generateAura} and
 * {@code regenAura} (the original only ever read them in dead code - the aura always generated), and
 * {@code allowSpawnTaintacle} (never read by the original either).
 */
public class ThaumcraftConfig {

	// ---- client ----
	public static final ModConfigSpec CLIENT_SPEC;
	public static final ModConfigSpec.BooleanValue LARGE_TAG_TEXT;
	public static final ModConfigSpec.BooleanValue DISABLE_SHADERS;
	public static final ModConfigSpec.BooleanValue NO_STRESS;
	public static final ModConfigSpec.BooleanValue CROOKED;
	public static final ModConfigSpec.BooleanValue DIAL_BOTTOM;
	public static final ModConfigSpec.BooleanValue SHOW_TAGS;

	// ---- common ----
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.BooleanValue BLUE_BIOME;
	public static final ModConfigSpec.BooleanValue SHOW_GOLEM_EMOTES;

	public static final ModConfigSpec.ConfigValue<String> OVERWORLD_DIM;
	public static final ModConfigSpec.ConfigValue<String> DIMENSION_OUTER_ID;
	public static final ModConfigSpec.IntValue ORE_DENSITY;
	public static final ModConfigSpec.BooleanValue GENERATE_MAGIC_FOREST;
	public static final ModConfigSpec.IntValue BIOME_MAGICAL_FOREST_WEIGHT;
	public static final ModConfigSpec.DoubleValue TAINT_SPREAD_RATE;
	public static final ModConfigSpec.IntValue TAINT_SPREAD_AREA;
	public static final ModConfigSpec.BooleanValue GENERATE_AURA;
	public static final ModConfigSpec.BooleanValue GENERATE_STRUCTURE;
	public static final ModConfigSpec.BooleanValue GENERATE_CINNABAR;
	public static final ModConfigSpec.BooleanValue GENERATE_AMBER;
	public static final ModConfigSpec.BooleanValue GENERATE_QUARTZ;
	public static final ModConfigSpec.BooleanValue GENERATE_CRYSTALS;
	public static final ModConfigSpec.BooleanValue GENERATE_TREES;
	public static final ModConfigSpec.ConfigValue<String> REGEN_KEY;
	public static final ModConfigSpec.BooleanValue REGEN_AURA;
	public static final ModConfigSpec.BooleanValue REGEN_STRUCTURE;
	public static final ModConfigSpec.BooleanValue REGEN_CINNABAR;
	public static final ModConfigSpec.BooleanValue REGEN_AMBER;
	public static final ModConfigSpec.BooleanValue REGEN_QUARTZ;
	public static final ModConfigSpec.BooleanValue REGEN_CRYSTALS;
	public static final ModConfigSpec.BooleanValue REGEN_TREES;
	public static final ModConfigSpec.BooleanValue ALLOW_SPAWN_ANGRY_ZOMBIE;
	public static final ModConfigSpec.BooleanValue ALLOW_SPAWN_FIRE_BAT;
	public static final ModConfigSpec.BooleanValue ALLOW_SPAWN_WISP;
	public static final ModConfigSpec.BooleanValue ALLOW_SPAWN_TAINTACLE;
	public static final ModConfigSpec.BooleanValue ALLOW_SPAWN_PECH;
	public static final ModConfigSpec.BooleanValue ALLOW_SPAWN_ELDER;
	public static final ModConfigSpec.BooleanValue ALLOW_CHAMPION_MOBS;

	public static final ModConfigSpec.BooleanValue NO_SLEEP;
	public static final ModConfigSpec.BooleanValue WUSS_MODE;
	public static final ModConfigSpec.BooleanValue ALLOW_CHEAT_SHEET;
	public static final ModConfigSpec.IntValue SHIELD_RECHARGE_MS;
	public static final ModConfigSpec.IntValue SHIELD_WAIT_MS;
	public static final ModConfigSpec.DoubleValue SHIELD_COST;

	static {
		ModConfigSpec.Builder client = new ModConfigSpec.Builder();
		client.push("graphics");
		LARGE_TAG_TEXT = client
				.comment("Setting this to true will make the amount text in aspect tags twice as large. Useful for certain resolutions and custom fonts.")
				.define("largeTagText", false);
		DISABLE_SHADERS = client
				.comment("This setting will disable certain thaumcraft shaders for those who experience FPS drops.")
				.define("disableShaders", false);
		NO_STRESS = client
				.comment("Set to true to disable anxiety triggers like the heartbeat sound.")
				.define("nostress", false);
		CROOKED = client
				.comment("Hate crooked labels, kittens, puppies and all things awesome? If yes, set this to false.")
				.define("crooked", true);
		DIAL_BOTTOM = client
				.comment("Set to true to have the wand dial display in the bottom left instead of the top left.")
				.define("dialBottom", false);
		SHOW_TAGS = client
				.comment("Item aspects are hidden by default and pressing shift reveals them.",
						"Changing this setting to 'true' will reverse this behaviour and always",
						"display aspects unless shift is pressed.")
				.define("showTags", false);
		client.pop();
		CLIENT_SPEC = client.build();

		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

		builder.push("graphics");
		BLUE_BIOME = builder
				.comment("Set this to true to get the old blue magical forest back.")
				.worldRestart()
				.define("blueBiome", false);
		SHOW_GOLEM_EMOTES = builder
				.comment("Will golems display emote particles if they recieve orders or encounter problems")
				.define("showGolemEmotes", true);
		builder.pop();

		builder.push("world");
		OVERWORLD_DIM = builder
				.comment("The dimension considered to be your 'overworld'. Certain TC structures will only spawn in this dim.")
				.worldRestart()
				.define("overworldDim", "minecraft:overworld");
		DIMENSION_OUTER_ID = builder
				.comment("Outer lands dimension id.",
						"(Does nothing in this version: the Outer Lands aren't in it.)")
				.gameRestart()
				.define("dimensionOuterId", "thaumcraft:outer_lands");
		ORE_DENSITY = builder
				.comment("The % of normal ore amounts that will be spawned. For example 50 will spawn half",
						"the ores while 200 will spawn double. Default 100")
				.defineInRange("oreDensity", 100, 1, 500);
		GENERATE_MAGIC_FOREST = builder
				.gameRestart()
				.define("generateMagicForest", true);
		BIOME_MAGICAL_FOREST_WEIGHT = builder
				.comment("Higher values increases number of magical forest biomes. If you are using biome",
						"addon mods you probably want to increase this weight quite a bit")
				.gameRestart()
				.defineInRange("biomeMagicalForestWeight", 5, 0, 100);
		TAINT_SPREAD_RATE = builder
				.comment("The % chance of taint fibres spreading on a block tick.",
						"Setting this to 0 will effectively stop taint fibre spread.")
				.defineInRange("taintSpreadRate", 100.0, 0.0, 500.0);
		TAINT_SPREAD_AREA = builder
				.comment("The range at which taint can spread from a taint seed.",
						"This value is only a base and will be modified by flux levels.")
				.defineInRange("taintSpreadArea", 32, 8, 256);
		GENERATE_AURA = builder
				.comment("(Does nothing: the aura always generates, as it did in the original.)")
				.define("generateAura", true);
		GENERATE_STRUCTURE = builder.define("generateStructure", true);
		GENERATE_CINNABAR = builder.define("generateCinnabar", true);
		GENERATE_AMBER = builder.define("generateAmber", true);
		GENERATE_QUARTZ = builder.define("generateQuartz", true);
		GENERATE_CRYSTALS = builder.define("generateCrystals", true);
		GENERATE_TREES = builder.define("generateTrees", true);
		REGEN_KEY = builder
				.comment("This key is used to keep track of which chunk have been generated/regenerated.",
						"Changing it will cause the regeneration code to run again, so only change it if you want it to happen.",
						"Useful to regen only one world feature at a time.")
				.define("regenKey", "DEFAULT");
		REGEN_AURA = builder
				.comment("(Does nothing: see generateAura.)")
				.define("regenAura", false);
		REGEN_STRUCTURE = builder.define("regenStructure", false);
		REGEN_CINNABAR = builder.define("regenCinnabar", false);
		REGEN_AMBER = builder.define("regenAmber", false);
		REGEN_QUARTZ = builder.define("regenQuartz", false);
		REGEN_CRYSTALS = builder.define("regenCrystals", false);
		REGEN_TREES = builder.define("regenTrees", false);
		ALLOW_SPAWN_ANGRY_ZOMBIE = builder.worldRestart().define("allowSpawnAngryZombie", true);
		ALLOW_SPAWN_FIRE_BAT = builder.worldRestart().define("allowSpawnFireBat", true);
		ALLOW_SPAWN_WISP = builder.worldRestart().define("allowSpawnWisp", true);
		ALLOW_SPAWN_TAINTACLE = builder
				.comment("(Does nothing: the original never read it either.)")
				.define("allowSpawnTaintacle", true);
		ALLOW_SPAWN_PECH = builder.worldRestart().define("allowSpawnPech", true);
		ALLOW_SPAWN_ELDER = builder.worldRestart().define("allowSpawnElder", true);
		ALLOW_CHAMPION_MOBS = builder
				.comment("Setting this to false will disable spawning champion mobs. Even when false they will still",
						"have a greatly reduced chance of spawning in certain dangerous places.")
				.define("allowChampionMobs", true);
		builder.pop();

		builder.push("misc");
		NO_SLEEP = builder
				.comment("Setting this to true will make you get the recipe book for salis mundus without having to sleep first.")
				.define("noSleep", false);
		WUSS_MODE = builder
				.comment("Setting this to true disables Warp, Taint spread and similar mechanics. You wuss.")
				.define("wussMode", false);
		ALLOW_CHEAT_SHEET = builder
				.comment("Enables a version of the Thauminomicon in creative mode that grants you all the research when you first use it.")
				.define("allowCheatSheet", false);
		SHIELD_RECHARGE_MS = builder
				.comment("How many milliseconds passes between runic shielding recharge ticks.",
						"Lower values equals faster recharge. Minimum of 500.")
				.defineInRange("shieldRecharge", 2000, 500, 500000);
		SHIELD_WAIT_MS = builder
				.comment("How many milliseconds passes after a shield has been reduced to zero",
						"before it can start recharging again. Minimum of 0.")
				.defineInRange("shieldWait", 4000, 0, 50000);
		SHIELD_COST = builder
				.comment("How much vis it costs to reacharge a single unit of shielding. Minimum of 0.")
				.defineInRange("shieldCost", 1.0, 0.0, 500.0);
		builder.pop();

		SPEC = builder.build();
	}

	private ThaumcraftConfig() {
	}

	public static void register(net.neoforged.fml.ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
		modContainer.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
	}

	/** Reads a client option, or its default where the client config isn't loaded (a dedicated server, GameTests). */
	public static boolean client(ModConfigSpec.BooleanValue value) {
		return CLIENT_SPEC.isLoaded() ? value.get() : value.getDefault();
	}

	/** Reads a common option, or its default before the config has loaded. */
	public static <T> T common(ModConfigSpec.ConfigValue<T> value) {
		return SPEC.isLoaded() ? value.get() : value.getDefault();
	}

	/** The original's Halloween check ({@code ModConfig.isHalloween}): 31 October on the machine's clock. */
	public static boolean isHalloween() {
		Calendar calendar = Calendar.getInstance();
		return calendar.get(Calendar.MONTH) == Calendar.OCTOBER && calendar.get(Calendar.DAY_OF_MONTH) == 31;
	}
}

package thaumcraft.common.config;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The real modern (NeoForge {@link ModConfigSpec}) replacement for the old (1.12.2) {@code ModConfig}'s
 * {@code CONFIG_MISC}/{@code CONFIG_WORLD} inner classes - the one piece of that file this port still had a
 * genuine open thread on (see the port-tracking memory's own "ModConfig" entry). The old file's real
 * consumers are ALL stale, unported 1.12.2 originals (nothing in the current build references
 * {@code thaumcraft.common.config.ModConfig} except via doc-comment cross-references) - so rather than port
 * that file wholesale, this is a deliberately narrow COMMON config covering exactly the settings that already
 * have a real, currently-shipped consumer hardcoding the original's own default value while noting "no config
 * system yet": {@link thaumcraft.common.lib.events.RunicShieldEvents}, {@link
 * thaumcraft.common.blocks.world.taint.TaintHelper}, {@link thaumcraft.common.lib.events.WarpEvents}, and
 * {@link thaumcraft.common.lib.events.ChampionModEvents}.
 *
 * Every other field the old {@code ModConfig}/{@code CONFIG_GRAPHICS}/{@code CONFIG_WORLD} held (loot-bag
 * table entries - now real recipe/loot JSON, not config; ore-dictionary/MFR-safari-net mod compatibility -
 * both dead in a modern-only environment; world-gen retrofit/regen flags, dimension ids, ore density, biome
 * weights - {@code ThaumcraftWorldGenerator}/{@code Registrar}'s own real modern world-gen isn't built yet,
 * nothing to toggle; graphics toggles like {@code largeTagText}/{@code crooked}/{@code dialBottom}/
 * {@code showTags}/{@code disableShaders}/{@code blueBiome}/{@code showGolemEmotes}/{@code nostress} - every
 * one of their real consumers (wand HUD dial, aspect tag rendering, shaders, golem emote particles) is itself
 * still a stale unported client system, nothing to gate yet; {@code noSleep} - its own real payoff
 * ({@code giveDreamJournal}) needs the still-unported {@code ConfigItems.startBook}, tracked separately;
 * {@code allowCheatSheet} - the creative-mode Thaumonomicon auto-research shortcut isn't built) has zero real
 * modern consumer and is intentionally left out rather than ported speculatively - same standing "don't build
 * config surface for a system that doesn't exist yet" discipline this session used for {@code EntityUtils}.
 */
public class ThaumcraftConfig {

	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.BooleanValue WUSS_MODE;
	public static final ModConfigSpec.BooleanValue ALLOW_CHAMPION_MOBS;
	public static final ModConfigSpec.IntValue SHIELD_RECHARGE_MS;
	public static final ModConfigSpec.IntValue SHIELD_WAIT_MS;
	public static final ModConfigSpec.DoubleValue SHIELD_COST;
	public static final ModConfigSpec.DoubleValue TAINT_SPREAD_RATE;
	public static final ModConfigSpec.IntValue TAINT_SPREAD_AREA;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

		builder.push("misc");
		WUSS_MODE = builder
				.comment("Setting this to true disables Warp misfortune events and taint fibre spread. You wuss.")
				.define("wussMode", false);
		builder.pop();

		builder.push("world");
		ALLOW_CHAMPION_MOBS = builder
				.comment("Setting this to false disables champion (elite) mob variants spawning entirely.")
				.define("allowChampionMobs", true);
		TAINT_SPREAD_RATE = builder
				.comment("The % chance of taint fibres spreading on a block tick. Setting this to 0 effectively",
						"stops taint fibre spread. Default 100.")
				.defineInRange("taintSpreadRate", 100.0, 0.0, 500.0);
		TAINT_SPREAD_AREA = builder
				.comment("The range at which taint can spread from a taint seed. Default 32.")
				.defineInRange("taintSpreadArea", 32, 8, 256);
		builder.pop();

		builder.push("runicShielding");
		SHIELD_RECHARGE_MS = builder
				.comment("How many milliseconds pass between runic shielding recharge ticks. Lower is faster. Minimum 500.")
				.defineInRange("shieldRecharge", 2000, 500, 500000);
		SHIELD_WAIT_MS = builder
				.comment("How many milliseconds pass after a shield is reduced to zero before it can start recharging again.")
				.defineInRange("shieldWait", 4000, 0, 50000);
		SHIELD_COST = builder
				.comment("How much vis it costs to recharge a single unit of shielding. Minimum 0.")
				.defineInRange("shieldCost", 1.0, 0.0, 500.0);
		builder.pop();

		SPEC = builder.build();
	}

	private ThaumcraftConfig() {
	}

	public static void register(net.neoforged.fml.ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
	}
}

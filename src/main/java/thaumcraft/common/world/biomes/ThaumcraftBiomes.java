package thaumcraft.common.world.biomes;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import thaumcraft.Thaumcraft;

/**
 * Real {@link ResourceKey} constants for the true original's own 3 real custom biomes - Magical Forest,
 * Eerie, and Eldritch (registered purely as data, via {@code data/thaumcraft/worldgen/biome/*.json}; this
 * class exists only so other real Java code can reference them type-safely, the same real pattern
 * {@link thaumcraft.common.registry.ThaumcraftEntities}/{@code ThaumcraftBlockEntities} etc. already use for
 * their own registries).
 *
 * <p>Real, honest story of what these 3 biomes are and aren't, worth recording here since it spans several
 * commits: the true original's own {@code BiomeGenEerie}/{@code BiomeGenEldritch}/{@code BiomeGenMagicalForest}
 * were never really custom terrain shapes - their real content is mob spawn lists, sky/grass/water color
 * overrides, vegetation density, and (Magical Forest only) a real {@code decorate()} override placing
 * {@code grassAmbient} patches, mossy-cobblestone blobs, and {@code vishroom} near wood. All of that maps
 * cleanly onto modern biome JSON (spawners/effects/features) - the one genuinely hard part turned out to be
 * getting a brand-new biome to actually <i>appear</i> in the existing overworld's noise-based terrain at
 * all, since modern Minecraft's own biome placement
 * ({@code multi_noise_biome_source_parameter_list}) has no plain-datapack extension point: the real codec
 * only accepts a {@code "preset"} reference to one of 2 hardcoded Java presets
 * ({@code minecraft:overworld}/{@code minecraft:nether}), verified directly, not assumed. The real, safe
 * modern path (confirmed against Biomes O' Plenty's own real source - a ~870-line reimplementation of
 * vanilla's own internal {@code OverworldBiomeBuilder}, real but the single biggest/riskiest chunk in this
 * whole port and non-cooperative with other biome mods) is a small dependency, Biolith
 * (see {@code build.gradle}'s own doc comment on the TerraformersMC repository for the full real reasoning) -
 * a real, additive, datapack-driven biome-placement library bundled straight into this mod's own jar via
 * NeoForge's real {@code jarJar} mechanism, so players only ever install Thaumcraft. Real placement lives at
 * {@code data/thaumcraft/biolith/biome_placement/overworld.json}, real climate-parameter windows chosen to
 * be well-separated and thematically reasonable (Magical Forest: warm/humid/positive-weirdness, matching
 * vanilla's own "rare, special forest variant" convention; Eerie: warm/dry/negative-weirdness; Eldritch: the
 * rarest, an extreme corner of eroded, dry, deeply-negative-weirdness terrain).
 *
 * <p>Real, honest limit of what this session could verify: two direct attempts at querying the real
 * placement result programmatically (via the registered {@code MultiNoiseBiomeSourceParameterList}, then via
 * the real overworld's own {@code LevelStem} registry entry and its real configured
 * {@code ChunkGenerator#getBiomeSource()}) both confirmed that {@code runGameTestServer}'s own real
 * environment never exposes a real {@code MultiNoiseBiomeSource} at all, for either path - a real, confirmed
 * (not assumed) limitation of that test environment specifically, not a bug in the placement itself. What
 * this session's own real GameTest coverage in {@code ThaumcraftGameTests} does verify: the biome JSON
 * parses and registers with the real intended effects/spawn categories/features (including 2 real
 * mob-category mismatches this same verification effort found and fixed - Bat is really
 * {@code AMBIENT}, not {@code CREATURE}; Pech is really {@code CREATURE}, not {@code MONSTER} - both now
 * matching each entity's own real registered {@code MobCategory}, confirmed via NeoForge's own startup
 * warning for exactly this mismatch), and Biolith's own placement file parses and loads without error.
 * Whether the real overworld actually generates these 3 biomes at reasonable places needs a real
 * {@code runClient} exploration pass - tracked in the project's own standing manual-verification checklist,
 * not assumed complete here.
 *
 * <p>Real, deliberate simplifications versus the true original, all documented at their own real point:
 * <ul>
 * <li>{@code grassAmbient} isn't a distinct block (see {@link thaumcraft.common.blocks.world.plants.BlockPlantShimmerleaf}'s
 * own doc comment) - plain vanilla grass covers it.</li>
 * <li>No bespoke terrain surface rule (Eldritch's own real dirt-topped terrain) - real surface block
 * selection for brand-new biomes would need injecting into {@code noise_settings/overworld.json}'s own
 * {@code surface_rule} tree, a similarly invasive change to the climate-placement problem above; left as
 * ordinary terrain for now.</li>
 * <li>No "big magic tree" (Magical Forest's own real majority-case generic tree, a whole unported custom
 * tree shape) - Silverwood/Greatwood's own already-real placed features are listed at boosted density
 * instead, so the biome isn't near-treeless without it.</li>
 * <li>No mossy-cobblestone block-blobs, no noise-based vanilla-flower-type blending (both real but purely
 * decorative flourishes in the original) - same "keep the mechanic, drop the flourish" precedent as
 * everywhere else in this port.</li>
 * </ul>
 */
public final class ThaumcraftBiomes {

	public static final ResourceKey<Biome> MAGICAL_FOREST = key("magical_forest");
	public static final ResourceKey<Biome> EERIE = key("eerie");
	public static final ResourceKey<Biome> ELDRITCH = key("eldritch");

	private static ResourceKey<Biome> key(String name) {
		return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, name));
	}

	private ThaumcraftBiomes() {
	}
}

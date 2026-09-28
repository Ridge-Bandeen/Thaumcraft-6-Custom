package thaumcraft.common.world;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import thaumcraft.Thaumcraft;
import thaumcraft.common.config.ThaumcraftConfig;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Where the original's {@code CONFIG_WORLD} options reach world generation and spawning - what its
 * {@code ThaumcraftWorldGenerator}, {@code BiomeHandler}/{@code Registrar} and {@code ConfigEntities} did with them:
 * <ul>
 *   <li>{@link ConfigPlacement} ({@code "type": "thaumcraft:config"}) heads the placement list of each of Thaumcraft's
 *       own scattered features: the {@code generate*} switches, {@code oreDensity} for ores and crystals,
 *       {@code overworldDim} for structures (mounds, cultist portals), and no trees or structures on flat worlds.</li>
 *   <li>{@code regenKey} / {@code regen*}: every chunk remembers the key it was generated under; an older chunk that
 *       loads while some {@code regen*} option is on has those features placed again, ten chunks a tick.</li>
 *   <li>{@link ConfigSpawns} ({@code thaumcraft:config_spawns}): the {@code allowSpawn*} natural spawns - Thaumcraft's
 *       mobs across the overworld and the Nether, and in its own biomes.</li>
 *   <li>{@link BlueBiome}: {@code blueBiome}'s old grass and leaf colours for the Magical Forest.</li>
 *   <li>{@link #placeMagicalForest}: {@code generateMagicForest} and {@code biomeMagicalForestWeight}.</li>
 * </ul>
 */
public final class WorldConfigHooks {

	public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Thaumcraft.MODID);
	public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Thaumcraft.MODID);
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Thaumcraft.MODID);

	public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigPlacement>> CONFIG_PLACEMENT =
			PLACEMENT_MODIFIERS.register("config", () -> () -> ConfigPlacement.CODEC);
	public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ConfigSpawns>> CONFIG_SPAWNS =
			BIOME_MODIFIERS.register("config_spawns", () -> MapCodec.unit(ConfigSpawns.INSTANCE));
	public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<BlueBiome>> BLUE_BIOME =
			BIOME_MODIFIERS.register("blue_biome", () -> MapCodec.unit(BlueBiome.INSTANCE));
	/** The {@code regenKey} a chunk was generated (or last regenerated) under - the original's chunk NBT flag. */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> REGEN_KEY =
			ATTACHMENTS.register("regen_key", () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).build());

	public static final ResourceKey<Biome> MAGICAL_FOREST = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "magical_forest"));
	public static final ResourceKey<Biome> EERIE = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "eerie"));
	public static final TagKey<Biome> MAGICAL = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_magical"));

	private WorldConfigHooks() {
	}

	public static void register(IEventBus modBus) {
		PLACEMENT_MODIFIERS.register(modBus);
		BIOME_MODIFIERS.register(modBus);
		ATTACHMENTS.register(modBus);
		NeoForge.EVENT_BUS.addListener(WorldConfigHooks::onChunkLoad);
		NeoForge.EVENT_BUS.addListener(WorldConfigHooks::onLevelTick);
	}

	/** Whether {@code level} is the configured {@code overworldDim}. */
	public static boolean isOverworldDim(Level level) {
		return level.dimension().location().toString().equals(ThaumcraftConfig.common(ThaumcraftConfig.OVERWORLD_DIM));
	}

	// ---- placement ----

	/** Which option switches a feature: the original's {@code generate*}/{@code regen*} pairs. */
	public enum Gen {
		STRUCTURE, CINNABAR, AMBER, QUARTZ, CRYSTALS, TREES;

		boolean generate() {
			return ThaumcraftConfig.common(switch (this) {
				case STRUCTURE -> ThaumcraftConfig.GENERATE_STRUCTURE;
				case CINNABAR -> ThaumcraftConfig.GENERATE_CINNABAR;
				case AMBER -> ThaumcraftConfig.GENERATE_AMBER;
				case QUARTZ -> ThaumcraftConfig.GENERATE_QUARTZ;
				case CRYSTALS -> ThaumcraftConfig.GENERATE_CRYSTALS;
				case TREES -> ThaumcraftConfig.GENERATE_TREES;
			});
		}

		boolean regen() {
			return ThaumcraftConfig.common(switch (this) {
				case STRUCTURE -> ThaumcraftConfig.REGEN_STRUCTURE;
				case CINNABAR -> ThaumcraftConfig.REGEN_CINNABAR;
				case AMBER -> ThaumcraftConfig.REGEN_AMBER;
				case QUARTZ -> ThaumcraftConfig.REGEN_QUARTZ;
				case CRYSTALS -> ThaumcraftConfig.REGEN_CRYSTALS;
				case TREES -> ThaumcraftConfig.REGEN_TREES;
			});
		}

		static boolean anyRegen() {
			for (Gen gen : values()) {
				if (gen.regen()) return true;
			}
			return false;
		}
	}

	/** Set on the server thread while re-placing features in an old chunk: then only {@code regen*} features pass. */
	static final ThreadLocal<Boolean> REGENERATING = ThreadLocal.withInitial(() -> false);

	/**
	 * {@code {"type": "thaumcraft:config", "feature": "cinnabar", "density": true}}: passes the position on only when
	 * that feature's {@code generate*} option is on (and, while regenerating, its {@code regen*} option), in the
	 * {@code overworldDim} for structures, and never on flat worlds for trees and structures. With {@code density} it
	 * passes it on {@code oreDensity}% as many times - so the count that follows is scaled, rounding randomly.
	 */
	public static final class ConfigPlacement extends PlacementModifier {
		public static final MapCodec<ConfigPlacement> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
				Codec.STRING.xmap(s -> Gen.valueOf(s.toUpperCase(java.util.Locale.ROOT)), g -> g.name().toLowerCase(java.util.Locale.ROOT))
						.fieldOf("feature").forGetter(p -> p.gen),
				Codec.BOOL.optionalFieldOf("density", false).forGetter(p -> p.density)
		).apply(inst, ConfigPlacement::new));

		final Gen gen;
		final boolean density;

		public ConfigPlacement(Gen gen, boolean density) {
			this.gen = gen;
			this.density = density;
		}

		@Override
		public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
			if (!gen.generate() || (REGENERATING.get() && !gen.regen())) return Stream.empty();
			if ((gen == Gen.STRUCTURE || gen == Gen.TREES) && context.generator() instanceof FlatLevelSource) return Stream.empty();
			if (gen == Gen.STRUCTURE && !isOverworldDim(context.getLevel().getLevel())) return Stream.empty();
			if (!density) return Stream.of(pos);
			float d = ThaumcraftConfig.common(ThaumcraftConfig.ORE_DENSITY) / 100.0f;
			int times = (int) d + (random.nextFloat() < d - (int) d ? 1 : 0);
			return Stream.generate(() -> pos).limit(times);
		}

		@Override
		public PlacementModifierType<?> type() {
			return CONFIG_PLACEMENT.get();
		}
	}

	// ---- regeneration ----

	private static final Map<ResourceKey<Level>, ArrayDeque<ChunkPos>> TO_REGENERATE = new HashMap<>();

	static void onChunkLoad(ChunkEvent.Load event) {
		if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
		String key = ThaumcraftConfig.common(ThaumcraftConfig.REGEN_KEY);
		if (key.equals(chunk.getData(REGEN_KEY))) return;
		if (!event.isNewChunk() && Gen.anyRegen()) {
			Thaumcraft.LOGGER.warn("World gen was never run for chunk at {}. Adding to queue for regeneration.", chunk.getPos());
			TO_REGENERATE.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>()).add(chunk.getPos());
		}
		chunk.setData(REGEN_KEY, key);
		chunk.setUnsaved(true);
	}

	static void onLevelTick(LevelTickEvent.Post event) {
		if (!(event.getLevel() instanceof ServerLevel level)) return;
		ArrayDeque<ChunkPos> queue = TO_REGENERATE.get(level.dimension());
		if (queue == null || queue.isEmpty()) return;
		int count = 0;
		for (; count < 10 && !queue.isEmpty(); count++) {
			regenerate(level, queue.poll());
		}
		Thaumcraft.LOGGER.info("[Thaumcraft] Regenerated {} chunks. {} chunks left", count, queue.size());
	}

	/** Places every config-switched Thaumcraft feature whose {@code regen*} option is on into an existing chunk. */
	public static void regenerate(ServerLevel level, ChunkPos chunk) {
		if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) return;
		// the original's per-chunk seed: fmlRandom from the world seed and the chunk coordinates
		RandomSource seeder = RandomSource.create(level.getSeed());
		long xSeed = seeder.nextLong() >> 3;
		long zSeed = seeder.nextLong() >> 3;
		RandomSource random = RandomSource.create(xSeed * chunk.x + zSeed * chunk.z ^ level.getSeed());
		BlockPos origin = new BlockPos(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ());
		REGENERATING.set(true);
		try {
			level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).holders()
					.filter(h -> h.key().location().getNamespace().equals(Thaumcraft.MODID))
					.filter(h -> h.value().placement().stream().anyMatch(m -> m instanceof ConfigPlacement))
					.forEach(h -> h.value().placeWithBiomeCheck(level, level.getChunkSource().getGenerator(), random, origin));
		} finally {
			REGENERATING.set(false);
		}
	}

	// ---- biome modifiers ----

	/**
	 * The {@code allowSpawn*} natural spawns, as the original's {@code ConfigEntities} and biome classes added them:
	 * brainy zombies in every overworld biome that has monsters (weight 10), pech in the magical biomes (10, and 20 in
	 * groups of 1-2 in the Magical Forest), firebats in the Nether (10, 1-2; on Halloween the overworld too, weight 5),
	 * wisps in the Nether (5) and the Magical Forest (20, 1-2), and in the Eerie biome brainy zombies (32), giant brainy
	 * zombies (8), wisps (3) and eldritch guardians (1). All as monsters, like the original.
	 */
	public static final class ConfigSpawns implements BiomeModifier {
		static final ConfigSpawns INSTANCE = new ConfigSpawns();

		@Override
		public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
			if (phase != Phase.ADD) return;
			var spawns = builder.getMobSpawnSettings();
			boolean magicalForest = biome.is(MAGICAL_FOREST);
			boolean eerie = biome.is(EERIE);
			boolean overworld = biome.is(BiomeTags.IS_OVERWORLD) || magicalForest;
			boolean nether = biome.is(BiomeTags.IS_NETHER);
			boolean hasMonsters = !spawns.getSpawner(MobCategory.MONSTER).isEmpty();

			if (ThaumcraftConfig.ALLOW_SPAWN_ANGRY_ZOMBIE.get()) {
				if (overworld && hasMonsters) add(spawns, ThaumcraftEntities.BRAINY_ZOMBIE.get(), 10, 1, 1);
				if (eerie) {
					add(spawns, ThaumcraftEntities.BRAINY_ZOMBIE.get(), 32, 1, 1);
					add(spawns, ThaumcraftEntities.GIANT_BRAINY_ZOMBIE.get(), 8, 1, 1);
				}
			}
			if (ThaumcraftConfig.ALLOW_SPAWN_PECH.get()) {
				if (biome.is(MAGICAL) && hasMonsters) add(spawns, ThaumcraftEntities.PECH.get(), 10, 1, 1);
				if (magicalForest) add(spawns, ThaumcraftEntities.PECH.get(), 20, 1, 2);
			}
			if (ThaumcraftConfig.ALLOW_SPAWN_FIRE_BAT.get()) {
				if (nether) add(spawns, ThaumcraftEntities.FIRE_BAT.get(), 10, 1, 2);
				if (overworld && ThaumcraftConfig.isHalloween()) add(spawns, ThaumcraftEntities.FIRE_BAT.get(), 5, 1, 2);
			}
			if (ThaumcraftConfig.ALLOW_SPAWN_WISP.get()) {
				if (nether) add(spawns, ThaumcraftEntities.WISP.get(), 5, 1, 1);
				if (magicalForest) add(spawns, ThaumcraftEntities.WISP.get(), 20, 1, 2);
				if (eerie) add(spawns, ThaumcraftEntities.WISP.get(), 3, 1, 1);
			}
			if (ThaumcraftConfig.ALLOW_SPAWN_ELDER.get() && eerie) {
				add(spawns, ThaumcraftEntities.ELDRITCH_GUARDIAN.get(), 1, 1, 1);
			}
		}

		private static void add(net.neoforged.neoforge.common.world.MobSpawnSettingsBuilder spawns, EntityType<?> type, int weight, int min, int max) {
			spawns.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(type, weight, min, max));
		}

		@Override
		public MapCodec<? extends BiomeModifier> codec() {
			return CONFIG_SPAWNS.get();
		}
	}

	/** {@code blueBiome}: the Magical Forest's old blue grass (6728396) and leaves (7851246). */
	public static final class BlueBiome implements BiomeModifier {
		static final BlueBiome INSTANCE = new BlueBiome();

		@Override
		public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
			if (phase == Phase.MODIFY && biome.is(MAGICAL_FOREST) && ThaumcraftConfig.BLUE_BIOME.get()) {
				builder.getSpecialEffects().grassColorOverride(6728396).foliageColorOverride(7851246);
			}
		}

		@Override
		public MapCodec<? extends BiomeModifier> codec() {
			return BLUE_BIOME.get();
		}
	}

	// ---- spawn rules ----

	/** The original pech's {@code getCanSpawnHere}: a monster's darkness, fewer than 4 pech within 16 blocks, and a
	 * magical biome - in the {@code overworldDim}, or else only the Magical Forest or Eerie biome. */
	public static boolean checkPechSpawn(EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
		if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) return false;
		Holder<Biome> biome = level.getBiome(pos);
		if (!biome.is(MAGICAL)) return false;
		if (!isOverworldDim(level.getLevel()) && !biome.is(MAGICAL_FOREST) && !biome.is(EERIE)) return false;
		return level.getEntitiesOfClass(thaumcraft.common.entities.monster.EntityPech.class, new AABB(pos).inflate(16.0)).size() < 4;
	}

	/** The original wisp's {@code getCanSpawnHere}: fewer than 8 wisps within 16 blocks, not peaceful, dark enough. */
	public static boolean checkWispSpawn(EntityType<? extends net.minecraft.world.entity.Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
		return level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
				&& (MobSpawnType.ignoresLightRequirements(reason) || Monster.isDarkEnoughToSpawn(level, pos, random))
				&& net.minecraft.world.entity.Mob.checkMobSpawnRules(type, level, reason, pos, random)
				&& level.getEntitiesOfClass(thaumcraft.common.entities.monster.EntityWisp.class, new AABB(pos).inflate(16.0)).size() < 8;
	}

	/** The original firebat's {@code getCanSpawnHere}: light at most a random 0-6, then a monster's rules. */
	public static boolean checkFireBatSpawn(EntityType<? extends net.minecraft.world.entity.Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
		return level.getMaxLocalRawBrightness(pos) <= random.nextInt(7)
				&& level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
				&& (MobSpawnType.ignoresLightRequirements(reason) || Monster.isDarkEnoughToSpawn(level, pos, random))
				&& net.minecraft.world.entity.Mob.checkMobSpawnRules(type, level, reason, pos, random);
	}

	// ---- Magical Forest placement ----

	/**
	 * {@code generateMagicForest} / {@code biomeMagicalForestWeight}: the original put the Magical Forest in the warm
	 * and cool biome lists at that weight. Here it takes a slice of the overworld's climate noise instead - at the
	 * default weight of 5, high-weirdness temperate land (weirdness 0.3 to 1, temperature 0.2 to 0.55, humidity 0 to
	 * 0.6); the slice's weirdness span grows in proportion to the weight, and once it covers everything, the humidity
	 * span grows too. Weight 0, or the option off, and there is none.
	 */
	public static void placeMagicalForest() {
		if (!ThaumcraftConfig.GENERATE_MAGIC_FOREST.get()) return;
		int weight = ThaumcraftConfig.BIOME_MAGICAL_FOREST_WEIGHT.get();
		if (weight <= 0) return;
		float span = 0.7f * weight / 5.0f;
		float weirdMin = Math.max(-1.0f, 1.0f - span);
		float overflow = Math.max(0.0f, span - 2.0f) / 2.0f;
		com.terraformersmc.biolith.api.biome.BiomePlacement.addOverworld(MAGICAL_FOREST, new Climate.ParameterPoint(
				Climate.Parameter.span(0.2f, 0.55f),
				Climate.Parameter.span(Math.max(-1.0f, 0.0f - overflow), Math.min(1.0f, 0.6f + overflow)),
				Climate.Parameter.span(0.0f, 1.0f),
				Climate.Parameter.span(-0.5f, 0.3f),
				Climate.Parameter.point(0.0f),
				Climate.Parameter.span(weirdMin, 1.0f),
				0L));
	}
}

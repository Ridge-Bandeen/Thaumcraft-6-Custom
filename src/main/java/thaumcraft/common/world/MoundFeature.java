package thaumcraft.common.world;

import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;

/**
 * Replaces the true original's own {@code WorldGenMound} - a rare (real 1-in-100-per-chunk-attempt, see
 * {@code ThaumcraftWorldGenerator#generateSurface}'s own real {@code random.nextInt(100) == 0}) surface
 * dungeon: a buried, mostly-cobblestone/dirt mound with 2 tiered loot spots, a dungeon chest (sometimes
 * trapped with buried TNT), and 2 monster spawners. Earlier work in this port dismissed this as "too big" by
 * file size alone (2560 decompiled lines) without reading it - re-checked directly this session (see
 * [[thaumcraft-1211-port]]) and it turns out to be purely mechanical: ~2441 static {@code setBlockState}
 * calls with only 5 truly dynamic positions, not algorithmic complexity. The static bulk was parsed straight
 * out of the real decompiled source and converted into a real modern structure NBT template
 * ({@code data/thaumcraft/structure/mound.nbt}, DataVersion 3955, 19x16x19), placed here via the real modern
 * {@link StructureTemplateManager}/{@link StructureTemplate#placeInWorld} API; only the 5 dynamic positions
 * (2 loot-tier blocks, the chest/TNT, the 2 spawners) are still placed programmatically below, with the
 * exact same real weighted-random logic and exact same real relative offsets as the true original.
 *
 * <p>{@link #isValidSpawn} replicates the true original's own real {@code LocationIsValidSpawn} 5-corner
 * check verbatim (stone/grass/dirt ground, optionally topped with a snow layer or short grass/fern) - one
 * real no-op branch in the original (an {@code isAirBlock} re-check that's always true given how
 * {@code distanceToAir} was derived) is deliberately not reproduced since it can never affect the result.
 *
 * <p>Real, deliberate simplification versus the true original: the original only ever rolled its rare
 * Cultist Portal (Lesser) surface spawn ({@link CultistPortalSpawnFeature}, already shipped) when a mound
 * did NOT spawn at that same roll ({@code else if}) - this port's 2 placed features roll independently since
 * modern placed-feature chance rolls don't share state across features, a minor frequency increase for the
 * portal spawn specifically that isn't worth the complexity of suppressing.
 */
public class MoundFeature extends Feature<MoundFeature.Config> {

	private static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "mound");
	/** The ground a mound may sit on ({@code #thaumcraft:mound_ground}; the original's stone, grass and dirt). */
	public static final net.minecraft.tags.TagKey<Block> GROUND = net.minecraft.tags.BlockTags.create(
			net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "mound_ground"));

	/**
	 * The configured feature's options, all optional ({@code "config": {}} is the original mound): a processor list
	 * run over the template as it is placed ({@code "processors"}), and the blocks used for the loot chest and its
	 * trapped variant ({@code "chest"}, {@code "trapped_chest"} - block states, vanilla's by default; any block whose
	 * block entity takes a loot table works).
	 */
	public record Config(java.util.Optional<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList>> processors,
			net.minecraft.world.level.block.state.BlockState chest, net.minecraft.world.level.block.state.BlockState trappedChest)
			implements net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration {
		public static final Codec<Config> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
				net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType.LIST_CODEC.optionalFieldOf("processors").forGetter(Config::processors),
				net.minecraft.world.level.block.state.BlockState.CODEC.optionalFieldOf("chest", Blocks.CHEST.defaultBlockState()).forGetter(Config::chest),
				net.minecraft.world.level.block.state.BlockState.CODEC.optionalFieldOf("trapped_chest", Blocks.TRAPPED_CHEST.defaultBlockState()).forGetter(Config::trappedChest))
				.apply(i, Config::new));
	}

	public MoundFeature(Codec<Config> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<Config> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		// The true original computed its own base position as `precipitationHeight.down(9)` before calling
		// WorldGenMound#generate - the structure's own real bounding box runs from that base up 15 blocks.
		BlockPos pos = context.origin().below(9);

		if (!isValidSpawn(level, pos.offset(9, 9, 9))
				|| !isValidSpawn(level, pos.offset(0, 9, 0))
				|| !isValidSpawn(level, pos.offset(18, 9, 0))
				|| !isValidSpawn(level, pos.offset(18, 9, 18))
				|| !isValidSpawn(level, pos.offset(0, 9, 18))) {
			return false;
		}

		StructureTemplateManager templates = level.getLevel().getStructureManager();
		StructureTemplate template = templates.getOrCreate(TEMPLATE);
		StructurePlaceSettings settings = new StructurePlaceSettings();
		context.config().processors().ifPresent(list -> list.value().list().forEach(settings::addProcessor));
		template.placeInWorld(level, pos, pos, settings, random, 2);

		placeLootTier(level, random, pos.offset(9, 1, 7));
		placeLootTier(level, random, pos.offset(9, 1, 11));
		placeChest(level, random, pos.offset(10, 1, 9), context.config());
		placeSpawner(level, random, pos.offset(4, 5, 4), EntityType.SKELETON);
		placeSpawner(level, random, pos.offset(4, 5, 14), EntityType.ZOMBIE);
		return true;
	}

	private boolean isValidSpawn(WorldGenLevel level, BlockPos pos) {
		int distanceToAir = 0;
		while (distanceToAir < 3 && !level.getBlockState(pos.above(distanceToAir)).isAir()) {
			distanceToAir++;
		}
		if (distanceToAir > 2) return false;

		BlockPos surfacePos = pos.above(distanceToAir - 1);
		Block surfaceBlock = level.getBlockState(surfacePos).getBlock();
		Block belowBlock = level.getBlockState(surfacePos.below()).getBlock();
		if (level.getBlockState(surfacePos).is(GROUND)) return true;
		return (surfaceBlock == Blocks.SNOW || surfaceBlock == Blocks.SHORT_GRASS || surfaceBlock == Blocks.FERN)
				&& level.getBlockState(surfacePos.below()).is(GROUND);
	}

	/** Real weighted tier roll, verbatim from the original: 10% rare / 23% uncommon / 67% common, each tier
	 *  independently 30% crate / 70% urn. */
	private void placeLootTier(WorldGenLevel level, RandomSource random, BlockPos pos) {
		float rr = random.nextFloat();
		Block block;
		if (rr < 0.1f) {
			block = random.nextFloat() < 0.3f ? BlocksTC.lootCrateRare.get() : BlocksTC.lootUrnRare.get();
		} else if (rr < 0.33f) {
			block = random.nextFloat() < 0.3f ? BlocksTC.lootCrateUncommon.get() : BlocksTC.lootUrnUncommon.get();
		} else {
			block = random.nextFloat() < 0.3f ? BlocksTC.lootCrateCommon.get() : BlocksTC.lootUrnCommon.get();
		}
		level.setBlock(pos, block.defaultBlockState(), 2);
	}

	/** Real 1-in-3 trapped chest (with buried TNT 2 blocks below) vs. plain chest, both using the real vanilla
	 *  {@code minecraft:chests/simple_dungeon} loot table. */
	private void placeChest(WorldGenLevel level, RandomSource random, BlockPos pos, Config config) {
		boolean trapped = random.nextInt(3) == 0;
		net.minecraft.world.level.block.state.BlockState chestState = trapped ? config.trappedChest() : config.chest();
		if (chestState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
			chestState = chestState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
		}
		level.setBlock(pos, chestState, 2);
		if (trapped) {
			level.setBlock(pos.below(2), Blocks.TNT.defaultBlockState(), 2);
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof net.minecraft.world.RandomizableContainer chest) {
			chest.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "chests/mound")));
			chest.setLootTableSeed(random.nextLong());
		}
	}

	private void placeSpawner(WorldGenLevel level, RandomSource random, BlockPos pos, EntityType<?> type) {
		level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 2);
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof SpawnerBlockEntity spawner) {
			// as vanilla's MonsterRoomFeature does: passing the live ServerLevel made the spawner read a block through
			// it from the worldgen thread, which waits on the server thread while the server waits on this chunk -
			// a deadlock that froze the game (every server-side click did nothing) once a new mound generated
			spawner.setEntityId(type, random);
		}
	}
}

package thaumcraft.api.world;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.world.WorldConfigHooks;
import thaumcraft.common.world.biomes.BiomeHandler;

/**
 * Where Thaumcraft asks what kind of place this is: a chunk's natural aura, the aspect of a crystal cluster, and
 * whether a spot counts as magical (Pech spawning) or as the Magical Forest or Eerie. By default it all reads the
 * biome, as the original. An addon whose world describes places some other way (per-chunk climate, regions) can
 * supply its own with {@link #set} - the last one set wins; set it during mod construction or setup.
 */
public interface WorldFlavour {

	/** A chunk's aura richness, about 0-1 (times the aura ceiling, with a little noise, is its starting vis).
	 * Default: the biome's aura modifier, averaged with the four neighbouring chunks'. */
	float auraModifier(Level level, ChunkPos chunk);

	/** An aspect for a crystal cluster here, or null for a random primal one. Default: one of the biome's aspects. */
	@Nullable
	Aspect randomAspect(LevelAccessor level, BlockPos pos, RandomSource random);

	/** Default: the biome is in {@code #c:is_magical}. */
	boolean isMagicalArea(LevelAccessor level, BlockPos pos);

	/** Default: the biome is the Magical Forest. */
	boolean isMagicalForest(LevelAccessor level, BlockPos pos);

	/** Default: the biome is Eerie. */
	boolean isEerieArea(LevelAccessor level, BlockPos pos);

	/** The biome-based flavour Thaumcraft ships. */
	WorldFlavour BIOMES = new WorldFlavour() {
		@Override
		public float auraModifier(Level level, ChunkPos pos) {
			float life = BiomeHandler.getAuraModifier(level.getBiome(new BlockPos(pos.x * 16 + 8, 50, pos.z * 16 + 8)));
			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				life += BiomeHandler.getAuraModifier(level.getBiome(new BlockPos((pos.x + dir.getStepX()) * 16 + 8, 50, (pos.z + dir.getStepZ()) * 16 + 8)));
			}
			return life / 5.0f;
		}

		@Override
		public Aspect randomAspect(LevelAccessor level, BlockPos pos, RandomSource random) {
			return BiomeHandler.getRandomAspect(level.getBiome(pos), random);
		}

		@Override
		public boolean isMagicalArea(LevelAccessor level, BlockPos pos) {
			return level.getBiome(pos).is(WorldConfigHooks.MAGICAL);
		}

		@Override
		public boolean isMagicalForest(LevelAccessor level, BlockPos pos) {
			return level.getBiome(pos).is(WorldConfigHooks.MAGICAL_FOREST);
		}

		@Override
		public boolean isEerieArea(LevelAccessor level, BlockPos pos) {
			return level.getBiome(pos).is(WorldConfigHooks.EERIE);
		}
	};

	static WorldFlavour get() {
		return Holder.current;
	}

	static void set(WorldFlavour flavour) {
		Holder.current = flavour;
	}

	final class Holder {
		private static volatile WorldFlavour current = BIOMES;

		private Holder() {
		}
	}
}

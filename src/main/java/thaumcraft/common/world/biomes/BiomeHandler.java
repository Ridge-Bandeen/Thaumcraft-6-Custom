package thaumcraft.common.world.biomes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.api.aspects.Aspect;

/**
 * Replaces the old (1.12.2) {@code BiomeHandler} - despite the name, this was never really about placing new
 * biomes (that's {@link thaumcraft.common.world.objects}, still a separate, larger, not-yet-ported arc). Its
 * real job, and the one {@link thaumcraft.common.world.aura.AuraHandler}'s own doc comment already flagged
 * as a real gap ("No per-biome variation on generation... the original weighted this by {@code
 * BiomeDictionary} tags... that isn't ported"), is a table of how magical a biome naturally is and which
 * {@link Aspect} it leans toward, keyed by the original's own {@code BiomeDictionary.Type} tags.
 *
 * <p>1.12.2 Forge's {@code BiomeDictionary.Type} has a clean, near-1:1 modern replacement: NeoForge's own
 * {@code c:}-namespaced common biome tags ({@link net.neoforged.neoforge.common.Tags.Biomes}), re-derived
 * directly against the true original's own {@code registerBiomeInfo} table rather than guessed - every real
 * entry there maps onto a real modern tag (down to {@code MAGICAL}/{@code DEAD}, both real modern tags kept
 * around specifically for backwards/mod compatibility with exactly this kind of use, per NeoForge's own
 * {@code NeoForgeBiomeTagsProvider}). {@code WATER} was dropped as redundant - it was never anything but a
 * strict subset of the original's own separate {@code OCEAN}/{@code RIVER} entries with the same aura level,
 * and modern tags don't carry a matching catch-all.
 *
 * <p>Real find worth recording from reading the original directly rather than trusting it at a glance: it
 * registers {@code DRY} <i>twice</i>, once under the FIRE grouping (0.25, matching HOT's own real "surface
 * heat" reasoning) and again later under the ENTROPY grouping (0.125) - real Java {@code HashMap} semantics
 * mean the second call silently overwrote the first, so the original's own real, final, in-play value for
 * {@code DRY} was always the ENTROPY one; the FIRE registration was dead on arrival. Reproduced faithfully
 * here as a single {@code IS_DRY} entry holding only that real final value, not both.
 *
 * <p>{@link #getAuraModifier} reproduces the original's own real <i>averaging</i> behavior exactly (a biome
 * with several matching tags averages every one of their levels, not just the first or strongest match).
 * {@link #getGreatwoodChance}/{@link #getRandomAspect} are real, deliberate simplifications of the other two
 * columns rather than literal ports: the original iterated the biome's own <i>external</i> full tag set (not
 * this table's order) and, the moment it hit even one real tag outside the table, either silently returned 0
 * ({@code getBiomeSupportsGreatwood}) or aborted the whole call to {@code null}
 * ({@code getRandomBiomeTag}) - a real, almost certainly unintentional fragility (a single untracked tag
 * anywhere on an otherwise well-matched biome could zero out its whole result) that modern tags have no
 * direct way to reproduce anyway (nothing hands back "every tag this biome carries" as a plain enumerable
 * list the way {@code BiomeDictionary.getTypes} did). Both pick from real matches within this table's own
 * fixed order instead - first match for {@link #getGreatwoodChance}, uniformly random for
 * {@link #getRandomAspect} (see its own doc comment) - which is what the original's real intent was aiming
 * for, not its incidental bug.
 */
public final class BiomeHandler {

	/** One real row of the true original's own {@code registerBiomeInfo} table: how much aura a biome with
	 * this tag naturally has, which {@link Aspect} it leans toward, and (for a few real tags) how much more
	 * likely a Greatwood tree is to spring up here than the baseline chance. */
	private record BiomeAspectInfo(float auraModifier, Aspect aspect, float greatwoodChance) {
	}

	/** Re-derived verbatim from the true original's own {@code BiomeHandler#registerBiomeInfo} calls (one
	 * real entry per distinct tag - see this class's own doc comment for the {@code DRY}-overwrite quirk and
	 * the real {@code BiomeDictionary.Type} -&gt; modern {@code c:} tag mapping). A real {@link LinkedHashMap}
	 * so {@link #getGreatwoodChance}'s own real first-match behavior has a fixed, meaningful order to work
	 * from (registration order here), rather than depending on incidental {@code HashMap} iteration order the
	 * way the original effectively did. */
	private static final Map<TagKey<Biome>, BiomeAspectInfo> TABLE = new LinkedHashMap<>();

	static {
		TABLE.put(Tags.Biomes.IS_OCEAN, new BiomeAspectInfo(0.33f, Aspect.WATER, 0f));
		TABLE.put(Tags.Biomes.IS_RIVER, new BiomeAspectInfo(0.4f, Aspect.WATER, 0f));
		TABLE.put(Tags.Biomes.IS_WET, new BiomeAspectInfo(0.4f, Aspect.WATER, 0f));
		TABLE.put(Tags.Biomes.IS_LUSH, new BiomeAspectInfo(0.5f, Aspect.WATER, 0.5f));
		TABLE.put(Tags.Biomes.IS_HOT, new BiomeAspectInfo(0.33f, Aspect.FIRE, 0f));
		TABLE.put(Tags.Biomes.IS_NETHER, new BiomeAspectInfo(0.125f, Aspect.FIRE, 0f));
		TABLE.put(Tags.Biomes.IS_BADLANDS, new BiomeAspectInfo(0.33f, Aspect.FIRE, 0f));
		TABLE.put(Tags.Biomes.IS_SPOOKY, new BiomeAspectInfo(0.5f, Aspect.FIRE, 0f));
		TABLE.put(Tags.Biomes.IS_DENSE_VEGETATION, new BiomeAspectInfo(0.4f, Aspect.ORDER, 0f));
		TABLE.put(Tags.Biomes.IS_SNOWY, new BiomeAspectInfo(0.25f, Aspect.ORDER, 0f));
		TABLE.put(Tags.Biomes.IS_COLD, new BiomeAspectInfo(0.25f, Aspect.ORDER, 0f));
		TABLE.put(Tags.Biomes.IS_MUSHROOM, new BiomeAspectInfo(0.75f, Aspect.ORDER, 0f));
		TABLE.put(Tags.Biomes.IS_MAGICAL, new BiomeAspectInfo(0.75f, Aspect.ORDER, 1.0f));
		TABLE.put(Tags.Biomes.IS_CONIFEROUS_TREE, new BiomeAspectInfo(0.33f, Aspect.EARTH, 0.2f));
		TABLE.put(Tags.Biomes.IS_FOREST, new BiomeAspectInfo(0.5f, Aspect.EARTH, 1.0f));
		TABLE.put(Tags.Biomes.IS_SANDY, new BiomeAspectInfo(0.25f, Aspect.EARTH, 0f));
		TABLE.put(Tags.Biomes.IS_BEACH, new BiomeAspectInfo(0.3f, Aspect.EARTH, 0f));
		TABLE.put(Tags.Biomes.IS_JUNGLE, new BiomeAspectInfo(0.6f, Aspect.EARTH, 0f));
		TABLE.put(Tags.Biomes.IS_SAVANNA, new BiomeAspectInfo(0.25f, Aspect.AIR, 0.2f));
		TABLE.put(Tags.Biomes.IS_MOUNTAIN, new BiomeAspectInfo(0.3f, Aspect.AIR, 0f));
		TABLE.put(Tags.Biomes.IS_HILL, new BiomeAspectInfo(0.33f, Aspect.AIR, 0f));
		TABLE.put(Tags.Biomes.IS_PLAINS, new BiomeAspectInfo(0.3f, Aspect.AIR, 0.2f));
		TABLE.put(Tags.Biomes.IS_END, new BiomeAspectInfo(0.125f, Aspect.AIR, 0f));
		TABLE.put(Tags.Biomes.IS_DRY, new BiomeAspectInfo(0.125f, Aspect.ENTROPY, 0f));
		TABLE.put(Tags.Biomes.IS_SPARSE_VEGETATION, new BiomeAspectInfo(0.2f, Aspect.ENTROPY, 0f));
		TABLE.put(Tags.Biomes.IS_SWAMP, new BiomeAspectInfo(0.5f, Aspect.ENTROPY, 0.2f));
		TABLE.put(Tags.Biomes.IS_WASTELAND, new BiomeAspectInfo(0.125f, Aspect.ENTROPY, 0f));
		TABLE.put(Tags.Biomes.IS_DEAD, new BiomeAspectInfo(0.1f, Aspect.ENTROPY, 0f));
	}

	/** The real original's own {@code catch} fallback - used any time a biome carries none of the real tags
	 * in {@link #TABLE} at all. */
	private static final float DEFAULT_AURA_MODIFIER = 0.5f;

	/** Averages {@link BiomeAspectInfo#auraModifier} across every real tag {@code biome} carries that's
	 * actually in {@link #TABLE} - the real weight {@link thaumcraft.common.world.aura.AuraHandler} seeds a
	 * freshly-generated chunk's baseline aura with, matching the true original's own real averaging exactly.
	 * Falls back to {@link #DEFAULT_AURA_MODIFIER} for a biome that matches nothing (the original's own real
	 * {@code catch} behavior, not a made-up default). */
	public static float getAuraModifier(Holder<Biome> biome) {
		float total = 0f;
		int count = 0;
		for (Map.Entry<TagKey<Biome>, BiomeAspectInfo> entry : TABLE.entrySet()) {
			if (biome.is(entry.getKey())) {
				total += entry.getValue().auraModifier();
				count++;
			}
		}
		return count > 0 ? total / count : DEFAULT_AURA_MODIFIER;
	}

	/** Real, deliberate simplification of the true original's own {@code getRandomBiomeTag}: that method
	 * picked uniformly at random from the biome's <i>entire</i> real tag set (not just the ones this table
	 * tracks), so a biome tagged with even one real type outside the table had a real chance of the pick
	 * landing on it - which, since only a looked-up-but-absent entry was ever handled, silently aborted the
	 * <i>whole call</i> back to {@code null} (a single untracked tag anywhere in the biome's real tag set
	 * could zero out an otherwise well-matched biome's real aspect entirely). Modern biome tags don't expose
	 * "every tag this biome carries" as a plain list the way {@code BiomeDictionary.getTypes} did, and
	 * reproducing that fragility deliberately isn't worth it for what's ultimately flavor (which Aspect a
	 * random crystal vein leans toward) - this picks uniformly at random among only the real tags {@code
	 * biome} actually matches in {@link #TABLE}, which is every case the original's own real intent (not its
	 * incidental bug) was aiming for. {@code null} only if literally nothing in the table matches. */
	public static Aspect getRandomAspect(Holder<Biome> biome, RandomSource random) {
		List<Aspect> matches = new ArrayList<>();
		for (Map.Entry<TagKey<Biome>, BiomeAspectInfo> entry : TABLE.entrySet()) {
			if (biome.is(entry.getKey())) {
				matches.add(entry.getValue().aspect());
			}
		}
		return matches.isEmpty() ? null : matches.get(random.nextInt(matches.size()));
	}

	/** The first real matching tag's Greatwood-tree bonus chance, in {@link #TABLE}'s own real registration
	 * order - same real first-match behavior as {@link #getRandomAspect}. 0 (never a bonus) if nothing
	 * real matches. */
	public static float getGreatwoodChance(Holder<Biome> biome) {
		for (Map.Entry<TagKey<Biome>, BiomeAspectInfo> entry : TABLE.entrySet()) {
			if (biome.is(entry.getKey())) {
				return entry.getValue().greatwoodChance();
			}
		}
		return 0f;
	}

	private BiomeHandler() {
	}
}

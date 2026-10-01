package thaumcraft.common.registry;

import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import thaumcraft.Thaumcraft;

/**
 * {@link TreeGrower}s for this port's own trees - referenced by {@code BlocksTC.saplingSilverwood}'s
 * {@code SaplingBlock}, resolved against the real registered {@code ConfiguredFeature} at grow time (a
 * mod-owned {@code TreeGrower} works the same way vanilla's own {@code TreeGrower.OAK} etc. do - a plain
 * {@link ResourceKey} the grower looks up via {@code level.registryAccess()}, not something that needs its
 * own registration event). The shapes are the original's generators, ported as GreatwoodTreeFeature and
 * SilverwoodTreeFeature; the configured features used here are the sapling versions (no spider nests or flowers).
 */
public final class ThaumcraftTreeGrowers {

	public static final ResourceKey<ConfiguredFeature<?, ?>> SILVERWOOD_TREE = ResourceKey.create(
			Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "silverwood_tree"));

	public static final TreeGrower SILVERWOOD = new TreeGrower(
			"thaumcraft_silverwood", Optional.empty(), Optional.of(SILVERWOOD_TREE), Optional.empty());

	public static final ResourceKey<ConfiguredFeature<?, ?>> GREATWOOD_TREE = ResourceKey.create(
			Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "greatwood_tree"));

	/** A mega tree only, as the original: a Greatwood grows from a 2x2 of saplings and a lone sapling never grows. */
	public static final TreeGrower GREATWOOD = new TreeGrower(
			"thaumcraft_greatwood", Optional.of(GREATWOOD_TREE), Optional.empty(), Optional.empty());

	private ThaumcraftTreeGrowers() {
	}
}

package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.common.world.CrystalClusterFeature;
import thaumcraft.common.world.CultistPortalSpawnFeature;
import thaumcraft.common.world.MoundFeature;

/** Real modern {@link Feature} registrations - see each feature's own doc comment for what it replaces. */
public final class ThaumcraftFeatures {

	public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, "thaumcraft");

	public static final DeferredHolder<Feature<?>, CrystalClusterFeature> CRYSTAL_CLUSTER = FEATURES.register("crystal_cluster",
			() -> new CrystalClusterFeature(NoneFeatureConfiguration.CODEC));

	public static final DeferredHolder<Feature<?>, CultistPortalSpawnFeature> CULTIST_PORTAL_SPAWN = FEATURES.register("cultist_portal_spawn",
			() -> new CultistPortalSpawnFeature(NoneFeatureConfiguration.CODEC));

	public static final DeferredHolder<Feature<?>, thaumcraft.common.world.AmbientGrassFeature> AMBIENT_GRASS = FEATURES.register("ambient_grass",
			() -> new thaumcraft.common.world.AmbientGrassFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
	public static final DeferredHolder<Feature<?>, MoundFeature> MOUND = FEATURES.register("mound",
			() -> new MoundFeature(MoundFeature.Config.CODEC));

	public static final DeferredHolder<Feature<?>, thaumcraft.common.world.GreatwoodTreeFeature> GREATWOOD_TREE = FEATURES.register("greatwood_tree",
			() -> new thaumcraft.common.world.GreatwoodTreeFeature(thaumcraft.common.world.GreatwoodTreeFeature.Config.CODEC));

	public static final DeferredHolder<Feature<?>, thaumcraft.common.world.SilverwoodTreeFeature> SILVERWOOD_TREE = FEATURES.register("silverwood_tree",
			() -> new thaumcraft.common.world.SilverwoodTreeFeature(thaumcraft.common.world.SilverwoodTreeFeature.Config.CODEC));

	private ThaumcraftFeatures() {
	}
}

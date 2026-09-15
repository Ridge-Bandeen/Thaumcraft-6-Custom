package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.common.world.CrystalClusterFeature;

/** Real modern {@link Feature} registrations - see each feature's own doc comment for what it replaces. */
public final class ThaumcraftFeatures {

	public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, "thaumcraft");

	public static final DeferredHolder<Feature<?>, CrystalClusterFeature> CRYSTAL_CLUSTER = FEATURES.register("crystal_cluster",
			() -> new CrystalClusterFeature(NoneFeatureConfiguration.CODEC));

	private ThaumcraftFeatures() {
	}
}

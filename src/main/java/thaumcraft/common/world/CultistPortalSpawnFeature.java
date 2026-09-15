package thaumcraft.common.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the true original's own real, rare Cultist Portal (Lesser) surface spawn - the other real half of
 * {@code ThaumcraftWorldGenerator#generateSurface} alongside the mound generator (a real, ~2500-line hand-
 * placed block schematic in the original's own decompiled source - genuinely a separate, much larger, low-
 * value-for-effort chunk on its own, deliberately not tackled here). This one's small: a rare surface
 * {@link thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser} spawn, and the entity itself was
 * already real and fully shipped (see its own doc comment / {@code cultistPortalLesserActivatesConjuresMinionsAndExplodesOnDeath})
 * with no natural spawn trigger anywhere - this is that trigger.
 *
 * <p>Real, deliberate simplification of the true original's own surface-material check ({@code
 * Material.GROUND}/{@code ROCK}/{@code SAND}/{@code SNOW}, none of which exist any more as a concept) - real
 * modern equivalent: the block below the target position is a real sturdy, non-liquid solid, and the target
 * position itself is real air, the same general "can something stand here" test this port already uses
 * elsewhere (see {@code BlockCrystal#findSturdyFacing}'s own sturdy-face check for the same real pattern).
 */
public class CultistPortalSpawnFeature extends Feature<NoneFeatureConfiguration> {

	public CultistPortalSpawnFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos pos = context.origin();

		BlockState ground = level.getBlockState(pos.below());
		if (!ground.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP) || !ground.getFluidState().isEmpty()) return false;
		if (!level.getBlockState(pos).isAir()) return false;

		var portal = ThaumcraftEntities.CULTIST_PORTAL_LESSER.get().create(level.getLevel());
		if (portal == null) return false;
		portal.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
		return level.addFreshEntity(portal);
	}
}

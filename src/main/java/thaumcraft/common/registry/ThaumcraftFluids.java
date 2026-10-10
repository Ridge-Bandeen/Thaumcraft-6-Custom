package thaumcraft.common.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;

/**
 * Real modern {@link FluidType}/{@link Fluid} registrations - see {@link thaumcraft.common.blocks.misc.BlockLiquidDeath}'s
 * own doc comment for the full real story. This is the first real vanilla-style (infinite source-block model)
 * fluid this port has needed - {@code BlockFluxGoo}/{@code BlockPurifyingFluid} both deliberately used a
 * from-scratch finite-quanta {@code Block} instead (see their own doc comments) because the true original's
 * own base class for THOSE two was Forge's old {@code BlockFluidFinite}, which has no modern equivalent.
 * {@code liquid_death}'s own true original base was {@code BlockFluidClassic} instead - Forge's OLD
 * infinite-source-block convenience wrapper, the direct ancestor of modern vanilla's own real
 * {@link net.minecraft.world.level.material.FlowingFluid} model - so this one gets the real modern API.
 *
 * <p>Property choices, real and deliberate: no natural light (the original never emitted any, unlike lava);
 * {@code canSwim(false)}/{@code canDrown(false)}/{@code pathType(LAVA)} match the original's own real "thick,
 * slows you down" feel (matching lava's own real defaults) rather than water's swimmable one; density/
 * viscosity/temperature are moderate (a thick purple ooze, not molten rock) since the original never exposed
 * real numeric equivalents to carry over exactly. Reuses the original's own real {@code animatedglow} texture
 * for both still and flowing (the original's own real {@code FluidDeath} definition used the exact same
 * texture for both too), no tint (the original's Forge {@code Fluid} definition never set one either).
 */
public final class ThaumcraftFluids {

	public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Thaumcraft.MODID);
	public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, Thaumcraft.MODID);

	public static final DeferredHolder<FluidType, FluidType> LIQUID_DEATH_TYPE = FLUID_TYPES.register("liquid_death",
			() -> new FluidType(FluidType.Properties.create()
					.lightLevel(0)
					.density(1500)
					.viscosity(2000)
					.temperature(300)
					.canSwim(false)
					.canDrown(false)
					.canExtinguish(false)
					.pathType(PathType.LAVA)
					.adjacentPathType(null)
					.sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
					.sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)));

	public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_DEATH = FLUIDS.register("liquid_death",
			() -> new BaseFlowingFluid.Source(fluidProperties()));

	public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> LIQUID_DEATH_FLOWING = FLUIDS.register("flowing_liquid_death",
			() -> new BaseFlowingFluid.Flowing(fluidProperties()));

	/** The original FluidPure: light 5, rare, water-like (Forge fluid defaults), but its material was not water, so no
	 * swimming, drowning or putting out fires. */
	public static final DeferredHolder<FluidType, FluidType> PURIFYING_FLUID_TYPE = FLUID_TYPES.register("purifying_fluid",
			() -> new FluidType(FluidType.Properties.create()
					.lightLevel(5)
					.rarity(net.minecraft.world.item.Rarity.RARE)
					.canSwim(false)
					.canDrown(false)
					.canExtinguish(false)
					.sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
					.sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

	public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PURIFYING_FLUID = FLUIDS.register("purifying_fluid",
			() -> new BaseFlowingFluid.Source(pureProperties()));

	public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> PURIFYING_FLUID_FLOWING = FLUIDS.register("flowing_purifying_fluid",
			() -> new BaseFlowingFluid.Flowing(pureProperties()));

	/** As the original BlockFluidClassic: spreads like water (8 quanta, one lost per block), never makes new sources. */
	private static BaseFlowingFluid.Properties pureProperties() {
		return new BaseFlowingFluid.Properties(PURIFYING_FLUID_TYPE, PURIFYING_FLUID, PURIFYING_FLUID_FLOWING)
				.slopeFindDistance(4)
				.levelDecreasePerBlock(1)
				.explosionResistance(100f)
				.block(BlocksTC.purifyingFluid)
				.bucket(ItemsTC.bucketPure);
	}

	private static BaseFlowingFluid.Properties fluidProperties() {
		return new BaseFlowingFluid.Properties(LIQUID_DEATH_TYPE, LIQUID_DEATH, LIQUID_DEATH_FLOWING)
				.slopeFindDistance(2)
				.levelDecreasePerBlock(2)
				.explosionResistance(100f)
				.block(BlocksTC.liquidDeath)
				.bucket(ItemsTC.bucketDeath);
	}

	private ThaumcraftFluids() {
	}
}

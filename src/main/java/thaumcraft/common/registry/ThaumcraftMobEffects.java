package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.potions.MobEffectFluxTaint;
import thaumcraft.api.potions.MobEffectVisExhaust;
import thaumcraft.common.lib.potions.MobEffectInfectiousVisExhaust;

/**
 * First real registration of a {@link MobEffect} this port has needed. {@code MobEffectFluxTaint} itself
 * was already ported early this session (see its own doc comment) but had no registry entry and no
 * consumer until {@link thaumcraft.common.entities.projectile.EntityBottleTaint}.
 *
 * {@code MobEffectVisExhaust} (ported the same pass as {@code MobEffectFluxTaint}) sat unregistered for a
 * while, per this doc comment's own original note ("add it here once something actually applies it") -
 * {@link MobEffectInfectiousVisExhaust} (an {@code EntityFluxRift} rift-event prerequisite) is that real
 * consumer, and needs both registered together: it hands infected entities off to plain
 * {@code VIS_EXHAUST} once its own decaying amplifier chain bottoms out.
 */
public final class ThaumcraftMobEffects {

	public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, "thaumcraft");

	public static final DeferredHolder<MobEffect, MobEffectFluxTaint> FLUX_TAINT = MOB_EFFECTS.register("flux_taint", MobEffectFluxTaint::new);

	public static final DeferredHolder<MobEffect, MobEffectVisExhaust> VIS_EXHAUST = MOB_EFFECTS.register("vis_exhaust", MobEffectVisExhaust::new);

	public static final DeferredHolder<MobEffect, MobEffectInfectiousVisExhaust> INFECTIOUS_VIS_EXHAUST =
			MOB_EFFECTS.register("infectious_vis_exhaust", MobEffectInfectiousVisExhaust::new);

	/** The 6 real Warp-misfortune effects - see {@code thaumcraft.common.lib.events.WarpEvents#checkWarpEvent}. */
	public static final DeferredHolder<MobEffect, thaumcraft.common.lib.potions.MobEffectThaumarhia> THAUMARHIA =
			MOB_EFFECTS.register("thaumarhia", thaumcraft.common.lib.potions.MobEffectThaumarhia::new);
	public static final DeferredHolder<MobEffect, thaumcraft.common.lib.potions.MobEffectUnnaturalHunger> UNNATURAL_HUNGER =
			MOB_EFFECTS.register("unnatural_hunger", thaumcraft.common.lib.potions.MobEffectUnnaturalHunger::new);
	public static final DeferredHolder<MobEffect, thaumcraft.common.lib.potions.MobEffectBlurredVision> BLURRED_VISION =
			MOB_EFFECTS.register("blurred_vision", thaumcraft.common.lib.potions.MobEffectBlurredVision::new);
	public static final DeferredHolder<MobEffect, thaumcraft.common.lib.potions.MobEffectSunScorned> SUN_SCORNED =
			MOB_EFFECTS.register("sun_scorned", thaumcraft.common.lib.potions.MobEffectSunScorned::new);
	public static final DeferredHolder<MobEffect, thaumcraft.common.lib.potions.MobEffectDeathGaze> DEATH_GAZE =
			MOB_EFFECTS.register("death_gaze", thaumcraft.common.lib.potions.MobEffectDeathGaze::new);
	public static final DeferredHolder<MobEffect, thaumcraft.common.lib.potions.MobEffectWarpWard> WARP_WARD =
			MOB_EFFECTS.register("warp_ward", thaumcraft.common.lib.potions.MobEffectWarpWard::new);

	/** The original's {@code getCurativeItems().clear()}: milk won't cure this instance (warp curses, vis exhaustion
	 * from flux). */
	public static net.minecraft.world.effect.MobEffectInstance milkProof(net.minecraft.world.effect.MobEffectInstance instance) {
		instance.getCures().clear();
		return instance;
	}

	private ThaumcraftMobEffects() {
	}
}

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

	private ThaumcraftMobEffects() {
	}
}

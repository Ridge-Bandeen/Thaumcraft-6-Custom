package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.potions.MobEffectFluxTaint;

/**
 * First real registration of a {@link MobEffect} this port has needed. {@code MobEffectFluxTaint} itself
 * was already ported early this session (see its own doc comment) but had no registry entry and no
 * consumer until {@link thaumcraft.common.entities.projectile.EntityBottleTaint}. {@code MobEffectVisExhaust}
 * (its sibling, ported the same pass) still has no real consumer anywhere in this port, so it's left
 * unregistered rather than added speculatively - add it here the same way once something actually applies it.
 */
public final class ThaumcraftMobEffects {

	public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, "thaumcraft");

	public static final DeferredHolder<MobEffect, MobEffectFluxTaint> FLUX_TAINT = MOB_EFFECTS.register("flux_taint", MobEffectFluxTaint::new);

	private ThaumcraftMobEffects() {
	}
}

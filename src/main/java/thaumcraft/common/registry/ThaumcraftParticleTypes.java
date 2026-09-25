package thaumcraft.common.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.Thaumcraft;

/**
 * Particle types this mod registers. Currently only {@link #NONE}: a deliberately provider-less particle type -
 * no {@code ParticleProvider} is ever registered for it client-side, so {@code Level#addParticle} with it is a
 * silent no-op ({@code ParticleEngine} skips a null provider). It exists so a vanilla mob whose class hard-codes
 * a particle burst can be told to spawn "nothing" through its own overridable hook - see
 * {@link thaumcraft.common.entities.monster.EntityThaumicSlime#getParticleType}, added 2026-09-25 when the
 * first playtest showed vanilla's green slimeball burst firing on every landing alongside the mod's own purple one.
 */
public final class ThaumcraftParticleTypes {

	public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, Thaumcraft.MODID);

	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NONE = PARTICLE_TYPES.register("none", () -> new SimpleParticleType(false));

	private ThaumcraftParticleTypes() {
	}
}

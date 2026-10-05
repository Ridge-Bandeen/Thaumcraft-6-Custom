package thaumcraft.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.Thaumcraft;

/**
 * {@link thaumcraft.client.fx.particles.FXGeneric}'s see-through layers (the original ParticleEngine's 2 and 3) turn
 * the depth test off for their draw; the particle pass starts with it on, so it is put back on afterwards, as the
 * original's engine did after its layers.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ParticleDepthRestore {

	private ParticleDepthRestore() {
	}

	@SubscribeEvent
	static void afterParticles(RenderLevelStageEvent event) {
		if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) RenderSystem.enableDepthTest();
	}
}

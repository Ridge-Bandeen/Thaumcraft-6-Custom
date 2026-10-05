package thaumcraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.client.fx.GleTube;
import thaumcraft.client.fx.particles.FXVoidStream;

/**
 * Draws the live {@link FXVoidStream}s after the particles, as the original's FXVoidStream#renderParticle: through the
 * ender shader, an additive pass at 1.5 times the radius with depth writes off, then the core at half the radius;
 * GLE's three-sided tube with caps, culling on.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class VoidStreamRenderer {

	private VoidStreamRenderer() {
	}

	@SubscribeEvent
	static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FXVoidStream.LIVE.isEmpty()) return;
		Vec3 cam = event.getCamera().getPosition();
		PoseStack poseStack = event.getPoseStack();
		for (FXVoidStream fx : FXVoidStream.LIVE.toArray(new FXVoidStream[0])) {
			if (fx.points == null || fx.points.length <= 2 || !fx.isAlive()) continue;
			poseStack.pushPose();
			poseStack.translate(fx.startX - cam.x, fx.startY - cam.y, fx.startZ - cam.z);
			PoseStack.Pose pose = poseStack.last();
			for (int q = 0; q <= 1; q++) {
				RenderSystem.depthMask(q >= 1);
				BufferBuilder builder = EnderShader.begin(VertexFormat.Mode.QUADS, q < 1);
				if (builder == null) break;
				RenderSystem.enableCull();
				float m = 1.5f - q;
				double[] r2 = new double[fx.radii.length];
				for (int i = 0; i < r2.length; i++) r2[i] = fx.radii[i] * m;
				GleTube.polyCone((x, y, z, u, v, r, g, b, a) -> builder.addVertex(pose, (float) x, (float) y, (float) z),
						fx.points, fx.colours, r2, 3, 0.075f, 0.0f, true, false);
				EnderShader.end(builder);
			}
			RenderSystem.depthMask(true);
			poseStack.popPose();
		}
	}
}

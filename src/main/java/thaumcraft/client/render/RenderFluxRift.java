package thaumcraft.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.items.WornGearHelper;
import thaumcraft.common.entities.EntityFluxRift;

/**
 * Renders {@link EntityFluxRift}'s real wobbling, tapered tendril mesh - a real, faithful-in-spirit port of
 * the true original's own real {@code RenderFluxRift}, painted through the same real {@link EnderShader} as
 * {@link TileHoleRenderer}/{@link TileVoidSiphonRenderer}.
 *
 * <p>As the original the tube is GLE's {@code glePolyCone} ({@link thaumcraft.client.fx.GleTube}): six sides, angle
 * joins, no caps, from the second point to the second-last, drawn with culling on. Each point wobbles on three sine
 * waves phased by its index and the radius pulses ({@code 1 - sin(t/8) * 0.1}), both scaled by how unstable the rift is;
 * three glow passes at 1.25, 1.75 and 2.25 times the width with depth writes off, then the core.
 *
 * <p>Blend functions match the original too: the 3 glow passes are ADDITIVE ({@code SRC_ALPHA, ONE}), only the
 * core is plain alpha blend. That difference matters even though the shader's output alpha is always 1
 * (found 2026-09-25 - an earlier note here called it a no-op): additive is dst+src, a glow that never hides
 * what is behind it, whereas plain blend at alpha 1 is an opaque replace - so with goggles' depth-test-off
 * outer pass the whole tube showed as a solid shape through terrain instead of a faint halo.
 */
public class RenderFluxRift extends EntityRenderer<EntityFluxRift> {

	private static final int SIDES = 6;
	private static final float[] PASS_RADIUS_SCALE = { 1.25f, 1.75f, 2.25f, 1.0f };

	public RenderFluxRift(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityFluxRift entity) {
		return ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");
	}

	@Override
	public void render(EntityFluxRift entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		List<Vec3> points = entity.points;
		List<Float> widths = entity.pointsWidth;
		if (points.size() < 3) return;

		float stability = Mth.clamp(1.0f - entity.getRiftStability() / 50.0f, 0.0f, 1.5f);
		float age = entity.tickCount + partialTick;

		int n = points.size();
		Vec3[] wobbled = new Vec3[n];
		double[] pulse = new double[n];
		float[][] colours = new float[n][];
		for (int i = 0; i < n; i++) {
			float var = age;
			int half = n / 2;
			if (i > half) {
				var -= i * 10;
			} else if (i < half) {
				var += i * 10;
			}
			Vec3 p = points.get(i);
			wobbled[i] = new Vec3(
					p.x + Math.sin(var / 50.0f) * 0.1 * stability,
					p.y + Math.sin(var / 60.0f) * 0.1 * stability,
					p.z + Math.sin(var / 70.0f) * 0.1 * stability);
			pulse[i] = 1.0 - Math.sin(var / 8.0f) * 0.1 * stability;
			colours[i] = new float[] { 1, 1, 1, 1 };
		}

		// Real original behaviour: with Goggles of Revealing (or anything else IGoggles) the OUTERMOST glow pass
		// is drawn with depth testing off, so the rift shows through walls; without goggles it's hidden by
		// terrain like anything else. Only that first pass - the inner passes and the core stay depth-tested.
		boolean goggles = WornGearHelper.hasGoggles(net.minecraft.client.Minecraft.getInstance().player);

		PoseStack.Pose pose = poseStack.last();
		for (int pass = 0; pass < PASS_RADIUS_SCALE.length; pass++) {
			boolean core = pass == PASS_RADIUS_SCALE.length - 1;
			RenderSystem.depthMask(core);

			BufferBuilder builder = EnderShader.begin(VertexFormat.Mode.QUADS, !core);
			if (builder == null) break;
			RenderSystem.enableCull(); // the original drew the rift with culling on
			if (pass == 0 && goggles) {
				RenderSystem.disableDepthTest();
			}
			double[] radii = new double[n];
			for (int i = 0; i < n; i++) radii[i] = widths.get(i) * pulse[i] * PASS_RADIUS_SCALE[pass];
			thaumcraft.client.fx.GleTube.polyCone((x, y, z, u, v, r, g, b, a) -> builder.addVertex(pose, (float) x, (float) y, (float) z),
					wobbled, colours, radii, SIDES, 1.0f, 0.0f, false, false);

			EnderShader.end(builder);
		}

		RenderSystem.depthMask(true);
	}
}

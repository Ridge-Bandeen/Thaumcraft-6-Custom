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
import thaumcraft.common.entities.EntityFluxRift;

/**
 * Renders {@link EntityFluxRift}'s real wobbling, tapered tendril mesh - a real, faithful-in-spirit port of
 * the true original's own real {@code RenderFluxRift}, painted through the same real {@link EnderShader} as
 * {@link TileHoleRenderer}/{@link TileVoidSiphonRenderer}.
 *
 * <p>Real, genuine architectural substitution, not a simplification of intent: the true original's own real
 * tube geometry came from a real third-party tessellation library ({@code com.sasmaster.glelwjgl}'s own real
 * {@code glePolyCone} - a generalized tapered-cylinder mesher along a real point/radius list), which has no
 * modern equivalent and isn't itself part of this port. This builds the same real shape (a smooth, 6-sided
 * tube through {@link EntityFluxRift#points}, tapering per {@link EntityFluxRift#pointsWidth}) with a real,
 * standard ring-extrusion algorithm instead: a running, minimally-rotated ("parallel-transport-style")
 * orthonormal frame at each point avoids the visible twisting a naive fixed-world-up reference would produce
 * whenever the tendril's own path turns through vertical. Same real per-point wobble formula as the true
 * original's own real render loop (three independent sine waves per axis, phase-offset by point index,
 * scaled by {@link EntityFluxRift#getRiftStability}), and the same real 4-pass layered draw (3 "glow" passes
 * at increasing radius with depth-write disabled, one "core" pass at the tendril's own real width with
 * depth-write enabled) for the same real soft-glow look.
 *
 * <p>Real, disclosed simplification: the true original's own real per-pass blend-function difference is
 * dropped, same real reasoning as {@link TileVoidSiphonRenderer}'s own doc comment (the shader's own real
 * fragment output is unconditionally opaque, so blend function choice has zero real visual effect).
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

		Vec3[] wobbled = new Vec3[points.size()];
		for (int i = 0; i < points.size(); i++) {
			float var = age;
			int half = points.size() / 2;
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
		}

		Vec3[] tangents = computeTangents(wobbled);
		Vec3[] rights = computeRights(tangents);

		PoseStack.Pose pose = poseStack.last();
		for (int pass = 0; pass < PASS_RADIUS_SCALE.length; pass++) {
			boolean core = pass == PASS_RADIUS_SCALE.length - 1;
			RenderSystem.depthMask(core);

			BufferBuilder builder = EnderShader.begin(VertexFormat.Mode.QUADS);
			if (builder == null) break;

			for (int i = 0; i < wobbled.length - 1; i++) {
				float w0 = widths.get(i) * PASS_RADIUS_SCALE[pass];
				float w1 = widths.get(i + 1) * PASS_RADIUS_SCALE[pass];
				emitRingSegment(builder, pose, wobbled[i], tangents[i], rights[i], w0, wobbled[i + 1], tangents[i + 1], rights[i + 1], w1);
			}

			EnderShader.end(builder);
		}

		RenderSystem.depthMask(true);
	}

	/** One direction per point - the true original's own real {@code Vec3d#rotatePitch/rotateYaw}-driven
	 * tangent isn't reproduced verbatim (see {@link EntityFluxRift}'s own doc comment for why those methods
	 * needed reimplementing at all); a simple, real central-difference tangent (direction from the previous
	 * point to the next) is used instead, standard for spline-tube generation and visually equivalent for a
	 * smooth point sequence like this one. */
	private static Vec3[] computeTangents(Vec3[] points) {
		Vec3[] tangents = new Vec3[points.length];
		for (int i = 0; i < points.length; i++) {
			Vec3 prev = points[Math.max(i - 1, 0)];
			Vec3 next = points[Math.min(i + 1, points.length - 1)];
			Vec3 dir = next.subtract(prev);
			tangents[i] = dir.lengthSqr() > 1.0E-8 ? dir.normalize() : new Vec3(0.0, 1.0, 0.0);
		}
		return tangents;
	}

	/** A running "right" reference vector per point, each derived from the previous one by projecting it onto
	 * the new tangent's own perpendicular plane and renormalizing - a standard, simple parallel-transport
	 * approximation that avoids the visible twist a fixed world-up reference produces once the tendril's own
	 * path turns through vertical. */
	private static Vec3[] computeRights(Vec3[] tangents) {
		Vec3[] rights = new Vec3[tangents.length];
		Vec3 worldUp = Math.abs(tangents[0].y) < 0.99 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
		rights[0] = tangents[0].cross(worldUp).normalize();
		for (int i = 1; i < tangents.length; i++) {
			Vec3 projected = rights[i - 1].subtract(tangents[i].scale(rights[i - 1].dot(tangents[i])));
			rights[i] = projected.lengthSqr() > 1.0E-8 ? projected.normalize() : tangents[i].cross(worldUp).normalize();
		}
		return rights;
	}

	private static void emitRingSegment(BufferBuilder builder, PoseStack.Pose pose,
			Vec3 p0, Vec3 t0, Vec3 r0, float w0, Vec3 p1, Vec3 t1, Vec3 r1, float w1) {
		Vec3 u0 = t0.cross(r0);
		Vec3 u1 = t1.cross(r1);
		for (int side = 0; side < SIDES; side++) {
			double a0 = side / (double) SIDES * (Math.PI * 2.0);
			double a1 = (side + 1) / (double) SIDES * (Math.PI * 2.0);

			Vec3 v00 = ringVertex(p0, r0, u0, w0, a0);
			Vec3 v01 = ringVertex(p0, r0, u0, w0, a1);
			Vec3 v11 = ringVertex(p1, r1, u1, w1, a1);
			Vec3 v10 = ringVertex(p1, r1, u1, w1, a0);

			addVertex(builder, pose, v00);
			addVertex(builder, pose, v01);
			addVertex(builder, pose, v11);
			addVertex(builder, pose, v10);
		}
	}

	private static Vec3 ringVertex(Vec3 center, Vec3 right, Vec3 up, float radius, double angle) {
		return center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
	}

	private static void addVertex(BufferBuilder builder, PoseStack.Pose pose, Vec3 v) {
		builder.addVertex(pose, (float) v.x, (float) v.y, (float) v.z);
	}
}

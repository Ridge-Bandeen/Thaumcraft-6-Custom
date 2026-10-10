package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * The original's {@code UtilsFX.renderTextureIn3D}: a texture region extruded into a 1x1 slab {@code thickness} deep
 * (front face at z 0, back at {@code -thickness}), with a strip per texel column and row for the edges, as vanilla 1.12
 * drew held items. Used for the Pattern Crafter's gears and the Magic Mirror's frame.
 */
final class TextureIn3D {

	private TextureIn3D() {
	}

	static void render(VertexConsumer vc, PoseStack.Pose pose, float maxu, float maxv, float minu, float minv, int width, int height,
			float thickness, int light, int overlay) {
		float t = -thickness;
		vertex(vc, pose, 0, 0, 0, maxu, minv, 0, 0, 1, light, overlay);
		vertex(vc, pose, 1, 0, 0, minu, minv, 0, 0, 1, light, overlay);
		vertex(vc, pose, 1, 1, 0, minu, maxv, 0, 0, 1, light, overlay);
		vertex(vc, pose, 0, 1, 0, maxu, maxv, 0, 0, 1, light, overlay);
		vertex(vc, pose, 0, 1, t, maxu, maxv, 0, 0, -1, light, overlay);
		vertex(vc, pose, 1, 1, t, minu, maxv, 0, 0, -1, light, overlay);
		vertex(vc, pose, 1, 0, t, minu, minv, 0, 0, -1, light, overlay);
		vertex(vc, pose, 0, 0, t, maxu, minv, 0, 0, -1, light, overlay);
		float f5 = 0.5f * (maxu - minu) / width;
		float f6 = 0.5f * (minv - maxv) / height;
		for (int k = 0; k < width; k++) {
			float f7 = k / (float) width;
			float f8 = maxu + (minu - maxu) * f7 - f5;
			vertex(vc, pose, f7, 0, t, f8, minv, -1, 0, 0, light, overlay);
			vertex(vc, pose, f7, 0, 0, f8, minv, -1, 0, 0, light, overlay);
			vertex(vc, pose, f7, 1, 0, f8, maxv, -1, 0, 0, light, overlay);
			vertex(vc, pose, f7, 1, t, f8, maxv, -1, 0, 0, light, overlay);
			float f9 = f7 + 1.0f / width;
			vertex(vc, pose, f9, 1, t, f8, maxv, 1, 0, 0, light, overlay);
			vertex(vc, pose, f9, 1, 0, f8, maxv, 1, 0, 0, light, overlay);
			vertex(vc, pose, f9, 0, 0, f8, minv, 1, 0, 0, light, overlay);
			vertex(vc, pose, f9, 0, t, f8, minv, 1, 0, 0, light, overlay);
		}
		for (int k = 0; k < height; k++) {
			float f7 = k / (float) height;
			float f8 = minv + (maxv - minv) * f7 - f6;
			float f9 = f7 + 1.0f / height;
			vertex(vc, pose, 0, f9, 0, maxu, f8, 0, 1, 0, light, overlay);
			vertex(vc, pose, 1, f9, 0, minu, f8, 0, 1, 0, light, overlay);
			vertex(vc, pose, 1, f9, t, minu, f8, 0, 1, 0, light, overlay);
			vertex(vc, pose, 0, f9, t, maxu, f8, 0, 1, 0, light, overlay);
			vertex(vc, pose, 1, f7, 0, minu, f8, 0, -1, 0, light, overlay);
			vertex(vc, pose, 0, f7, 0, maxu, f8, 0, -1, 0, light, overlay);
			vertex(vc, pose, 0, f7, t, maxu, f8, 0, -1, 0, light, overlay);
			vertex(vc, pose, 1, f7, t, minu, f8, 0, -1, 0, light, overlay);
		}
	}

	static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz,
			int light, int overlay) {
		vc.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
	}
}

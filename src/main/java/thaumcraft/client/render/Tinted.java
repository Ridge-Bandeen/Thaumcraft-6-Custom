package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * A vertex consumer that multiplies every vertex colour by a fixed tint - the modern stand-in for setting the GL colour
 * before a model drew, as the original did for tinted models. Open for subclassing (e.g. to shift texture coordinates).
 */
public class Tinted implements VertexConsumer {

	private final VertexConsumer inner;
	private final float r;
	private final float g;
	private final float b;
	private final float a;

	public Tinted(VertexConsumer inner, float r, float g, float b, float a) {
		this.inner = inner;
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		inner.addVertex(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer setColor(int red, int green, int blue, int alpha) {
		inner.setColor((int) (red * r), (int) (green * g), (int) (blue * b), (int) (alpha * a));
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		inner.setUv(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		inner.setUv1(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		inner.setUv2(u, v);
		return this;
	}

	@Override
	public VertexConsumer setNormal(float x, float y, float z) {
		inner.setNormal(x, y, z);
		return this;
	}
}

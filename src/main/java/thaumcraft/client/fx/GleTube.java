package thaumcraft.client.fx;

import net.minecraft.world.phys.Vec3;

/**
 * The tube the original drew with its bundled GLE library ({@code com.sasmaster.glelwjgl.CoreGLE#glePolyCone}), for
 * the effects that used it: the Wisp / bolt focus zap ({@code FXBolt}), essentia streams, the Void Siphon's void
 * streaks, the flux rift and the grapple rope.
 *
 * <p>As GLE: the tube runs from the second point to the second-last (the first and last only aim the end joins); at each
 * joint the {@code sides}-sided contour, scaled by that point's radius, is cut by the plane bisecting the bend (GLE's
 * angle join); the contour is oriented by GLE's up vector (+Y, or (1,1,1) when the path starts straight up or down);
 * colours are per point; the texture wraps once round the tube (u) and advances {@code texSlice} along each segment (v)
 * from {@code texStart}. {@code caps} adds flat end caps (join style 0x10); {@code bothWindings} emits each face twice
 * for effects the original drew with culling off - otherwise the outward face only.
 */
public final class GleTube {

	/** Where the vertices go: position, texture coordinates and colour. */
	@FunctionalInterface
	public interface Sink {
		void vertex(double x, double y, double z, float u, float v, float r, float g, float b, float a);
	}

	private GleTube() {
	}

	public static void polyCone(Sink sink, Vec3[] points, float[][] colours, double[] radii, int sides, float texSlice, float texStart,
			boolean caps, boolean bothWindings) {
		int n = points.length;
		if (n < 3) return;
		Vec3 up = null;
		for (int i = 0; i < n - 1; i++) {
			Vec3 d = points[i + 1].subtract(points[i]);
			if (d.lengthSqr() > 1.0e-12) {
				up = d.x == 0.0 && d.z == 0.0 ? new Vec3(1, 1, 1) : new Vec3(0, 1, 0);
				break;
			}
		}
		if (up == null) return;
		Vec3[][] rings = new Vec3[n][];
		for (int i = 1; i < n - 1; i++) {
			Vec3 prev = points[i].subtract(points[i - 1]);
			Vec3 next = points[i + 1].subtract(points[i]);
			if (prev.lengthSqr() < 1.0e-12) prev = next;
			if (next.lengthSqr() < 1.0e-12) next = prev;
			if (prev.lengthSqr() < 1.0e-12) continue;
			prev = prev.normalize();
			next = next.normalize();
			Vec3 bis = prev.add(next);
			bis = bis.lengthSqr() < 1.0e-12 ? prev : bis.normalize();
			Vec3 side = prev.cross(up);
			if (side.lengthSqr() < 1.0e-12) side = prev.cross(new Vec3(1, 0, 0));
			if (side.lengthSqr() < 1.0e-12) side = prev.cross(new Vec3(0, 0, 1));
			side = side.normalize();
			Vec3 norm = side.cross(prev).normalize();
			double denom = prev.dot(bis);
			Vec3[] ring = new Vec3[sides];
			for (int j = 0; j < sides; j++) {
				double ang = Math.PI * 2.0 * j / sides;
				Vec3 c = side.scale(Math.cos(ang) * radii[i]).add(norm.scale(Math.sin(ang) * radii[i]));
				// slide along the incoming segment onto the bisecting plane (GLE's angle join)
				double t = Math.abs(denom) > 0.2 ? -c.dot(bis) / denom : 0.0;
				ring[j] = points[i].add(c).add(prev.scale(t));
			}
			rings[i] = ring;
		}
		float slice = texStart;
		int first = -1;
		int last = -1;
		for (int i = 1; i < n - 2; i++) {
			Vec3[] a = rings[i];
			Vec3[] b = rings[i + 1];
			if (a == null || b == null) continue;
			if (first < 0) first = i;
			last = i + 1;
			float[] ca = colours[i];
			float[] cb = colours[i + 1];
			Vec3 axis = points[i + 1].subtract(points[i]);
			for (int j = 0; j < sides; j++) {
				int k = (j + 1) % sides;
				float u0 = j / (float) sides;
				float u1 = (j + 1) / (float) sides;
				quad(sink, a[j], b[j], b[k], a[k], points[i], axis, u0, u1, slice, slice + texSlice, ca, cb, bothWindings);
			}
			slice += texSlice;
		}
		if (caps && first >= 0) {
			cap(sink, rings[first], points[first], colours[first], 1.0f, texStart, bothWindings, points[first].subtract(points[first + 1]));
			cap(sink, rings[last], points[last], colours[last], 1.0f, slice, bothWindings, points[last].subtract(points[last - 1]));
		}
	}

	private static void quad(Sink sink, Vec3 a0, Vec3 b0, Vec3 b1, Vec3 a1, Vec3 centre, Vec3 axis, float u0, float u1, float v0, float v1,
			float[] ca, float[] cb, boolean both) {
		Vec3 normal = b0.subtract(a0).cross(a1.subtract(a0));
		Vec3 out = a0.subtract(centre);
		out = out.subtract(axis.scale(axis.lengthSqr() > 0 ? out.dot(axis) / axis.lengthSqr() : 0));
		boolean outward = normal.dot(out) >= 0;
		if (outward || both) emit(sink, a0, b0, b1, a1, u0, u1, v0, v1, ca, cb);
		if (!outward || both) emit(sink, a1, b1, b0, a0, u1, u0, v0, v1, ca, cb);
	}

	private static void emit(Sink sink, Vec3 a0, Vec3 b0, Vec3 b1, Vec3 a1, float u0, float u1, float v0, float v1, float[] ca, float[] cb) {
		sink.vertex(a0.x, a0.y, a0.z, u0, v0, ca[0], ca[1], ca[2], ca[3]);
		sink.vertex(b0.x, b0.y, b0.z, u0, v1, cb[0], cb[1], cb[2], cb[3]);
		sink.vertex(b1.x, b1.y, b1.z, u1, v1, cb[0], cb[1], cb[2], cb[3]);
		sink.vertex(a1.x, a1.y, a1.z, u1, v0, ca[0], ca[1], ca[2], ca[3]);
	}

	/** A flat end cap as a fan of quads (each a triangle with its last corner repeated). */
	private static void cap(Sink sink, Vec3[] ring, Vec3 centre, float[] c, float u, float v, boolean both, Vec3 facing) {
		for (int j = 0; j < ring.length; j++) {
			Vec3 p0 = ring[j];
			Vec3 p1 = ring[(j + 1) % ring.length];
			Vec3 normal = p0.subtract(centre).cross(p1.subtract(centre));
			boolean outward = normal.dot(facing) >= 0;
			if (outward || both) {
				sink.vertex(centre.x, centre.y, centre.z, u, v, c[0], c[1], c[2], c[3]);
				sink.vertex(p0.x, p0.y, p0.z, u, v, c[0], c[1], c[2], c[3]);
				sink.vertex(p1.x, p1.y, p1.z, u, v, c[0], c[1], c[2], c[3]);
				sink.vertex(p1.x, p1.y, p1.z, u, v, c[0], c[1], c[2], c[3]);
			}
			if (!outward || both) {
				sink.vertex(centre.x, centre.y, centre.z, u, v, c[0], c[1], c[2], c[3]);
				sink.vertex(p1.x, p1.y, p1.z, u, v, c[0], c[1], c[2], c[3]);
				sink.vertex(p0.x, p0.y, p0.z, u, v, c[0], c[1], c[2], c[3]);
				sink.vertex(p0.x, p0.y, p0.z, u, v, c[0], c[1], c[2], c[3]);
			}
		}
	}
}

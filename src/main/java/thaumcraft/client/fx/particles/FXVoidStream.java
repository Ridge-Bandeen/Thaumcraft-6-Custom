package thaumcraft.client.fx.particles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Replaces the old (1.12.2) {@code FXVoidStream}: the Void Siphon's streaks of void drawn out of a flux rift. As the
 * original the head wanders (pull towards the siphon plus random jitter) and leaves a tube behind it - three sides,
 * radii pulsing and tapered by the same table as the essentia stream, shrinking from the tail once it arrives. It is
 * drawn through the ender shader in two passes, an additive halo at 1.5 times the radius and the core at half
 * ({@link thaumcraft.client.render.VoidStreamRenderer}); the particle engine only moves it.
 */
public class FXVoidStream extends Particle {

	/** The streams alive now, for {@link thaumcraft.client.render.VoidStreamRenderer}. */
	public static final Set<FXVoidStream> LIVE = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	private final double targetX;
	private final double targetY;
	private final double targetZ;
	public final double startX;
	public final double startY;
	public final double startZ;
	private final int seed;
	private float particleScale;
	private int length = 40;
	public int growing = -1;
	private final List<double[]> vecs = new ArrayList<>();
	public Vec3[] points;
	public float[][] colours;
	public double[] radii;

	public FXVoidStream(ClientLevel level, double x, double y, double z, double tx, double ty, double tz, int seed, float scale) {
		super(level, x, y, z);
		this.particleScale = (float) (scale * (1.0 + random.nextGaussian() * 0.15));
		this.seed = seed;
		this.targetX = tx;
		this.targetY = ty;
		this.targetZ = tz;
		double dx = tx - x;
		double dy = ty - y;
		double dz = tz - z;
		int base = Math.max(1, (int) (Mth.sqrt((float) (dx * dx + dy * dy + dz * dz)) * 21.0f));
		this.lifetime = base * 2;
		this.xd = Mth.sin(seed / 4.0f) * 0.025f;
		this.yd = Mth.sin(seed / 3.0f) * 0.025f;
		this.zd = Mth.sin(seed / 2.0f) * 0.025f;
		this.gravity = 0.2f;
		vecs.add(new double[] { 0.001, 0, 0, 0 });
		vecs.add(new double[] { 0.001, 0, 0, 0 });
		this.startX = x;
		this.startY = y;
		this.startZ = z;
		LIVE.add(this);
	}

	public int age() {
		return age;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime || length < 1) {
			remove();
			LIVE.remove(this);
			return;
		}
		yd += 0.01 * gravity;
		move(xd, yd, zd);
		xd *= 0.985;
		yd *= 0.985;
		zd *= 0.985;
		xd = Mth.clamp((float) xd, -0.04f, 0.04f);
		yd = Mth.clamp((float) yd, -0.04f, 0.04f);
		zd = Mth.clamp((float) zd, -0.04f, 0.04f);
		double dx = targetX - x;
		double dy = targetY - y;
		double dz = targetZ - z;
		double d14 = Mth.sqrt((float) (dx * dx + dy * dy + dz * dz));
		dx /= d14;
		dy /= d14;
		dz /= d14;
		xd += dx * (0.01 / Math.min(1.0, d14)) + random.nextGaussian() * 0.015;
		yd += dy * (0.01 / Math.min(1.0, d14)) + random.nextGaussian() * 0.015;
		zd += dz * (0.01 / Math.min(1.0, d14)) + random.nextGaussian() * 0.015;
		float scale = particleScale * (0.75f + Mth.sin((seed + age) / 2.0f) * 0.25f);
		if (d14 < 0.5) {
			float f = Mth.sin((float) (d14 * (Math.PI / 2.0)));
			scale *= f;
			particleScale *= f;
		}
		if (particleScale > 0.001) {
			vecs.add(new double[] { scale, x - startX, y - startY, z - startZ });
		} else {
			if (growing < 0) growing = age;
			--length;
		}
		if (vecs.size() > length) vecs.remove(0);
		int n = vecs.size();
		points = new Vec3[n];
		colours = new float[n][];
		radii = new double[n];
		int c = n;
		for (double[] v : vecs) {
			--c;
			float variance = 1.0f + Mth.sin((c + age) / 3.0f) * 0.2f;
			float xx = Mth.sin((c + age) / 6.0f) * 0.01f;
			float yy = Mth.sin((c + age) / 7.0f) * 0.01f;
			float zz = Mth.sin((c + age) / 8.0f) * 0.01f;
			points[c] = new Vec3(v[1] + xx, v[2] + yy, v[3] + zz);
			radii[c] = v[0] * variance;
			if (c > n - 10) radii[c] *= Mth.cos((float) ((c - (n - 12)) / 10.0f * (Math.PI / 2.0)));
			if (c == 0 || c == 1) {
				radii[c] = 0.0;
			} else if (c == 2) {
				radii[c] = (particleScale * 0.5 + radii[c]) / 2.0;
			} else if (c == 3) {
				radii[c] = (particleScale + radii[c]) / 2.0;
			} else if (c == 4) {
				radii[c] = (particleScale + radii[c] * 2.0) / 3.0;
			}
			colours[c] = new float[] { 1, 1, 1, 1 };
		}
	}

	@Override
	public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, Camera camera, float partialTicks) {
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.NO_RENDER;
	}
}

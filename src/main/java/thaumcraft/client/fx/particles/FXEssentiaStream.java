package thaumcraft.client.fx.particles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import thaumcraft.Thaumcraft;
import thaumcraft.client.fx.GleTube;

/**
 * Replaces the old (1.12.2) {@code FXEssentiaStream}, as the original: a head that homes on the target (drag, pull and
 * a little gravity) leaves its positions behind as an eight-sided tube ({@link GleTube}) on the essentia texture.
 * Every tick the tube's points wobble, its radii pulse (the first two points closed, the next three easing in, the
 * last ten narrowing to the head) and its colour shimmers; once the head arrives the tube shrinks from its tail
 * while the texture runs along it, with essentia drops at the target and along the stream. Drawn with normal
 * blending (the original's particle layer 1). A stream from an essentia input or output starts just off its face.
 * Repeated streams between the same two blocks in the same colour lengthen the live one instead of starting anew.
 */
public class FXEssentiaStream extends Particle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/essentia.png");

	private static final Map<String, FXEssentiaStream> ACTIVE = new HashMap<>();

	public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
		@Nullable
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.setShader(GameRenderer::getParticleShader);
			RenderSystem.setShaderTexture(0, TEXTURE);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.depthMask(false);
			return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}

		@Override
		public String toString() {
			return "thaumcraft:essentia_stream";
		}
	};

	private final double targetX;
	private final double targetY;
	private final double targetZ;
	private final double startX;
	private final double startY;
	private final double startZ;
	private final int count;
	private float particleScale;
	private int length;
	private String key = "";
	private boolean expired;
	private int growing = -1;

	/** The original's Quat list: scale and position relative to the start. */
	private final List<double[]> vecs = new ArrayList<>();
	private Vec3[] points;
	private float[][] colours;
	private double[] radii;

	public FXEssentiaStream(ClientLevel level, double x, double y, double z, double tx, double ty, double tz,
			int count, int color, float scale, int extend, double my) {
		super(level, x, y, z);
		this.count = count;
		this.particleScale = (float) (scale * (1.0 + random.nextGaussian() * 0.15));
		this.length = Math.max(20, extend);
		this.targetX = tx;
		this.targetY = ty;
		this.targetZ = tz;
		BlockPos bp1 = BlockPos.containing(x, y, z);
		BlockPos bp2 = BlockPos.containing(tx, ty, tz);
		BlockState bs = level.getBlockState(bp1);
		if ((bs.getBlock() instanceof thaumcraft.common.blocks.essentia.BlockEssentiaInput
				|| bs.getBlock() instanceof thaumcraft.common.blocks.essentia.BlockEssentiaOutput) && bs.hasProperty(BlockStateProperties.FACING)) {
			Direction f = bs.getValue(BlockStateProperties.FACING);
			setPos(this.x + f.getStepX() * 0.05f, this.y + f.getStepY() * 0.05f, this.z + f.getStepZ() * 0.05f);
		}
		double dx = tx - this.x;
		double dy = ty - this.y;
		double dz = tz - this.z;
		int base = (int) (Mth.sqrt((float) (dx * dx + dy * dy + dz * dz)) * 21.0f);
		this.lifetime = Math.max(1, base);
		String k = bp1.asLong() + "" + bp2.asLong() + "" + color;
		FXEssentiaStream existing = ACTIVE.get(k);
		if (existing != null && !existing.expired && existing.vecs.size() < existing.length) {
			existing.length += Math.max(extend, 5);
			existing.lifetime += Math.max(extend, 5);
			this.lifetime = 0;
		}
		if (this.lifetime > 0) {
			ACTIVE.put(k, this);
			this.key = k;
		}
		this.xd = Mth.sin(count / 4.0f) * 0.015f;
		this.yd = my + Mth.sin(count / 3.0f) * 0.015f;
		this.zd = Mth.sin(count / 2.0f) * 0.015f;
		this.rCol = ((color >> 16) & 0xFF) / 255.0f;
		this.gCol = ((color >> 8) & 0xFF) / 255.0f;
		this.bCol = (color & 0xFF) / 255.0f;
		this.gravity = 0.2f;
		vecs.add(new double[] { 0.001, 0, 0, 0 });
		vecs.add(new double[] { 0.001, 0, 0, 0 });
		this.startX = this.x;
		this.startY = this.y;
		this.startZ = this.z;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime || length < 1) {
			expired = true;
			remove();
			if (ACTIVE.get(key) == this) ACTIVE.remove(key);
			return;
		}
		yd += 0.01 * gravity;
		move(xd, yd, zd);
		xd *= 0.985;
		yd *= 0.985;
		zd *= 0.985;
		xd = Mth.clamp((float) xd, -0.05f, 0.05f);
		yd = Mth.clamp((float) yd, -0.05f, 0.05f);
		zd = Mth.clamp((float) zd, -0.05f, 0.05f);
		double dx = targetX - x;
		double dy = targetY - y;
		double dz = targetZ - z;
		double d14 = Mth.sqrt((float) (dx * dx + dy * dy + dz * dz));
		dx /= d14;
		dy /= d14;
		dz /= d14;
		xd += dx * (0.01 / Math.min(1.0, d14));
		yd += dy * (0.01 / Math.min(1.0, d14));
		zd += dz * (0.01 / Math.min(1.0, d14));
		float scale = particleScale * (0.75f + Mth.sin((count + age) / 2.0f) * 0.25f);
		if (d14 < 1.0) {
			float f = Mth.sin((float) (d14 * (Math.PI / 2.0)));
			scale *= f;
			particleScale *= f;
		}
		if (particleScale > 0.001) {
			vecs.add(new double[] { scale, x - startX, y - startY, z - startZ });
		} else {
			if (growing < 0) growing = age;
			--length;
			thaumcraft.client.fx.ClientFxHandlers.spawnEssentiaDrop(level, targetX + random.nextGaussian() * 0.075, targetY + random.nextGaussian() * 0.075,
					targetZ + random.nextGaussian() * 0.075, rCol, gCol, bCol, 0.5f);
		}
		if (vecs.size() > length) vecs.remove(0);
		int n = vecs.size();
		points = new Vec3[n];
		colours = new float[n][4];
		radii = new double[n];
		int c = n;
		for (double[] v : vecs) {
			--c;
			float variance = 1.0f + Mth.sin((c + age) / 3.0f) * 0.2f;
			float xx = Mth.sin((c + age) / 6.0f) * 0.03f;
			float yy = Mth.sin((c + age) / 7.0f) * 0.03f;
			float zz = Mth.sin((c + age) / 8.0f) * 0.03f;
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
			float v2 = 1.0f - Mth.sin((c + age) / 2.0f) * 0.1f;
			colours[c][0] = rCol * v2;
			colours[c][1] = gCol * v2;
			colours[c][2] = bCol * v2;
			colours[c][3] = 1.0f;
		}
		if (n > 2 && random.nextBoolean()) {
			int q = random.nextInt(3);
			if (random.nextBoolean()) q = n - 2;
			double[] p = vecs.get(q);
			thaumcraft.client.fx.ClientFxHandlers.spawnEssentiaDrop(level, p[1] + startX, p[2] + startY, p[3] + startZ, rCol, gCol, bCol, 0.5f);
		}
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		if (points == null || points.length <= 2) return;
		Vec3 cam = camera.getPosition();
		double ox = startX - cam.x;
		double oy = startY - cam.y;
		double oz = startZ - cam.z;
		int light = LightTexture.FULL_BRIGHT;
		GleTube.Sink sink = (x, y, z, u, v, r, g, b, a) -> buffer.addVertex((float) (x + ox), (float) (y + oy), (float) (z + oz))
				.setUv(u, v).setColor(r, g, b, a).setLight(light);
		// set_POLYCYL_TESS(8), join style 1042, texture slice 0.075, running once the stream has arrived
		GleTube.polyCone(sink, points, colours, radii, 8, 0.075f, growing < 0 ? 0.0f : 0.075f * (age - growing + partialTicks), true, false);
	}

	@Override
	public net.minecraft.world.phys.AABB getRenderBoundingBox(float partialTicks) {
		return new net.minecraft.world.phys.AABB(startX, startY, startZ, x, y, z).inflate(1.0);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

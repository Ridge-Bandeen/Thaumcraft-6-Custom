package thaumcraft.client.fx.beams;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import thaumcraft.Thaumcraft;

/**
 * Real modern equivalent of the true original's own {@code FXArc} - drives {@link
 * thaumcraft.common.entities.monster.boss.EntityEldritchGolem}'s headless-phase arc-lightning "search for a
 * ground target" decoration and {@link thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater}'s
 * stage-setup banner/loot-crate "pulse" signal, both real, restored 2026-09-15.
 *
 * The true original computed a real jittered projectile-arc trajectory ({@code Utils.calculateVelocity}'s
 * own real parabola-fitting formula, faithfully reproduced below as {@link #calculateVelocity}) and drew it
 * as a double-plane cross-billboard ribbon via raw immediate-mode GL calls, one {@code GL_TRIANGLE_STRIP}
 * pass per plane. Modern rendering has no immediate mode and no {@code GL_TRIANGLE_STRIP} option through
 * {@link ParticleRenderType} (which only ever begins {@link VertexFormat.Mode#QUADS}), so this reformulates
 * the same two planes as a real per-segment quad ribbon instead - mathematically the same silhouette, just a
 * different primitive assembly. Additively blended and bound directly to the true original's own real
 * {@code beamh.png} texture (not the particle atlas - matches the original's own real "whole texture, not a
 * stitched sprite" choice), via a dedicated {@link #RENDER_TYPE} rather than one of vanilla's built-in ones,
 * since none of those bind an arbitrary non-atlas texture.
 *
 * Real, documented simplification: the true original also spawned a trailing {@code FXGeneric} ember at
 * every jittered waypoint - dropped for this pass. {@code FXGeneric} is a real, much larger "generic
 * configurable particle" system reused by dozens of other true-original effects, not exclusive to this one -
 * queued as its own future chunk rather than built piecemeal here. The ribbon itself, this effect's own real
 * primary silhouette, is faithfully kept; only the extra ember dressing around it is missing this pass.
 */
public class FXArc extends Particle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/beamh.png");
	private static final float HALF_WIDTH = 0.125f;

	public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
		@Nullable
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.setShader(GameRenderer::getParticleShader);
			RenderSystem.setShaderTexture(0, TEXTURE);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			RenderSystem.depthMask(false);
			return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}

		@Override
		public String toString() {
			return "thaumcraft:arc";
		}
	};

	private final List<Vec3> points = new ArrayList<>();
	private final float length;

	public FXArc(ClientLevel level, double x, double y, double z, double tx, double ty, double tz, float red, float green, float blue, float heightGain) {
		super(level, x, y, z);
		this.rCol = red;
		this.gCol = green;
		this.bCol = blue;
		this.alpha = 1.0f;
		setSize(0.02f, 0.02f);
		setLifetime(3);
		hasPhysics = false;

		double dx = tx - x;
		double dy = ty - y;
		double dz = tz - z;
		Vec3 end = new Vec3(dx, dy, dz);
		this.length = (float) end.length();

		double gravity = 0.115;
		double noise = 0.25;
		Vec3 velocity = calculateVelocity(Vec3.ZERO, end, heightGain <= 0.0f ? 0.1f : heightGain, gravity);
		double stepLenSq = velocity.lengthSqr();

		points.add(new Vec3(x, y, z));
		Vec3 current = Vec3.ZERO;
		for (int c = 0; current.distanceToSqr(end) > stepLenSq && c < 50; ++c) {
			current = current.add(velocity.x, velocity.y, velocity.z);
			current = current.add((random.nextDouble() - random.nextDouble()) * noise,
					(random.nextDouble() - random.nextDouble()) * noise,
					(random.nextDouble() - random.nextDouble()) * noise);
			points.add(new Vec3(x + current.x, y + current.y, z + current.z));
			velocity = velocity.subtract(0.0, gravity / 1.9, 0.0);
		}
		points.add(new Vec3(tx, ty, tz));
	}

	/** Faithful reproduction of the true original's own {@code Utils#calculateVelocity} - fits a real
	 * parabolic arc between two points given a desired peak height gain and gravity, returning the real
	 * per-step velocity vector. */
	private static Vec3 calculateVelocity(Vec3 from, Vec3 to, double heightGain, double gravity) {
		double endGain = to.y - from.y;
		double horizDist = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
		double maxGain = Math.max(heightGain, endGain + heightGain);
		double a = -horizDist * horizDist / (4.0 * maxGain);
		double b = horizDist;
		double c = -endGain;
		double slope = -b / (2.0 * a) - Math.sqrt(b * b - 4.0 * a * c) / (2.0 * a);
		double vy = Math.sqrt(maxGain * gravity);
		double vh = vy / slope;
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double mag = Math.sqrt(dx * dx + dz * dz);
		return new Vec3(vh * dx / mag, vy, vh * dz / mag);
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime) {
			remove();
		}
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		Vec3 camPos = camera.getPosition();
		float renderAlpha = net.minecraft.util.Mth.clamp(1.0f - (age + partialTicks) / lifetime, 0.0f, 1.0f);
		int light = LightTexture.FULL_BRIGHT;

		for (int c = 0; c < points.size() - 1; c++) {
			Vec3 a = points.get(c).subtract(camPos);
			Vec3 b = points.get(c + 1).subtract(camPos);
			float u0 = c / length;
			float u1 = (c + 1) / length;
			emitQuad(buffer, a, b, 0.0, HALF_WIDTH, 0.0, u0, u1, renderAlpha, light);
			emitQuad(buffer, a, b, HALF_WIDTH, 0.0, HALF_WIDTH, u0, u1, renderAlpha, light);
		}
	}

	private void emitQuad(VertexConsumer buffer, Vec3 a, Vec3 b, double ox, double oy, double oz, float u0, float u1, float renderAlpha, int light) {
		buffer.addVertex((float) (a.x - ox), (float) (a.y - oy), (float) (a.z - oz)).setUv(u0, 1.0f).setColor(rCol, gCol, bCol, renderAlpha).setLight(light);
		buffer.addVertex((float) (a.x + ox), (float) (a.y + oy), (float) (a.z + oz)).setUv(u0, 0.0f).setColor(rCol, gCol, bCol, renderAlpha).setLight(light);
		buffer.addVertex((float) (b.x + ox), (float) (b.y + oy), (float) (b.z + oz)).setUv(u1, 0.0f).setColor(rCol, gCol, bCol, renderAlpha).setLight(light);
		buffer.addVertex((float) (b.x - ox), (float) (b.y - oy), (float) (b.z - oz)).setUv(u1, 1.0f).setColor(rCol, gCol, bCol, renderAlpha).setLight(light);
	}

	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (Vec3 p : points) {
			minX = Math.min(minX, p.x);
			minY = Math.min(minY, p.y);
			minZ = Math.min(minZ, p.z);
			maxX = Math.max(maxX, p.x);
			maxY = Math.max(maxY, p.y);
			maxZ = Math.max(maxZ, p.z);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.5);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

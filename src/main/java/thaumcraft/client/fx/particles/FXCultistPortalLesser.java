package thaumcraft.client.fx.particles;

import javax.annotation.Nullable;

import org.joml.Quaternionf;
import org.joml.Vector3f;

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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser;

/**
 * Real modern equivalent of the true original's own {@code RenderCultistPortalLesser} - {@link
 * EntityCultistPortalLesser} had genuinely no visible model at all before this (same real gap class as
 * {@code WISP}/{@code GOLEM_ORB}/{@code ELDRITCH_ORB} - see the Custom Mob Models/Renderers TODO entry).
 *
 * <p>A single real camera-facing billboard quad off a dedicated {@code cultist_portal.png} texture (a real
 * 16-frame horizontal strip, animated off real wall-clock time - {@code System.nanoTime()/50_000_000L}, same
 * real non-tick-based cadence the true original itself used, not a simplification), with real NON-uniform
 * scale - the true original's own real {@code scale}/{@code scaley} multipliers apply independently to the
 * world X/Z vs. world Y axes (an oval, not a circle), which {@link
 * net.minecraft.client.particle.SingleQuadParticle}'s own single {@code quadSize} can't express - so this
 * extends {@link Particle} directly and reuses that class's own real quaternion camera-facing technique by
 * hand (rotate a unit corner offset by the camera's own rotation, matching its real proven winding/UV
 * convention exactly) rather than its literal uniform-scale code path.
 *
 * <p>Real animation, matching the true original's own exact formulas: the quad only renders at all once
 * {@link EntityCultistPortalLesser#isActive()}; scale/alpha "breathe" off a real decaying {@code hurtTime}
 * wobble and a real decaying {@link EntityCultistPortalLesser#pulse} flash (set via a real vanilla entity-event
 * broadcast the instant a minion spawns), plus a real continuous sine bob driven by the portal's own current
 * health ratio (the lower its health, the more pronounced the bob) off {@link
 * EntityCultistPortalLesser#activeCounter} - a real, plain (not synced) field incremented independently on
 * both sides once active, restored alongside this visual.
 */
public class FXCultistPortalLesser extends Particle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/cultist_portal.png");
	private static final int FRAMES = 16;

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
			return "thaumcraft:cultist_portal_lesser";
		}
	};

	private final EntityCultistPortalLesser target;

	public FXCultistPortalLesser(ClientLevel level, EntityCultistPortalLesser target) {
		super(level, target.getX(), target.getY(), target.getZ());
		this.target = target;
		this.hasPhysics = false;
	}

	/** @return whether the target is still real and alive - callers use this to know when to stop tracking. */
	public boolean isTargetAlive() {
		return target != null && target.isAlive();
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (!isTargetAlive()) {
			remove();
			return;
		}
		x = target.getX();
		y = target.getY();
		z = target.getZ();
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		if (!target.isActive()) {
			return;
		}

		float scaleY = 1.4f;
		float e = Math.min(50.0f, target.activeCounter + partialTicks);
		if (target.hurtTime > 0) {
			double d = Math.sin(target.hurtTime * 72.0 * Math.PI / 180.0);
			scaleY -= (float) (d / 4.0);
			e += (int) (6.0 * d);
		}
		if (target.pulse > 0) {
			double d = Math.sin(target.pulse * 36.0 * Math.PI / 180.0);
			scaleY += (float) (d / 4.0);
			e += (int) (12.0 * d);
		}
		float scale = e / 50.0f * 1.25f;

		float maxHealth = target.getMaxHealth();
		float m = maxHealth > 0.0f ? (1.0f - target.getHealth() / maxHealth) / 3.0f : 0.0f;
		float bob = Mth.sin(target.activeCounter / (5.0f - 12.0f * m)) * m + m;
		float bob2 = Mth.sin(target.activeCounter / (6.0f - 15.0f * m)) * m + m;
		float alpha = 1.0f - bob;
		scaleY -= bob / 4.0f;
		scale -= bob2 / 3.0f;

		float heightOffset = target.getBbHeight() / 2.0f;
		float px = (float) Mth.lerp(partialTicks, xo, x);
		float py = (float) Mth.lerp(partialTicks, yo, y);
		float pz = (float) Mth.lerp(partialTicks, zo, z);
		renderQuad(buffer, camera, px, py, pz, heightOffset, scale, scaleY, alpha);
	}

	/** Package-private (not {@code private}) so {@link FXCultistPortalGreater} can reuse the same real
	 * billboard-quad drawing code - only the surrounding animation formula differs between the two real
	 * portal variants, not the actual quad geometry/texture/UV logic. Takes the caller's own already
	 * partial-tick-interpolated world position directly (not a particle reference) so both real variants can
	 * share it despite tracking 2 different real entity types. */
	static void renderQuad(VertexConsumer buffer, Camera camera, float worldX, float worldY, float worldZ, float heightOffset, float scale, float scaleY, float alpha) {
		Vec3 camPos = camera.getPosition();
		float cx = worldX - (float) camPos.x;
		float cy = worldY - (float) camPos.y;
		float cz = worldZ - (float) camPos.z;

		Quaternionf quaternion = new Quaternionf(camera.rotation());
		long time = System.nanoTime() / 50_000_000L;
		int frame = 15 - (int) (time % FRAMES);
		float u0 = frame / (float) FRAMES;
		float u1 = u0 + 1.0f / FRAMES;
		int light = LightTexture.FULL_BRIGHT;

		emitVertex(buffer, quaternion, cx, cy, cz, heightOffset, 1.0f, -1.0f, scale, scaleY, u1, 1.0f, alpha, light);
		emitVertex(buffer, quaternion, cx, cy, cz, heightOffset, 1.0f, 1.0f, scale, scaleY, u1, 0.0f, alpha, light);
		emitVertex(buffer, quaternion, cx, cy, cz, heightOffset, -1.0f, 1.0f, scale, scaleY, u0, 0.0f, alpha, light);
		emitVertex(buffer, quaternion, cx, cy, cz, heightOffset, -1.0f, -1.0f, scale, scaleY, u0, 1.0f, alpha, light);
	}

	private static void emitVertex(VertexConsumer buffer, Quaternionf quaternion, float baseX, float baseY, float baseZ,
			float heightOffset, float xOffset, float yOffset, float scale, float scaleY, float u, float v, float alpha, int light) {
		Vector3f corner = new Vector3f(xOffset, yOffset, 0.0f).rotate(quaternion);
		corner.x *= scale;
		corner.y *= scaleY;
		corner.z *= scale;
		buffer.addVertex(baseX + corner.x(), baseY + heightOffset + corner.y(), baseZ + corner.z())
				.setUv(u, v).setColor(1.0f, 1.0f, 1.0f, alpha).setLight(light);
	}

	/** Uses the particle's OWN stored camera-relative-free {@code x/y/z} intentionally, not the camera-relative
	 * offsets {@link #render} computes internally for the quad itself - matches every other tracking particle
	 * this session's own {@code getRenderBoundingBox} pattern. */
	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		return new AABB(x - 2.0, y - 1.0, z - 2.0, x + 2.0, y + 3.0, z + 2.0);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

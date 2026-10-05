package thaumcraft.client.fx.particles;

import java.util.Random;

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
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Real modern equivalent of the true original's own {@code RenderEldritchOrb}'s untextured "spike ball" pass -
 * {@link thaumcraft.common.entities.projectile.EntityEldritchOrb} (the Eldritch Guardian/Warden's own ranged
 * attack) had genuinely no visible model at all before this (same real gap class as {@code WISP}/{@code
 * FOCUS_MINE}/{@code GOLEM_ORB} - see the Custom Mob Models/Renderers TODO entry). The true original's own
 * real second pass (a billboarded glow quad) is {@link FXEldritchOrbGlow}, a separate real particle - one
 * {@link Particle#render} call is bound to exactly one texture/vertex-format via {@link #getRenderType()}, so
 * this untextured vertex-coloured geometry and that textured quad can't share a single particle instance
 * (same real "one visual effect, several real tracking particles" shape {@code FXWispGlow}'s own 3 particles
 * already established).
 *
 * <p>12 real triangular spikes (a 3-sided pyramid each: an apex at the origin, a triangular base "cap" at
 * some real distance out), each independently rotated - real, checked-not-assumed discovery: despite the true
 * original re-seeding its own {@code Random} to a fixed {@code 187L} at the top of every real frame and
 * re-drawing "random" values from it, the fixed seed means the exact same 96 values (8 per spike: 6 rotation
 * angles + 2 length/width draws) come out every single time - the spike arrangement is 100% deterministic,
 * not actually re-randomized per frame, so it's precomputed once here ({@link #ANGLES}/{@link #BASE_LENGTH}/
 * {@link #BASE_WIDTH}) rather than redrawn every tick. Real, easy-to-miss detail that WOULD have produced a
 * visibly wrong shape if skipped: the true original's own 12 rotation calls are never individually
 * pushed/popped - each spike's own 6 rotations are applied cumulatively ON TOP of every previous spike's own
 * rotations (one shared matrix stack for the whole loop), not 12 independent transforms - reproduced here via
 * one running {@link Quaternionf} updated across the loop, snapshotted after each spike's own 6th rotation.
 * The real, per-frame animated part is a shared spin term (matching the true original's own real {@code
 * ticksExisted/80 * 360} degrees) added into every spike's own final rotation.
 *
 * <p>The colours are what the original actually drew: its tip vertices passed 64.0 and 255.0 to the float colour call,
 * which multiplied by 255 and kept the low byte, so the tips came out light grey (192) with an alpha of 1/255 - each
 * spike a white glow at the centre fading to nothing at its tip. Culling is on, as the original (the fan's winding
 * faces outward).
 */
public class FXEldritchOrbSpikes extends Particle {

	private static final int SPIKE_COUNT = 12;
	private static final float[][] ANGLES = new float[SPIKE_COUNT][6];
	private static final float[] BASE_LENGTH = new float[SPIKE_COUNT];
	private static final float[] BASE_WIDTH = new float[SPIKE_COUNT];

	static {
		Random shapeRandom = new Random(187L);
		for (int i = 0; i < SPIKE_COUNT; i++) {
			for (int j = 0; j < 6; j++) {
				ANGLES[i][j] = (float) Math.toRadians(shapeRandom.nextFloat() * 360.0f);
			}
			BASE_LENGTH[i] = shapeRandom.nextFloat() * 20.0f + 5.0f;
			BASE_WIDTH[i] = shapeRandom.nextFloat() * 2.0f + 1.0f;
		}
	}

	public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
		@Nullable
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.setShader(GameRenderer::getPositionColorShader);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
			RenderSystem.enableCull();
			RenderSystem.depthMask(false);
			return tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
		}

		@Override
		public String toString() {
			return "thaumcraft:eldritch_orb_spikes";
		}
	};

	private final Entity target;

	public FXEldritchOrbSpikes(ClientLevel level, Entity target) {
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
		Vec3 camPos = camera.getPosition();
		float cx = (float) (Mth.lerp(partialTicks, xo, x) - camPos.x);
		float cy = (float) (Mth.lerp(partialTicks, yo, y) - camPos.y);
		float cz = (float) (Mth.lerp(partialTicks, zo, z) - camPos.z);

		int age = target.tickCount;
		float growth = Math.min(age, 10) / 10.0f;
		float spin = (age + partialTicks) / 80.0f * (float) (Math.PI * 2.0);

		Quaternionf cumulative = new Quaternionf();
		for (int i = 0; i < SPIKE_COUNT; i++) {
			cumulative.rotateX(ANGLES[i][0]).rotateY(ANGLES[i][1]).rotateZ(ANGLES[i][2])
					.rotateX(ANGLES[i][3]).rotateY(ANGLES[i][4]).rotateZ(ANGLES[i][5] + spin);

			float fa = BASE_LENGTH[i] * growth / 30.0f;
			float f4 = BASE_WIDTH[i] * growth / 30.0f;

			Vector3f apex = new Vector3f(0.0f, 0.0f, 0.0f);
			Vector3f v1 = new Vector3f(-0.866f * f4, fa, -0.5f * f4).rotate(cumulative);
			Vector3f v2 = new Vector3f(0.866f * f4, fa, -0.5f * f4).rotate(cumulative);
			Vector3f v3 = new Vector3f(0.0f, fa, 1.0f * f4).rotate(cumulative);

			emitTri(buffer, cx, cy, cz, apex, v1, v2);
			emitTri(buffer, cx, cy, cz, apex, v2, v3);
			emitTri(buffer, cx, cy, cz, apex, v3, v1);
		}
	}

	private static void emitTri(VertexConsumer buffer, float cx, float cy, float cz, Vector3f apex, Vector3f a, Vector3f b) {
		buffer.addVertex(cx + apex.x(), cy + apex.y(), cz + apex.z()).setColor(1.0f, 1.0f, 1.0f, 1.0f);
		buffer.addVertex(cx + a.x(), cy + a.y(), cz + a.z()).setColor(192, 192, 192, 1);
		buffer.addVertex(cx + b.x(), cy + b.y(), cz + b.z()).setColor(192, 192, 192, 1);
	}

	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		return new AABB(x - 1.0, y - 1.0, z - 1.0, x + 1.0, y + 1.0, z + 1.0);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

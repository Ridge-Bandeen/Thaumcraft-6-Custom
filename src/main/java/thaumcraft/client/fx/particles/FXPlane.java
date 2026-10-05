package thaumcraft.client.fx.particles;

import javax.annotation.Nullable;

import org.joml.Quaternionf;
import org.joml.Vector3f;

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
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code FXPlane}, the Arcing slash ({@code FXDispatcher#drawSlash}): a flat, additive slash
 * sprite that glides from one point to another over its life, laid along the line between them with a random roll,
 * running through nine frames of the particle sheet (row 10, from column 22) and growing from half to one and a half
 * blocks; it fades in over the first fifth of its life and out after, at half strength, full bright. Drawn from both
 * sides (the original turned culling off).
 */
public class FXPlane extends Particle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");

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
			return "thaumcraft:plane";
		}
	};

	private final float angle;
	private float angleYaw;
	private float anglePitch;

	public FXPlane(ClientLevel level, double x, double y, double z, double tx, double ty, double tz, int life) {
		super(level, x, y, z);
		this.rCol = 1.0f;
		this.gCol = 1.0f;
		this.bCol = 1.0f;
		this.gravity = 0.0f;
		this.lifetime = life;
		this.alpha = 0.0f;
		this.hasPhysics = false;
		setSize(0.01f, 0.01f);
		double dx = tx - x;
		double dy = ty - y;
		double dz = tz - z;
		this.xd = dx / lifetime;
		this.yd = dy / lifetime;
		this.zd = dz / lifetime;
		double d3 = Math.sqrt(dx * dx + dz * dz);
		if (d3 >= 1.0e-7) {
			angleYaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
			anglePitch = (float) (-(Mth.atan2(dy, d3) * 180.0 / Math.PI));
		}
		this.angle = (float) (random.nextGaussian() * 20.0);
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		float threshold = lifetime / 5.0f;
		alpha = age <= threshold ? age / threshold : (lifetime - age) / (float) lifetime;
		if (age++ >= lifetime) {
			remove();
		}
		x += xd;
		y += yd;
		z += zd;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		var cam = camera.getPosition();
		float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
		float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
		float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);
		Quaternionf q = new Quaternionf()
				.rotateY((float) Math.toRadians(-angleYaw + 90.0f))
				.rotateZ((float) Math.toRadians(anglePitch + 90.0f))
				.rotateY((float) Math.toRadians(angle));
		int frame = 22 + Math.round((age + partialTicks) / lifetime * 8.0f);
		float u0 = frame / 32.0f;
		float u1 = u0 + 0.03125f;
		float v0 = 10 / 32.0f;
		float v1 = v0 + 0.03125f;
		float s = 1.0f * (0.5f + (age + partialTicks) / lifetime);
		float a = alpha / 2.0f;
		float[][] corners = { { -0.5f, 0.5f }, { 0.5f, 0.5f }, { 0.5f, -0.5f }, { -0.5f, -0.5f } };
		float[][] uvs = { { u1, v1 }, { u1, v0 }, { u0, v0 }, { u0, v1 } };
		Vector3f[] p = new Vector3f[4];
		for (int i = 0; i < 4; i++) p[i] = new Vector3f(corners[i][0] * s, corners[i][1] * s, 0).rotate(q).add(px, py, pz);
		for (int i = 0; i < 4; i++) vertex(buffer, p[i], uvs[i], a);
		for (int i = 3; i >= 0; i--) vertex(buffer, p[i], uvs[i], a);
	}

	private void vertex(VertexConsumer buffer, Vector3f p, float[] uv, float a) {
		buffer.addVertex(p.x, p.y, p.z).setUv(uv[0], uv[1]).setColor(rCol, gCol, bCol, a).setLight(240);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

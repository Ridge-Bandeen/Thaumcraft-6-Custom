package thaumcraft.client.fx.particles;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.Thaumcraft;

/**
 * Real modern equivalent of the true original's own real {@code FXSmokeSpiral} - a single smoke puff that
 * orbits a real fixed origin point in an expanding-then-contracting double-helix arc, rising as it goes.
 * Restored 2026-09-17. Real call sites confirmed via `git grep`:
 * {@link thaumcraft.common.entities.monster.boss.EntityEldritchWarden}'s own real teleport cue and {@link
 * thaumcraft.common.items.tools.ItemElementalSword}'s own real special-attack cue.
 *
 * <p>As the original the particle itself never moves: each frame it is drawn on the spiral at (age + partial tick) -
 * two turns round, from the top of a sphere of the given radius to its bottom, never below {@code minY} + 0.1 - in
 * the frame for its age, fading out, at 1 to 2 times its size (1.12's random particle scale).
 */
public class FXSmokeSpiral extends SingleQuadParticle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final int GRID = 64;
	private static final float CELL = 1.0f / GRID;

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
			return "thaumcraft:smoke_spiral";
		}
	};

	private final double originX;
	private final double originY;
	private final double originZ;
	private final float radius;
	private final int start;
	private final float minY;

	public FXSmokeSpiral(ClientLevel level, double x, double y, double z, float radius, int start, int minY, int color) {
		super(level, x, y, z);
		this.originX = x;
		this.originY = y;
		this.originZ = z;
		this.radius = radius;
		this.start = start;
		this.minY = minY;
		this.rCol = ((color >> 16) & 0xFF) / 255.0f;
		this.gCol = ((color >> 8) & 0xFF) / 255.0f;
		this.bCol = (color & 0xFF) / 255.0f;
		this.lifetime = 20 + random.nextInt(10);
		this.quadSize = (random.nextFloat() * 0.5f + 0.5f) * 2.0f; // 1.12 Particle's own random scale
		this.alpha = 0.66f;
	}

	@Override
	public void tick() {
		alpha = 0.66f * (lifetime - age) / (float) lifetime;

		if (age++ >= lifetime) {
			remove();
		}
	}

	/** The original's renderParticle: the spiral position for this frame. */
	@Override
	public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTicks) {
		float t = (age + partialTicks) / lifetime;
		float r1 = start + 720.0f * t;
		float r2 = 90.0f - 180.0f * t;
		float mx = -Mth.sin(r1 / 180.0f * Mth.PI) * Mth.cos(r2 / 180.0f * Mth.PI) * radius;
		float mz = Mth.cos(r1 / 180.0f * Mth.PI) * Mth.cos(r2 / 180.0f * Mth.PI) * radius;
		float my = -Mth.sin(r2 / 180.0f * Mth.PI) * radius;
		var cam = camera.getPosition();
		renderRotatedQuad(buffer, camera.rotation(), (float) (originX + mx - cam.x), (float) (Math.max(originY + my, minY + 0.1f) - cam.y),
				(float) (originZ + mz - cam.z), partialTicks);
	}

	@Override
	public float getQuadSize(float partialTicks) {
		return 0.15f * quadSize;
	}

	@Override
	protected float getU0() {
		int frame = (int) (1.0f + age / (float) lifetime * 4.0f);
		return (frame % 16) * CELL;
	}

	@Override
	protected float getU1() {
		return getU0() + CELL;
	}

	@Override
	protected float getV0() {
		int frame = (int) (1.0f + age / (float) lifetime * 4.0f);
		return (frame / 16) * CELL;
	}

	@Override
	protected float getV1() {
		return getV0() + CELL;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

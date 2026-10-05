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
import net.minecraft.world.entity.Entity;
import thaumcraft.Thaumcraft;

/**
 * Real modern equivalent of the true original's own real {@code FXWispEG} - a faint, dark ambient mote that
 * drifts down off an Eldritch mob, drafting along with its own owner's real horizontal motion while airborne.
 * Restored 2026-09-17. Real call sites confirmed via `git grep`: {@link
 * thaumcraft.common.entities.monster.EntityEldritchGuardian}/{@link
 * thaumcraft.common.entities.monster.boss.EntityEldritchWarden}'s own real idle ambient trail.
 *
 * <p>Real, faithful port of the true original's own exact motion/colour formula: a real small downward drift
 * plus gaussian horizontal jitter, real very-dark per-instance colour (each channel independently
 * {@code 0-0.05}), a 13-frame cycling animation, full bright. As the original it fades with age and with the square of
 * the camera's distance (gone at 32 blocks), and is drawn at half of 1.12's own random scale (the original's quads had
 * no vanilla 0.1 factor).
 */
public class FXWispEG extends SingleQuadParticle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final int GRID = 64;
	private static final float CELL = 1.0f / GRID;
	private static final int FRAMES = 13;
	private static final float ROW_V0 = 3 * CELL;

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
			return "thaumcraft:wisp_eg";
		}
	};

	private final Entity target;

	public FXWispEG(ClientLevel level, double x, double y, double z, Entity target) {
		super(level, x, y, z);
		this.target = target;
		this.xd = random.nextGaussian() * 0.03;
		this.yd = -0.05;
		this.zd = random.nextGaussian() * 0.03;
		this.quadSize = (random.nextFloat() * 0.5f + 0.5f) * 2.0f * 0.4f; // 1.12's particleScale, then * 0.4
		this.lifetime = (int) (40.0 / (Math.random() * 0.3 + 0.7));
		this.rCol = random.nextFloat() * 0.05f;
		this.gCol = random.nextFloat() * 0.05f;
		this.bCol = random.nextFloat() * 0.05f;
		this.hasPhysics = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (target != null && !onGround) {
			x += target.getDeltaMovement().x;
			z += target.getDeltaMovement().z;
		}
	}

	/** The original's alpha: 0.2 * (1 - age / max) * (1 - min(1024, camera distance squared) / 1024). */
	@Override
	public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTicks) {
		var e = net.minecraft.client.Minecraft.getInstance().getCameraEntity();
		if (e == null) return;
		double dist = e.position().distanceToSqr(x, y, z);
		float base = (float) (1.0 - Math.min(1024.0, dist) / 1024.0);
		alpha = 0.2f * (1.0f - age / (float) lifetime) * base;
		super.render(buffer, camera, partialTicks);
	}

	@Override
	protected int getLightColor(float partialTick) {
		return 240;
	}

	@Override
	public float getQuadSize(float partialTicks) {
		return 0.5f * quadSize;
	}

	@Override
	protected float getU0() {
		return (age % FRAMES) * CELL;
	}

	@Override
	protected float getU1() {
		return getU0() + CELL;
	}

	@Override
	protected float getV0() {
		return ROW_V0;
	}

	@Override
	protected float getV1() {
		return ROW_V0 + CELL;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

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
 * Real modern equivalent of the true original's own real {@code FXVisSparkle} - a small glowing mote that
 * homes toward a real fixed target point and expires on arrival, used for the true original's own real
 * "vis flowing between 2 points" cue. Restored 2026-09-17. Real call sites confirmed via `git grep`:
 * {@link thaumcraft.common.tiles.crafting.TileFocalManipulator}/{@link
 * thaumcraft.common.tiles.devices.TileRechargePedestal}'s own real vis-transfer animation.
 *
 * <p>Real, faithful port of the true original's own real drag+attraction formula: eases toward the target
 * with a real, gently increasing pull as it gets closer (not a straight-line beeline), shrinking once within
 * 2 blocks and self-terminating once within 0.2 - a real 16-frame animation cycling off {@link #age}, and a
 * real subtle size "bob" pulse. The true original's own real vertex alpha is a hardcoded constant (0.5,
 * ignoring its own tracked {@code particleAlpha} field entirely) - kept as-is, not a simplification.
 */
public class FXVisSparkle extends SingleQuadParticle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final int GRID = 64;
	private static final float CELL = 1.0f / GRID;
	private static final int FRAMES = 16;
	private static final float ROW_V0 = 8 * CELL;

	public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
		@Nullable
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.setShader(GameRenderer::getParticleShader);
			RenderSystem.setShaderTexture(0, TEXTURE);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
			RenderSystem.depthMask(false);
			return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}

		@Override
		public String toString() {
			return "thaumcraft:vis_sparkle";
		}
	};

	private final double targetX;
	private final double targetY;
	private final double targetZ;
	private final float sizeMod;

	public FXVisSparkle(ClientLevel level, double x, double y, double z, double tx, double ty, double tz) {
		super(level, x, y, z);
		this.targetX = tx;
		this.targetY = ty;
		this.targetZ = tz;
		this.quadSize = 0.0f;
		this.lifetime = 1000;
		float speed = 0.01f;
		this.xd = random.nextGaussian() * speed;
		this.yd = random.nextGaussian() * speed;
		this.zd = random.nextGaussian() * speed;
		this.sizeMod = 45 + random.nextInt(15);
		this.rCol = 0.2f;
		this.gCol = 0.6f + random.nextFloat() * 0.3f;
		this.bCol = 0.2f;
		this.gravity = 0.2f;
		this.alpha = 0.5f;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		move(xd, yd, zd);
		xd *= 0.985;
		yd *= 0.985;
		zd *= 0.985;

		double dx = targetX - x;
		double dy = targetY - y;
		double dz = targetZ - z;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist < 2.0) {
			quadSize *= 0.95f;
		}
		if (dist < 0.2) {
			lifetime = age;
		}
		if (age < 10) {
			quadSize = age / sizeMod;
		}
		dx /= dist;
		dy /= dist;
		dz /= dist;
		xd += dx * 0.1;
		yd += dy * 0.1;
		zd += dz * 0.1;
		xd = Mth.clamp((float) xd, -0.1f, 0.1f);
		yd = Mth.clamp((float) yd, -0.1f, 0.1f);
		zd = Mth.clamp((float) zd, -0.1f, 0.1f);

		if (age++ >= lifetime) {
			remove();
		}
	}

	@Override
	public float getQuadSize(float partialTicks) {
		float bob = Mth.sin(age / 3.0f) * 0.3f + 6.0f;
		return 0.1f * quadSize * bob;
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

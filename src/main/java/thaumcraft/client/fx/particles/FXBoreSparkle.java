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
import net.minecraft.world.entity.Entity;
import thaumcraft.Thaumcraft;

/**
 * Real modern equivalent of the true original's own {@code FXBoreSparkle} - a small homing spark, real
 * restored 2026-09-15 as part of {@link thaumcraft.common.entities.construct.EntityArcaneBore}'s real
 * digging FX (see {@code ClientFxHandlers#handleBoreDig}). Real quarter-chance sibling of {@link
 * FXBoreParticles} (matches the true original's own real {@code boreDigFx} weighting exactly).
 *
 * <p>Single billboarded quad, same real "extend {@link SingleQuadParticle}, only override the grid UV"
 * approach {@link FXGeneric} already established - a real 4-frame flicker animation on a fixed row of the
 * shared {@code particles.png} sheet (row 4 of 64, matching the true original's own real hardcoded UV math
 * exactly, not routed through {@code FXGeneric}'s own configurable grid since this was never generic in the
 * true original either).
 *
 * <p>Real distinctive behaviour: homes in on a real moving target (the bore itself) rather than falling
 * under gravity - accelerates toward it every tick (clamped, decaying as it gets close), expiring once it
 * reaches the target's own block position or its own age limit, whichever comes first. The true original's
 * own {@code particleGravity = 0.2f} is real, harmless dead code (this class's own fully custom
 * {@code onUpdate} never actually reads it) - not reproduced, since it would do nothing either way.
 */
public class FXBoreSparkle extends SingleQuadParticle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final float CELL = 1.0f / 64.0f;
	private static final float ROW_V = 4.0f * CELL;

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
			return "thaumcraft:bore_sparkle";
		}
	};

	@Nullable
	private Entity target;
	private double targetX, targetY, targetZ;

	public FXBoreSparkle(ClientLevel level, double x, double y, double z, double tx, double ty, double tz) {
		super(level, x, y, z);
		this.rCol = 0.2f;
		this.gCol = 0.6f + random.nextFloat() * 0.3f;
		this.bCol = 0.2f;
		this.alpha = 1.0f;
		this.quadSize = 0.1f * (random.nextFloat() * 0.5f + 0.5f);
		this.hasPhysics = false;

		this.targetX = tx;
		this.targetY = ty;
		this.targetZ = tz;
		double dx = tx - x, dy = ty - y, dz = tz - z;
		int base = Math.max(1, (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 10.0f));
		setLifetime(base / 2 + random.nextInt(base));

		float jitter = 0.01f;
		this.xd = random.nextGaussian() * jitter;
		this.yd = random.nextGaussian() * jitter;
		this.zd = random.nextGaussian() * jitter;
	}

	public FXBoreSparkle(ClientLevel level, double x, double y, double z, Entity target) {
		this(level, x, y, z, target.getX(), target.getY() + target.getEyeHeight(), target.getZ());
		this.target = target;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (target != null) {
			targetX = target.getX();
			targetY = target.getY() + target.getEyeHeight();
			targetZ = target.getZ();
		}
		if (age++ >= lifetime
				|| (Mth.floor(x) == Mth.floor(targetX) && Mth.floor(y) == Mth.floor(targetY) && Mth.floor(z) == Mth.floor(targetZ))) {
			remove();
			return;
		}
		move(xd, yd, zd);
		xd *= 0.985;
		yd *= 0.95;
		zd *= 0.985;

		double dx = targetX - x, dy = targetY - y, dz = targetZ - z;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double clamp = Math.min(0.25, dist / 15.0);
		if (dist < 2.0) {
			quadSize *= 0.9f;
		}
		dx /= dist;
		dy /= dist;
		dz /= dist;
		xd += dx * clamp;
		yd += dy * clamp;
		zd += dz * clamp;
		xd = Mth.clamp(xd, -clamp, clamp);
		yd = Mth.clamp(yd, -clamp, clamp);
		zd = Mth.clamp(zd, -clamp, clamp);
		xd += random.nextGaussian() * 0.01;
		yd += random.nextGaussian() * 0.01;
		zd += random.nextGaussian() * 0.01;
	}

	@Override
	public float getQuadSize(float scaleFactor) {
		float bob = Mth.sin(age / 3.0f) * 0.5f + 1.0f;
		return quadSize * bob;
	}

	@Override
	protected float getU0() {
		int frame = age % 4;
		return frame * CELL;
	}

	@Override
	protected float getU1() {
		return getU0() + CELL;
	}

	@Override
	protected float getV0() {
		return ROW_V;
	}

	@Override
	protected float getV1() {
		return ROW_V + CELL;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

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
 * Real modern equivalent of the true original's own {@code FXVent2} - see {@link FXVent}'s own doc comment
 * for the shared real growing-then-fading puff shape both classes use. Restored 2026-09-16 alongside {@link
 * thaumcraft.common.tiles.devices.TilePotionSprayer}'s own real spray-vent cue - its only real call site in
 * the true original.
 *
 * <p>Real, checked-not-assumed differences from {@link FXVent}, not a copy: a real per-particle random
 * gravity (positive or negative, {@code gaussian * 0.0075} - drifts up or down, not always down) instead of a
 * fixed downward one; a real per-channel colour jitter ({@code +/- gaussian * 0.05}, clamped) instead of a
 * flat colour; a real faster growth rate ({@code *1.2}/tick, not {@code *1.15}); and no initial-heading
 * jitter at all - the true original's own version uses the given motion vector exactly as passed in, matching
 * its own real "spray straight out along the sprayer's facing" intent rather than {@link FXVent}'s own
 * "scatter loosely" one.
 */
public class FXVent2 extends SingleQuadParticle {

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
			return "thaumcraft:vent2";
		}
	};

	private float targetScale = 1.0f;
	private final double randomGravity;

	public FXVent2(ClientLevel level, double x, double y, double z, double mx, double my, double mz, int color) {
		super(level, x, y, z);
		this.quadSize = random.nextFloat() * 0.1f + 0.05f;
		this.rCol = Mth.clamp(((color >> 16) & 0xFF) / 255.0f + (float) (random.nextGaussian() * 0.05), 0.0f, 1.0f);
		this.gCol = Mth.clamp(((color >> 8) & 0xFF) / 255.0f + (float) (random.nextGaussian() * 0.05), 0.0f, 1.0f);
		this.bCol = Mth.clamp((color & 0xFF) / 255.0f + (float) (random.nextGaussian() * 0.05), 0.0f, 1.0f);
		this.alpha = 0.4f;
		this.hasPhysics = true;
		this.xd = mx;
		this.yd = my;
		this.zd = mz;
		this.randomGravity = random.nextGaussian() * 0.0075;
	}

	/** @return this particle for real fluent-setter chaining, matching the true original's own real API. */
	public FXVent2 setScale(float scale) {
		this.quadSize *= scale;
		this.targetScale *= scale;
		return this;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		age++;
		if (quadSize >= targetScale) {
			remove();
			return;
		}
		yd += randomGravity;
		move(xd, yd, zd);
		xd *= 0.85;
		yd *= 0.85;
		zd *= 0.85;
		if (quadSize < targetScale) {
			quadSize *= 1.2f;
		}
		if (quadSize > targetScale) {
			quadSize = targetScale;
		}
		if (onGround) {
			xd *= 0.7;
			zd *= 0.7;
		}
		this.alpha = 0.4f * ((targetScale - quadSize) / targetScale);
	}

	/** See {@link FXVent#getQuadSize}'s own doc comment for the same real {@code quadSize} vs. render-size
	 * split. */
	@Override
	public float getQuadSize(float partialTicks) {
		return quadSize * 0.3f;
	}

	@Override
	protected float getU0() {
		int part = Mth.clamp(1 + (int) (quadSize / targetScale * 4.0f), 1, 4);
		return (part % 16) * CELL;
	}

	@Override
	protected float getU1() {
		return getU0() + CELL;
	}

	@Override
	protected float getV0() {
		return 0.0f;
	}

	@Override
	protected float getV1() {
		return CELL;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

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
 * Real modern equivalent of the true original's own {@code FXVent} - a small growing-then-fading steam/vent
 * puff off the shared {@code particles.png} sheet, restored 2026-09-16 alongside {@link
 * thaumcraft.common.tiles.crafting.TileGolemBuilder}'s own real press-ram threshold cues (the true original's
 * own only real call site, found while restoring that device's renderer - see that class's own {@code
 * tickPress} doc comment).
 *
 * <p>Real, faithful port of the true original's own real growth/fade shape: spawns at a small random scale,
 * grows toward a real target scale (a real {@code setScale} multiplier, defaulting to {@code 1.0}) at a real
 * {@code *1.15}/tick rate, fading out as it approaches that target (alpha scales with {@code (target-scale)/
 * target} - opaque when fresh/small, transparent as it finishes growing, a real "dissipating puff" look) -
 * removed the instant it reaches full scale. Real physics: a fixed small downward drift (gravity {@code
 * 0.0025}), {@code 0.85} air drag, {@code 0.7} extra ground-friction while resting. Real initial-heading
 * jitter (normalize + real gaussian spread, matching the true original's own {@code setHeading} exactly).
 *
 * <p>Reuses {@link SingleQuadParticle}'s own real camera-facing billboard - a single flat quad, same real
 * "check the easy way first" shape {@code FXBoreSparkle}/{@code FXSwarm}/{@code FXGolemOrb} already
 * established - addressing frame {@code 1 + floor(scale/target*4)} (a real 1-4 frame growth-animation) on the
 * same real {@code gridSize=64} row-0 region the true original's own exact formula used.
 */
public class FXVent extends SingleQuadParticle {

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
			return "thaumcraft:vent";
		}
	};

	private float targetScale = 1.0f;

	public FXVent(ClientLevel level, double x, double y, double z, double mx, double my, double mz, int color) {
		super(level, x, y, z);
		this.quadSize = random.nextFloat() * 0.1f + 0.05f;
		this.rCol = ((color >> 16) & 0xFF) / 255.0f;
		this.gCol = ((color >> 8) & 0xFF) / 255.0f;
		this.bCol = (color & 0xFF) / 255.0f;
		this.alpha = 0.4f;
		this.hasPhysics = true;
		setHeading(mx, my, mz, 0.125f, 5.0f);
	}

	/** @return this particle for real fluent-setter chaining, matching the true original's own real API. */
	public FXVent setScale(float scale) {
		this.quadSize *= scale;
		this.targetScale *= scale;
		return this;
	}

	private void setHeading(double mx, double my, double mz, float speed, float jitter) {
		double len = Math.sqrt(mx * mx + my * my + mz * mz);
		mx /= len;
		my /= len;
		mz /= len;
		mx += random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * 0.0075 * jitter;
		my += random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * 0.0075 * jitter;
		mz += random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * 0.0075 * jitter;
		xd = mx * speed;
		yd = my * speed;
		zd = mz * speed;
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
		yd += 0.0025;
		move(xd, yd, zd);
		xd *= 0.85;
		yd *= 0.85;
		zd *= 0.85;
		if (quadSize < targetScale) {
			quadSize *= 1.15f;
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

	/** Real, faithful split between {@link #quadSize} (a real growth/threshold-tracking value, {@code 0.05}
	 * to {@link #targetScale}) and the actual real rendered half-width ({@code 0.3 * quadSize}) - matches the
	 * true original's own real distinct {@code particleScale} vs. render-size ({@code var12 = 0.3f *
	 * particleScale}) split exactly. */
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

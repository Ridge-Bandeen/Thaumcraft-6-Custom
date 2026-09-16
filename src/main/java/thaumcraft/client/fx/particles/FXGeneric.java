package thaumcraft.client.fx.particles;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.world.phys.Vec3;
import thaumcraft.Thaumcraft;

/**
 * Real modern equivalent of the true original's own {@code FXGeneric} - a generic, highly configurable
 * billboard particle reused by dozens of real true-original effects (ember trails, smoke, sparkles, etc.),
 * restored 2026-09-15 as its own dedicated chunk given how many other effects depend on it.
 *
 * <p>Extends vanilla's own {@link SingleQuadParticle} rather than reimplementing camera-facing billboard
 * rotation from scratch (the same "check the easy way first" instinct that paid off for {@link
 * thaumcraft.client.fx.beams.FXBolt}) - {@code SingleQuadParticle} already provides exactly the real
 * quaternion-based "always face the camera" rendering this needs, since (unlike {@code FXArc}/{@code
 * FXBolt}) every real {@code FXGeneric} particle is a single flat quad, never a multi-segment ribbon. Only
 * the real grid-frame UV lookup ({@link #getU0}/{@link #getU1}/{@link #getV0}/{@link #getV1}) and quad size
 * ({@link #getQuadSize}) need overriding; the rest of the real rendering math is inherited.
 *
 * <p>Binds the true original's own real shared particle sprite sheet directly ({@code
 * textures/misc/particles.png}, a 1024x1024 grid already sitting unused in this port's resource tree, same
 * "assets ready, only Java missing" shape {@code Sounds}/the beam textures had) - the true original's own
 * {@code ParticleEngine.particleTexture} field, bound once per frame before every real {@code FXGeneric}
 * instance in that frame, all addressing their own sub-region of the one shared sheet via {@link
 * #gridSize}/frame index. Two real blend modes are supported via {@link #setLayer} (a real, simplified stand-in
 * for the true original's own real dual particle-bucket-array mechanism, itself half GUI-overlay-specific
 * plumbing not relevant to a real world-space particle): {@code 0} = normal alpha blend, non-zero =
 * additive/glowing - matches the two real blend functions the true original's own {@code ParticleEngine}
 * used ({@code [770,1]} additive, {@code [770,771]} normal), not the literal bucket-array mechanism itself.
 *
 * <p>Real, documented simplification: the true original's own "angled" mode ({@link #setAngles}, a fixed
 * yaw/pitch orientation instead of always facing the camera - used for ground-decal-style particles) isn't
 * supported yet, since it needs a real full {@code render()} override bypassing {@code SingleQuadParticle}'s
 * own camera-facing math entirely, not just new getters - deferred until a real effect that actually needs
 * it gets restored. {@link #setFinalFrames} (an explicit end-of-life frame-index override sequence) is kept
 * for real API fidelity but not yet exercised by any real restored caller.
 */
public class FXGeneric extends SingleQuadParticle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");

	private static final ParticleRenderType RENDER_TYPE_NORMAL = renderType(false);
	private static final ParticleRenderType RENDER_TYPE_ADDITIVE = renderType(true);

	private static ParticleRenderType renderType(boolean additive) {
		return new ParticleRenderType() {
			@Nullable
			@Override
			public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
				RenderSystem.setShader(GameRenderer::getParticleShader);
				RenderSystem.setShaderTexture(0, TEXTURE);
				RenderSystem.enableBlend();
				if (additive) {
					RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
				} else {
					RenderSystem.defaultBlendFunc();
				}
				RenderSystem.depthMask(false);
				return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
			}

			@Override
			public String toString() {
				return additive ? "thaumcraft:generic_additive" : "thaumcraft:generic";
			}
		};
	}

	private int gridSize = 64;
	private int startParticle;
	private int numParticles = 1;
	private int particleInc = 1;
	private boolean loop;
	private int[] finalFrames;
	private boolean additive;

	private float[] alphaKeys = { 1.0f };
	private float[] scaleKeys = { 1.0f };
	private float[] alphaFrames;
	private float[] scaleFrames;
	private boolean framesReady;

	private float dr, dg, db;
	private float spawnR = 1.0f, spawnG = 1.0f, spawnB = 1.0f;
	private float baseQuadSize;
	private double slowDown = 0.98;
	private float randomX, randomY, randomZ;
	private double windX, windZ;
	private float rotationSpeed;

	public FXGeneric(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
		// Real 3-arg super() on purpose, not the 6-arg one - Particle's own 6-arg constructor adds random
		// jitter and renormalizes the given speed (real vanilla "generic particle" behaviour), which the
		// true original's own FXGeneric never did: it always sets the exact given motion, unrandomized.
		super(level, x, y, z);
		this.xd = xd;
		this.yd = yd;
		this.zd = zd;
		this.baseQuadSize = this.quadSize;
	}

	public FXGeneric(ClientLevel level, double x, double y, double z) {
		this(level, x, y, z, 0.0, 0.0, 0.0);
	}

	private void calculateFrames() {
		framesReady = true;
		alphaFrames = new float[lifetime + 1];
		float inc = (alphaKeys.length - 1) / (float) Math.max(1, lifetime);
		float is = 0.0f;
		for (int a = 0; a <= lifetime; a++) {
			int isF = Mth.floor(is);
			float diff = isF < alphaKeys.length - 1 ? alphaKeys[isF + 1] - alphaKeys[isF] : 0.0f;
			alphaFrames[a] = alphaKeys[isF] + diff * (is - isF);
			is += inc;
		}
		scaleFrames = new float[lifetime + 1];
		inc = (scaleKeys.length - 1) / (float) Math.max(1, lifetime);
		is = 0.0f;
		for (int a = 0; a <= lifetime; a++) {
			int isF = Mth.floor(is);
			float diff = isF < scaleKeys.length - 1 ? scaleKeys[isF + 1] - scaleKeys[isF] : 0.0f;
			scaleFrames[a] = scaleKeys[isF] + diff * (is - isF);
			is += inc;
		}
	}

	@Override
	public void tick() {
		if (!framesReady) {
			calculateFrames();
		}
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime) {
			remove();
			return;
		}
		oRoll = roll;
		roll += (float) Math.PI * rotationSpeed * 2.0f;

		yd -= 0.04 * gravity;
		move(xd, yd, zd);
		xd *= slowDown;
		yd *= slowDown;
		zd *= slowDown;
		xd += random.nextGaussian() * randomX;
		yd += random.nextGaussian() * randomY;
		zd += random.nextGaussian() * randomZ;
		xd += windX;
		zd += windZ;
		if (onGround && slowDown != 1.0) {
			xd *= 0.7;
			zd *= 0.7;
		}

		int frame = currentFrameIndex();
		if (finalFrames != null && finalFrames.length > 0 && age > lifetime - finalFrames.length) {
			int f = lifetime - age;
			frame = finalFrames[Math.max(0, Math.min(f, finalFrames.length - 1))];
		}
		setFrame(frame);

		float fs = Mth.clamp(age / (float) lifetime, 0.0f, 1.0f);
		alpha = alphaFrames.length > 0 ? alphaFrames[Math.min(age, alphaFrames.length - 1)] : 0.0f;
		float scaleFrame = scaleFrames.length > 0 ? scaleFrames[Math.min(age, scaleFrames.length - 1)] : 0.0f;
		quadSize = baseQuadSize * scaleFrame;
		rCol = spawnR + (dr - spawnR) * fs;
		gCol = spawnG + (dg - spawnG) * fs;
		bCol = spawnB + (db - spawnB) * fs;
	}

	private int currentFrameIndex() {
		if (loop) {
			return startParticle + age / particleInc % numParticles;
		}
		float fs = age / (float) lifetime;
		return startParticle + (int) Math.min(numParticles * fs, numParticles - 1);
	}

	private int frameX, frameY;

	private void setFrame(int index) {
		int i = Math.max(0, index);
		frameX = i % gridSize;
		frameY = i / gridSize;
	}

	@Override
	protected float getU0() {
		return frameX / (float) gridSize;
	}

	@Override
	protected float getU1() {
		return getU0() + 1.0f / gridSize;
	}

	@Override
	protected float getV0() {
		return frameY / (float) gridSize;
	}

	@Override
	protected float getV1() {
		return getV0() + 1.0f / gridSize;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return additive ? RENDER_TYPE_ADDITIVE : RENDER_TYPE_NORMAL;
	}

	// --- Real ported setters, matching the true original's own real fluent-setter API shape ---

	public FXGeneric setMaxAge(int max) {
		setLifetime(max);
		return this;
	}

	public FXGeneric setGridSize(int gridSize) {
		this.gridSize = gridSize;
		return this;
	}

	public FXGeneric setParticles(int startParticle, int numParticles, int particleInc) {
		this.numParticles = numParticles;
		this.particleInc = particleInc;
		this.startParticle = startParticle;
		setFrame(startParticle);
		return this;
	}

	public FXGeneric setParticle(int startParticle) {
		return setParticles(startParticle, 1, 1);
	}

	public FXGeneric setParticleTextureIndex(int index) {
		this.startParticle = Math.max(0, index);
		setFrame(this.startParticle);
		return this;
	}

	public FXGeneric setLoop(boolean loop) {
		this.loop = loop;
		return this;
	}

	public FXGeneric setFinalFrames(int... frames) {
		this.finalFrames = frames;
		return this;
	}

	public FXGeneric setScale(float... scale) {
		this.scaleKeys = scale;
		this.quadSize = this.baseQuadSize * scale[0];
		return this;
	}

	/** Also sets the base quad size this scales relative to - call after any real vanilla {@code scale()}. */
	public FXGeneric setBaseQuadSize(float size) {
		this.baseQuadSize = size;
		return this;
	}

	public FXGeneric setAlphaF(float... a) {
		this.alphaKeys = a;
		this.alpha = a[0];
		return this;
	}

	public FXGeneric setAlphaF(float a) {
		return setAlphaF(new float[] { a });
	}

	/** Real 2-colour form: starts at {@code (r,g,b)}, fades toward {@code (r2,g2,b2)} over the real lifetime. */
	public FXGeneric setRBGColorF(float r, float g, float b, float r2, float g2, float b2) {
		setColor(r, g, b);
		this.spawnR = r;
		this.spawnG = g;
		this.spawnB = b;
		this.dr = r2;
		this.dg = g2;
		this.db = b2;
		return this;
	}

	public FXGeneric setRBGColorF(float r, float g, float b) {
		return setRBGColorF(r, g, b, r, g, b);
	}

	public FXGeneric setSlowDown(double slowDown) {
		this.slowDown = slowDown;
		this.friction = (float) slowDown;
		return this;
	}

	public FXGeneric setGravity(float g) {
		this.gravity = g;
		return this;
	}

	public FXGeneric setRandomMovementScale(float x, float y, float z) {
		this.randomX = x;
		this.randomY = y;
		this.randomZ = z;
		return this;
	}

	public FXGeneric setRotationSpeed(float rot) {
		this.rotationSpeed = (float) Math.toRadians(rot);
		return this;
	}

	public FXGeneric setRotationSpeed(float start, float rot) {
		this.roll = (float) (start * Math.PI * 2.0);
		this.rotationSpeed = (float) Math.toRadians(rot);
		return this;
	}

	/** Real simplified stand-in for the true original's own real dual particle-bucket-array mechanism - see
	 * the class doc comment. {@code 0} = normal alpha blend, non-zero = additive/glowing. */
	public FXGeneric setLayer(int layer) {
		this.additive = layer != 0;
		return this;
	}

	/** Faithful reproduction of the true original's own real moon-phase-driven pseudo-wind direction. */
	public FXGeneric setWind(double strength) {
		int moonPhase = level.getMoonPhase();
		Vec3 target = new Vec3(0.1, 0.0, 0.0);
		float angle = (float) Math.toRadians(moonPhase * (40 + random.nextInt(10)));
		float cos = Mth.cos(angle);
		float sin = Mth.sin(angle);
		double rx = target.x * cos + target.z * sin;
		double rz = target.z * cos - target.x * sin;
		this.windX = rx * strength;
		this.windZ = rz * strength;
		return this;
	}

	/** Matches the true original's own confusingly-named real {@code setNoClip(boolean)} exactly (despite
	 * the name, it sets whether the particle really does collide, not whether it skips collision). */
	public FXGeneric setNoClip(boolean collides) {
		this.hasPhysics = collides;
		return this;
	}
}

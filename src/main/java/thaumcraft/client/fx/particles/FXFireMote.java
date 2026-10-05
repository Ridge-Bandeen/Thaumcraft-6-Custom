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
import thaumcraft.Thaumcraft;

/**
 * Real modern equivalent of the true original's own real {@code FXFireMote} - a small glowing, shrinking,
 * fading spark off the shared {@code particles.png} sheet (fixed frame index 7). Restored 2026-09-17, one of
 * a batch of small real particle classes this port's own scoping notes had flagged as "not yet scoped in
 * detail" - real call sites confirmed via `git grep` before building: {@link
 * thaumcraft.common.entities.projectile.EntityFocusProjectile}'s own real Fire/Flame focus trail and {@link
 * thaumcraft.common.entities.projectile.EntityAlumentum}/{@link
 * thaumcraft.common.entities.projectile.EntityCausalityCollapser}'s own real flight trail (via a shared
 * {@code FXDispatcher.drawAlumentum} wrapper - same particle class, different default colour/scale).
 *
 * <p>Real, checked-not-assumed detail: the true original's own real constructor sets {@code motionX/Y/Z} but
 * its own {@code onUpdate} never calls {@code move()} directly - the actual movement comes from vanilla's own
 * inherited {@code Particle#onUpdate} physics (real drag, real zero gravity - the true original never
 * overrides {@code particleGravity} for this class), matched here by calling {@link #tick() super.tick()}
 * first exactly like the original's own {@code super.onUpdate()} call, then layering the real custom
 * slow-aging/shrink/fade logic on top. Real slow-aging quirk kept: the true original only advances its own
 * visual age 1-in-6 real ticks (a real "this mote visually lives longer than its own 16-tick counter
 * suggests" effect) - tracked here via a separate {@link #visualAge} field so the base class's own real
 * {@code lifetime}/{@code age} bookkeeping (used for the standard tick-and-physics call) doesn't fight it.
 */
public class FXFireMote extends SingleQuadParticle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final int GRID = 64;
	private static final float CELL = 1.0f / GRID;
	private static final int MAX_VISUAL_AGE = 16;

	/** The original's glow layer 0 (additive). */
	public static final ParticleRenderType RENDER_TYPE = renderType(true);
	/** Its layer 1 (normal blend) - the Alumentum's motes, and half of the focus projectile's. */
	public static final ParticleRenderType RENDER_TYPE_NORMAL = renderType(false);

	private static ParticleRenderType renderType(boolean additive) {
		return new ParticleRenderType() {
			@Nullable
			@Override
			public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
				RenderSystem.setShader(GameRenderer::getParticleShader);
				RenderSystem.setShaderTexture(0, TEXTURE);
				RenderSystem.enableBlend();
				if (additive) {
					RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
				} else {
					RenderSystem.defaultBlendFunc();
				}
				RenderSystem.depthMask(false);
				return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
			}

			@Override
			public String toString() {
				return additive ? "thaumcraft:fire_mote" : "thaumcraft:fire_mote_normal";
			}
		};
	}

	private int layer;
	/** The original's setAlphaF: every caller passed 0.5. */
	private float baseAlpha = 0.5f;

	/** The original's glow layer: 0 additive, 1 normal blend. */
	public FXFireMote setLayer(int layer) {
		this.layer = layer;
		return this;
	}

	private final float baseScale;
	private int visualAge;

	public FXFireMote(ClientLevel level, double x, double y, double z, double mx, double my, double mz, float r, float g, float b, float scale) {
		super(level, x, y, z);
		this.rCol = r;
		this.gCol = g;
		this.bCol = b;
		this.quadSize = scale;
		this.baseScale = scale;
		this.xd = mx;
		this.yd = my;
		this.zd = mz;
		this.hasPhysics = true;
		this.lifetime = Integer.MAX_VALUE;
	}

	@Override
	public void tick() {
		super.tick();
		if (random.nextInt(6) == 0) {
			visualAge++;
		}
		if (visualAge >= MAX_VISUAL_AGE) {
			remove();
			return;
		}
		float lifespan = visualAge / (float) MAX_VISUAL_AGE;
		quadSize = baseScale - baseScale * lifespan;
		alpha = baseAlpha * (1.0f - lifespan);
	}

	@Override
	public float getQuadSize(float partialTicks) {
		return quadSize * 0.1f;
	}

	@Override
	protected float getU0() {
		return 7 * CELL;
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
		return layer == 1 ? RENDER_TYPE_NORMAL : RENDER_TYPE;
	}
}

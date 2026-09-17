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
 * <p>Real, checked-not-assumed detail: the true original's own real {@code onUpdate} never calls
 * {@code super.onUpdate()} at all and never actually moves the particle's own real tracked position - the
 * whole spiral motion is computed fresh every real FRAME inside {@code renderParticle} itself (a pure
 * render-time offset, not a real physics simulation) off {@code (particleAge + partialTick)}. This port bakes
 * the same real formula into the particle's own real {@code x}/{@code y}/{@code z} once per TICK instead (not
 * per frame) - a real, disclosed simplification: standard particle interpolation smooths between each tick's
 * own spiral position for free, which reads the same for a slow, few-tick-lived effect like this one without
 * needing a real custom {@code render()} override.
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
		this.quadSize = 1.0f;
		this.alpha = 0.66f;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;

		float r1 = start + 720.0f * (age / (float) lifetime);
		float r2 = 90.0f - 180.0f * (age / (float) lifetime);
		float mx = -Mth.sin(r1 / 180.0f * Mth.PI) * Mth.cos(r2 / 180.0f * Mth.PI);
		float mz = Mth.cos(r1 / 180.0f * Mth.PI) * Mth.cos(r2 / 180.0f * Mth.PI);
		float my = -Mth.sin(r2 / 180.0f * Mth.PI);
		x = originX + mx * radius;
		y = Math.max(originY + my * radius, minY + 0.1);
		z = originZ + mz * radius;

		alpha = 0.66f * (lifetime - age) / (float) lifetime;

		if (age++ >= lifetime) {
			remove();
		}
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

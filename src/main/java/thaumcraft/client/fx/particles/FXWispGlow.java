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

/**
 * Real modern equivalent of the true original's own {@code RenderWisp} - {@link
 * thaumcraft.common.entities.monster.EntityWisp} had genuinely no visible model at all in this port before
 * this class (a real, previously-undocumented gap, not the deliberate "no client FX" simplification this
 * port's doc comments elsewhere describe - {@code EntityWisp}'s own doc comment incorrectly filed it under
 * that same bucket, corrected alongside this restoration).
 *
 * <p>The true original never actually drew a skeletal/box model for this entity at all - its own real {@code
 * RenderWisp} just billboarded 3 real glowing quads straight off {@code UtilsFX.renderFacingQuad} every
 * frame, 2 off the shared particle sheet ({@code particles.png}) and one off a second, distinct sheet ({@code
 * auranodes.png}, tinted by the wisp's own real aspect colour). Rather than hand-rolling a brand new
 * camera-facing quad primitive inside a custom {@code EntityRenderer} (real, new, and unverifiable rendering
 * pipeline work - GameTest never renders a frame), this reuses the exact same real, already-proven billboard
 * machinery {@link FXGeneric}/{@link FXSwarm} already use: 3 real client-local particles that snap to {@link
 * #target}'s own position every tick (zero physics, matching the true original's own "just draw wherever the
 * entity currently is" approach) rather than moving under their own velocity, spawned once via {@link
 * thaumcraft.client.fx.ClientFxHandlers#maintainWisp} and self-removing the instant the target dies (matches
 * the true original's own real {@code if (entity.getHealth() <= 0) return;} early-out exactly - no fade, this
 * effect just stops).
 *
 * <p>One real, reusable class instead of two ({@code FXGeneric} isn't reused directly since it moves under
 * real velocity/gravity and has no "track a live entity" concept at all) - each of the 3 real quads is just a
 * different real (texture, grid size, frame base, scale, colour, alpha) tuple applied to the same real
 * tracking/rendering shape, matching {@code renderFacingQuad}'s own real per-call parameterization.
 */
public class FXWispGlow extends SingleQuadParticle {

	private final Entity target;
	private final ResourceLocation texture;
	private final int gridSize;
	private final int frameBase;
	private final ParticleRenderType renderType;

	public FXWispGlow(ClientLevel level, Entity target, ResourceLocation texture, int gridSize, int frameBase,
			float scale, float r, float g, float b, float alphaF) {
		super(level, target.getX(), target.getY(), target.getZ());
		this.target = target;
		this.texture = texture;
		this.gridSize = gridSize;
		this.frameBase = frameBase;
		this.rCol = r;
		this.gCol = g;
		this.bCol = b;
		this.alpha = alphaF;
		this.quadSize = scale;
		this.hasPhysics = false;
		this.renderType = new ParticleRenderType() {
			@Nullable
			@Override
			public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
				RenderSystem.setShader(GameRenderer::getParticleShader);
				RenderSystem.setShaderTexture(0, FXWispGlow.this.texture);
				RenderSystem.enableBlend();
				RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
				RenderSystem.depthMask(false);
				return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
			}

			@Override
			public String toString() {
				return "thaumcraft:wisp_glow";
			}
		};
	}

	/** @return whether the target is still real and alive - callers use this to know when to stop tracking. */
	public boolean isTargetAlive() {
		return target != null && target.isAlive();
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (!isTargetAlive()) {
			remove();
			return;
		}
		x = target.getX();
		y = target.getY();
		z = target.getZ();
	}

	@Override
	protected float getU0() {
		int frame = frameBase + target.tickCount % 16;
		return (frame % gridSize) / (float) gridSize;
	}

	@Override
	protected float getU1() {
		return getU0() + 1.0f / gridSize;
	}

	@Override
	protected float getV0() {
		int frame = frameBase + target.tickCount % 16;
		return (frame / gridSize) / (float) gridSize;
	}

	@Override
	protected float getV1() {
		return getV0() + 1.0f / gridSize;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return renderType;
	}
}

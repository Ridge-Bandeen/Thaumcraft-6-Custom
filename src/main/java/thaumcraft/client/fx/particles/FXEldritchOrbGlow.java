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
 * Real modern equivalent of the true original's own {@code RenderEldritchOrb}'s second (billboard glow) pass
 * - see {@link FXEldritchOrbSpikes}'s own doc comment for why this is a separate particle rather than folded
 * into that one. Same real {@link SingleQuadParticle}-tracking shape as {@link FXGolemOrb}, but genuinely
 * different real config, checked directly rather than assumed identical: a real {@code 64}-wide grid (not
 * {@code GOLEM_ORB}'s {@code 32}), a real 13-frame column cycle (not 6, and starting at column 0, not 1), a
 * real fixed {@code 0.75} scale with no pulsing "bob" animation at all (unlike {@code GOLEM_ORB}'s own real
 * bob), and real NORMAL alpha blending (the true original's own real {@code GL_SRC_ALPHA,
 * GL_ONE_MINUS_SRC_ALPHA} here, not {@code GOLEM_ORB}'s own real additive {@code GL_SRC_ALPHA, GL_ONE}).
 */
public class FXEldritchOrbGlow extends SingleQuadParticle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final int GRID = 64;
	private static final float CELL = 1.0f / GRID;
	private static final int ROW = 3;

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
			return "thaumcraft:eldritch_orb_glow";
		}
	};

	private final Entity target;

	public FXEldritchOrbGlow(ClientLevel level, Entity target) {
		super(level, target.getX(), target.getY(), target.getZ());
		this.target = target;
		this.rCol = 1.0f;
		this.gCol = 1.0f;
		this.bCol = 1.0f;
		this.alpha = 1.0f;
		this.quadSize = 0.375f;
		this.hasPhysics = false;
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
		int col = target.tickCount % 13;
		return col * CELL;
	}

	@Override
	protected float getU1() {
		return getU0() + CELL;
	}

	@Override
	protected float getV0() {
		return ROW * CELL;
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

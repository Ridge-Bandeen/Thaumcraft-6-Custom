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
 * Real modern equivalent of the true original's own real {@code FXSlimyBubble} - a small rising bubble off
 * the shared {@code particles.png} sheet, real 3-phase frame animation (a real "forming", "rising", and
 * "popping" sequence). Restored 2026-09-17. Real call site confirmed via `git grep`: {@link
 * thaumcraft.common.blocks.misc.BlockFluidDeath}'s own real random ambient surface-bubble tick.
 *
 * <p>Real, faithful port of the true original's own exact frame-index/motion state machine: forms over its
 * first 6 real ticks (frames 144-146, with a real small upward pop at tick 4), rises with real slowly
 * increasing upward speed for the bulk of its real life (frames 147-150), then decelerates and settles into
 * its final "popping" frames for its last 4 real ticks. The true original's own real {@code onUpdate} never
 * calls {@code super.onUpdate()} at all - position only ever changes via its own explicit {@code posY +=
 * motionY}, matched here directly rather than assumed to inherit standard physics.
 */
public class FXSlimyBubble extends SingleQuadParticle {

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
			return "thaumcraft:slimy_bubble";
		}
	};

	private int frame = 144;

	public FXSlimyBubble(ClientLevel level, double x, double y, double z, float scale) {
		super(level, x, y, z);
		this.rCol = 1.0f;
		this.gCol = 1.0f;
		this.bCol = 1.0f;
		this.quadSize = scale;
		this.lifetime = 15 + random.nextInt(5);
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		boolean expire = age >= lifetime;

		if (age < 6) {
			frame = 144 + age / 2;
			if (age == 4) {
				y += 0.1;
			}
		} else if (age < lifetime - 4) {
			yd += 0.005;
			frame = 147 + (age % 4) / 2;
		} else {
			yd /= 2.0;
			frame = 150 - (lifetime - age) / 2;
		}
		y += yd;

		age++;
		if (expire) {
			remove();
		}
	}

	@Override
	public float getQuadSize(float partialTicks) {
		return quadSize;
	}

	@Override
	protected float getU0() {
		return (frame % 16) * CELL;
	}

	@Override
	protected float getU1() {
		return getU0() + CELL;
	}

	@Override
	protected float getV0() {
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

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
 * Real modern equivalent of the true original's own {@code RenderElectricOrb} - {@link
 * thaumcraft.common.entities.projectile.EntityGolemOrb} (a {@link
 * thaumcraft.common.entities.monster.cult.EntityCultistCleric}'s real "blast" ranged attack) had genuinely no
 * visible model at all before this (a real, previously-undocumented gap, found during the same
 * {@code RenderFocusProjectile}-placeholder cross-check that fixed {@code WISP}/{@code FOCUS_MINE} - see the
 * Custom Mob Models/Renderers TODO entry).
 *
 * <p>Same real "reuse the already-proven {@link SingleQuadParticle} tracking-particle pattern instead of a
 * brand-new {@code EntityRenderer} primitive" call {@link FXWispGlow} already established - a single real
 * billboarded quad off the shared {@code particles.png} sheet (at a real coarser {@code 32}-wide grid, not
 * the {@code 64}-wide one {@link FXGeneric}/{@code FXBoreSparkle}/{@code FXWispGlow} use elsewhere - the true
 * original's own real raw UV fractions for this specific effect are exactly grid-cell-aligned to 32, not 64,
 * confirmed by checking they land on clean {@code n/32} boundaries), snapping to the target entity's own
 * position every tick with a real pulsing "bob" scale (matching the true original's own real {@code
 * sin(ticksExisted/5)*0.2+0.2} formula exactly) and a real 6-frame flicker. Picks one of 2 real fixed UV rows
 * depending on {@link thaumcraft.common.entities.projectile.EntityGolemOrb#isRed()} - the true original's own
 * real "red" vs. default orb colour variant, now a real synced field (was server-only before, since the
 * client never needed it until this real visual existed to consume it).
 */
public class FXGolemOrb extends SingleQuadParticle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final int GRID = 32;
	private static final float CELL = 1.0f / GRID;

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
			return "thaumcraft:golem_orb";
		}
	};

	private final Entity target;
	private final int row;

	public FXGolemOrb(ClientLevel level, Entity target, boolean red) {
		super(level, target.getX(), target.getY(), target.getZ());
		this.target = target;
		this.row = red ? 6 : 7;
		this.rCol = 1.0f;
		this.gCol = 1.0f;
		this.bCol = 1.0f;
		this.alpha = 0.8f;
		this.quadSize = 0.5f;
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
	public float getQuadSize(float partialTicks) {
		float bob = Mth.sin(target.tickCount / 5.0f) * 0.2f + 0.2f;
		return 0.5f * (1.0f + bob);
	}

	@Override
	protected float getU0() {
		int col = 1 + target.tickCount % 6;
		return col * CELL;
	}

	@Override
	protected float getU1() {
		return getU0() + CELL;
	}

	@Override
	protected float getV0() {
		return row * CELL;
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

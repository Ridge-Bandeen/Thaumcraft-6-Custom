package thaumcraft.client.fx.particles;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater;

/**
 * Real modern equivalent of the true original's own {@code RenderCultistPortalGreater} - see {@link
 * FXCultistPortalLesser}'s own doc comment for the shared real billboard-quad technique (reused directly via
 * {@link FXCultistPortalLesser#renderQuad}). Real, checked-not-assumed differences from the Lesser variant:
 * no {@link EntityCultistPortalLesser#isActive() isActive} gate at all (this boss variant is always visible
 * from spawn - matches the true original's own real unconditional render), a real {@code 1.3} scale
 * multiplier (not Lesser's {@code 1.25}), and the real animation drives off plain {@link
 * net.minecraft.world.entity.Entity#tickCount} directly rather than a dedicated {@code activeCounter} field
 * (this boss has no activation gate to count ticks-since, so vanilla's own free tick counter already is the
 * right real driver).
 */
public class FXCultistPortalGreater extends Particle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/cultist_portal.png");

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
			return "thaumcraft:cultist_portal_greater";
		}
	};

	private final EntityCultistPortalGreater target;

	public FXCultistPortalGreater(ClientLevel level, EntityCultistPortalGreater target) {
		super(level, target.getX(), target.getY(), target.getZ());
		this.target = target;
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
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		float scaleY = 1.5f;
		float e = Math.min(50.0f, target.tickCount + partialTicks);
		if (target.hurtTime > 0) {
			double d = Math.sin(target.hurtTime * 72.0 * Math.PI / 180.0);
			scaleY -= (float) (d / 4.0);
			e += (int) (6.0 * d);
		}
		if (target.pulse > 0) {
			double d = Math.sin(target.pulse * 36.0 * Math.PI / 180.0);
			scaleY += (float) (d / 4.0);
			e += (int) (12.0 * d);
		}
		float scale = e / 50.0f * 1.3f;

		float maxHealth = target.getMaxHealth();
		float m = maxHealth > 0.0f ? (1.0f - target.getHealth() / maxHealth) / 3.0f : 0.0f;
		float bob = Mth.sin(target.tickCount / (5.0f - 12.0f * m)) * m + m;
		float bob2 = Mth.sin(target.tickCount / (6.0f - 15.0f * m)) * m + m;
		float alpha = 1.0f - bob;
		scaleY -= bob / 4.0f;
		scale -= bob2 / 3.0f;

		float heightOffset = target.getBbHeight() / 2.0f;
		float px = (float) Mth.lerp(partialTicks, xo, x);
		float py = (float) Mth.lerp(partialTicks, yo, y);
		float pz = (float) Mth.lerp(partialTicks, zo, z);
		FXCultistPortalLesser.renderQuad(buffer, camera, px, py, pz, heightOffset, scale, scaleY, alpha);
	}

	@Override
	public AABB getRenderBoundingBox(float partialTicks) {
		return new AABB(x - 2.0, y - 1.0, z - 2.0, x + 2.0, y + 3.0, z + 2.0);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

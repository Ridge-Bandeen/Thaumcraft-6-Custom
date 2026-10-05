package thaumcraft.client.fx.particles;

import javax.annotation.Nullable;

import org.joml.Vector3f;

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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import thaumcraft.Thaumcraft;

/**
 * Real modern equivalent of the true original's own real {@code FXBlockRunes} - a small glowing rune painted
 * flush against a block's own face, real FIXED orientation (one of 4 random 90-degree turns, picked once at
 * spawn) rather than a camera-facing billboard. Restored 2026-09-17. Real call sites confirmed via
 * `git grep`: {@link thaumcraft.common.blocks.basic.BlockPavingStoneBarrier}'s own real ambient warding-state
 * cue, {@link thaumcraft.common.items.tools.ItemThaumometer}'s own real scan effect, {@link
 * thaumcraft.common.tiles.crafting.TileInfusionMatrix}'s own real crafting cue, and {@link
 * thaumcraft.common.blocks.devices.BlockPedestal}'s own real offering cue (the {@code blockRunes2} variant -
 * same real particle class, a real fixed scale and zeroed X-offset instead of the default random jitter).
 *
 * <p>Real, faithful port of the true original's own real fixed-orientation transform: since this isn't a
 * camera billboard, this extends {@link Particle} directly and builds its own quad corners by hand (a real
 * local offset, then a real fixed 90-degree "face the block" turn, then the particle's own real random
 * 0/90/180/270-degree spawn-time rotation - matching the true original's own real GL matrix order exactly,
 * translated into successive {@link Vector3f} rotations composed in the same order) rather than reusing
 * {@link net.minecraft.client.particle.SingleQuadParticle}'s own real camera-facing code path, same real shape
 * {@code FXCultistPortalLesser}/{@code FXEldritchOrbSpikes} already established for a non-billboard quad.
 */
public class FXBlockRunes extends Particle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final int GRID = 64;
	private static final float CELL = 1.0f / GRID;
	private static final float ROW_V0 = 6 * CELL;

	public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
		@Nullable
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.setShader(GameRenderer::getParticleShader);
			RenderSystem.setShaderTexture(0, TEXTURE);
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE); // the original's layer 0: additive
			RenderSystem.depthMask(false);
			return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}

		@Override
		public String toString() {
			return "thaumcraft:block_runes";
		}
	};

	private double ofx;
	private double ofy;
	private final float rotationDeg;
	private final int runeCol;
	private float scale;
	private float runeGravity;

	public FXBlockRunes(ClientLevel level, double x, double y, double z, float r, float g, float b, int m) {
		super(level, x, y, z);
		if (r == 0.0f) {
			r = 1.0f;
		}
		this.rotationDeg = random.nextInt(4) * 90.0f;
		this.rCol = r;
		this.gCol = g;
		this.bCol = b;
		this.lifetime = 3 * m;
		this.runeCol = random.nextInt(16);
		this.ofx = random.nextFloat() * 0.2;
		this.ofy = -0.3 + random.nextFloat() * 0.6;
		this.scale = (float) (1.0 + random.nextGaussian() * 0.1);
		this.alpha = 0.0f;
		this.hasPhysics = false;
	}

	public FXBlockRunes setScale(float s) {
		this.scale = s;
		return this;
	}

	public FXBlockRunes setOffsetX(double f) {
		this.ofx = f;
		return this;
	}

	public FXBlockRunes setGravity(float value) {
		this.runeGravity = value;
		return this;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		float threshold = lifetime / 5.0f;
		if (age <= threshold) {
			alpha = age / threshold;
		} else {
			alpha = (lifetime - age) / (float) lifetime;
		}
		if (age++ >= lifetime) {
			remove();
		}
		yd -= 0.04 * runeGravity;
		x += xd;
		y += yd;
		z += zd;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
		Vec3 camPos = camera.getPosition();
		float px = (float) Mth.lerp(partialTicks, xo, x) - (float) camPos.x;
		float py = (float) Mth.lerp(partialTicks, yo, y) - (float) camPos.y;
		float pz = (float) Mth.lerp(partialTicks, zo, z) - (float) camPos.z;

		float half = 0.15f * scale;
		float u0 = runeCol * CELL;
		float u1 = u0 + CELL;
		float v0 = ROW_V0;
		float v1 = ROW_V0 + CELL;
		int light = LightTexture.FULL_BRIGHT;

		emitVertex(buffer, px, py, pz, -half, half, u0, v0, light);
		emitVertex(buffer, px, py, pz, half, half, u1, v0, light);
		emitVertex(buffer, px, py, pz, half, -half, u1, v1, light);
		emitVertex(buffer, px, py, pz, -half, -half, u0, v1, light);
	}

	private void emitVertex(VertexConsumer buffer, float baseX, float baseY, float baseZ, float lx, float ly, float u, float v, int light) {
		Vector3f corner = new Vector3f(lx, ly, 0.0f);
		corner.add((float) ofx, (float) ofy, -0.51f);
		corner.rotateZ((float) Math.toRadians(90.0));
		corner.rotateY((float) Math.toRadians(rotationDeg));
		buffer.addVertex(baseX + corner.x(), baseY + corner.y(), baseZ + corner.z())
				.setUv(u, v).setColor(rCol, gCol, bCol, alpha / 2.0f).setLight(light);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}

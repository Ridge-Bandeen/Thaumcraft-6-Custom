package thaumcraft.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import org.joml.Matrix4f;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.entities.construct.EntityArcaneBore;

/**
 * Renders {@link EntityArcaneBore} via the real, faithful {@link ModelArcaneBore} - see that class's own doc
 * comment. Real 0.5x scale, matching the original's own {@code RenderLiving} constructor call exactly.
 *
 * <p>While it digs (and is switched on with a usable pickaxe), the original's mining beam on top of the model: at the
 * muzzle (0.5 forward and 0.075 up from the eye, turned with the head) a camera-facing glow cycling through row 3 of
 * {@code auranodes.png}, then three tapered planes 60 degrees apart on {@code beam1.png}, 0.3 wide at the muzzle and
 * meeting 5 blocks out along the bore's facing, spinning with the day time and scrolling their texture. Both additive,
 * without writing depth, and the planes drawn from both sides.
 */
public class RenderArcaneBore extends MobRenderer<EntityArcaneBore, ModelArcaneBore> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/arcanebore.png");
	private static final ResourceLocation NODES = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/auranodes.png");
	private static final ResourceLocation BEAM = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/beam1.png");

	public RenderArcaneBore(EntityRendererProvider.Context context) {
		super(context, new ModelArcaneBore(), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityArcaneBore entity) {
		return TEXTURE;
	}

	@Override
	public void render(EntityArcaneBore bore, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		super.render(bore, entityYaw, partialTick, poseStack, bufferSource, packedLight);
		if (bore.clientDigging && bore.isActive() && bore.validInventory()) {
			renderBeam(bore, partialTick, poseStack);
		}
	}

	private void renderBeam(EntityArcaneBore bore, float pt, PoseStack poseStack) {
		Vec3 muzzle = new Vec3(0.5, 0.075, 0.0).zRot(bore.getXRot() / 180.0f * Mth.PI)
				.yRot(-((bore.getYHeadRot() + 90.0f) / 180.0f * Mth.PI));
		poseStack.pushPose();
		poseStack.translate(muzzle.x, bore.getEyeHeight() + muzzle.y, muzzle.z);

		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

		// the glow: frame (96 + age % 32) of the 32x32 node sheet, half-size 0.5
		int part = bore.tickCount % 32;
		float u0 = part / 32.0f;
		float u1 = u0 + 1.0f / 32.0f;
		float v0 = 3 / 32.0f;
		float v1 = v0 + 1.0f / 32.0f;
		poseStack.pushPose();
		poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
		Matrix4f m = poseStack.last().pose();
		RenderSystem.setShaderTexture(0, NODES);
		BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		glow.addVertex(m, -0.5f, -0.5f, 0).setUv(u1, v1).setColor(0.0f, 1.0f, 0.4f, 0.8f);
		glow.addVertex(m, -0.5f, 0.5f, 0).setUv(u1, v0).setColor(0.0f, 1.0f, 0.4f, 0.8f);
		glow.addVertex(m, 0.5f, 0.5f, 0).setUv(u0, v0).setColor(0.0f, 1.0f, 0.4f, 0.8f);
		glow.addVertex(m, 0.5f, -0.5f, 0).setUv(u0, v1).setColor(0.0f, 1.0f, 0.4f, 0.8f);
		BufferUploader.drawWithShader(glow.buildOrThrow());
		poseStack.popPose();

		// the beam: three planes along the facing, turned 60 degrees apart and spinning
		float ry = Mth.lerp(pt, bore.yRotO, bore.getYRot());
		float rp = Mth.lerp(pt, bore.xRotO, bore.getXRot());
		float rot = bore.level().getDayTime() % 72L * 5L + 5.0f * pt;
		float age = bore.tickCount + pt;
		float scroll = age * 0.2f - Mth.floor(age * 0.1f);
		poseStack.pushPose();
		poseStack.mulPose(Axis.XN.rotationDegrees(90.0f));
		poseStack.mulPose(Axis.ZN.rotationDegrees(180.0f + ry));
		poseStack.mulPose(Axis.XN.rotationDegrees(rp));
		poseStack.mulPose(Axis.YP.rotationDegrees(rot));
		RenderSystem.setShaderTexture(0, BEAM);
		float length = 5.0f;
		for (int t = 0; t < 3; t++) {
			poseStack.mulPose(Axis.YP.rotationDegrees(60.0f));
			m = poseStack.last().pose();
			float vStart = -1.0f + scroll + t / 3.0f;
			float vEnd = length + vStart;
			BufferBuilder beam = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			beam.addVertex(m, 0.0f, length, 0.0f).setUv(1.0f, vEnd).setColor(0.0f, 1.0f, 0.4f, 0.4f);
			beam.addVertex(m, -0.15f, 0.0f, 0.0f).setUv(1.0f, vStart).setColor(0.0f, 1.0f, 0.4f, 0.4f);
			beam.addVertex(m, 0.15f, 0.0f, 0.0f).setUv(0.0f, vStart).setColor(0.0f, 1.0f, 0.4f, 0.4f);
			beam.addVertex(m, 0.0f, length, 0.0f).setUv(0.0f, vEnd).setColor(0.0f, 1.0f, 0.4f, 0.4f);
			BufferUploader.drawWithShader(beam.buildOrThrow());
		}
		poseStack.popPose();

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		poseStack.popPose();
	}

	@Override
	public boolean shouldRender(EntityArcaneBore bore, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ) {
		// the beam reaches 5 blocks past the bore's own box
		return super.shouldRender(bore, frustum, camX, camY, camZ)
				|| bore.clientDigging && frustum.isVisible(bore.getBoundingBox().inflate(5.0));
	}
}

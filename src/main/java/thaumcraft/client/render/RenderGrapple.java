package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.projectile.EntityGrapple;

/**
 * Renders {@link EntityGrapple} - real, faithful geometry translation of the original's own {@code
 * ModelGrappler} (a plain 4-box {@code ModelBase}/{@code ModelRenderer} model: a cube core plus three
 * mutually-perpendicular prongs) into the modern {@link LayerDefinition}/{@link CubeListBuilder} builder
 * system, same real coordinates/rotations, no dimensions changed - same real technique {@code
 * TileBannerRenderer} already established for a hand-cuboid model. Real yaw/pitch orientation, matching the
 * original's own manual interpolation exactly.
 *
 * <p>As the original: behind the hook a soft additive glow from the particle sheet (a six-frame cycle, bobbing in size,
 * faced to the camera), and the rope back to the thrower - GLE's {@code glePolyCone} ({@link thaumcraft.client.fx.GleTube}),
 * four sides, radius 0.025, on {@code rope.png}, sagging in three sine waves by the hook's slack ({@code ampl}, which
 * grows in flight and draws in once it pulls), tightest at the thrower. In a first-person view the rope starts beside
 * the firing hand, as the original.
 */
public class RenderGrapple extends EntityRenderer<EntityGrapple> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/grappler.png");

	private final ModelPart core;
	private final ModelPart prong1;
	private final ModelPart prong2;
	private final ModelPart prong3;

	public RenderGrapple(EntityRendererProvider.Context context) {
		super(context);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3), PartPose.ZERO);
		root.addOrReplaceChild("prong1", CubeListBuilder.create().texOffs(0, 10).addBox(-0.5f, -0.5f, -2.5f, 1, 1, 5), PartPose.ZERO);
		root.addOrReplaceChild("prong2", CubeListBuilder.create().texOffs(0, 10).addBox(-0.5f, -0.5f, -2.5f, 1, 1, 5), PartPose.rotation(0.0f, Mth.HALF_PI, 0.0f));
		root.addOrReplaceChild("prong3", CubeListBuilder.create().texOffs(0, 10).addBox(-0.5f, -0.5f, -2.5f, 1, 1, 5), PartPose.rotation(Mth.HALF_PI, Mth.HALF_PI, 0.0f));
		ModelPart baked = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		core = baked.getChild("core");
		prong1 = baked.getChild("prong1");
		prong2 = baked.getChild("prong2");
		prong3 = baked.getChild("prong3");
	}

	private static final ResourceLocation PARTICLES = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/particles.png");
	private static final ResourceLocation ROPE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/rope.png");

	@Override
	public void render(EntityGrapple entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(entityYaw - 90.0f));
		poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
		var consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
		core.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		prong1.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		prong2.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		prong3.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();

		renderGlow(entity, poseStack);
		renderRope(entity, partialTick, poseStack, bufferSource, packedLight);
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
	}

	/** The original's glow: frame (1 + age % 6) of row 7 of the particle sheet, additive, culling off. */
	private void renderGlow(EntityGrapple entity, PoseStack poseStack) {
		float f2 = (1 + entity.tickCount % 6) / 32.0f;
		float f3 = f2 + 0.03125f;
		float f4 = 0.21875f;
		float f5 = f4 + 0.03125f;
		float bob = Mth.sin(entity.tickCount / 5.0f) * 0.2f + 0.2f;
		poseStack.pushPose();
		poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
		poseStack.scale(1.0f + bob, 1.0f + bob, 1.0f + bob);
		var pose = poseStack.last().pose();
		com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
		com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, PARTICLES);
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
				com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
		com.mojang.blaze3d.systems.RenderSystem.disableCull();
		com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
		var builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
				com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
		builder.addVertex(pose, -0.5f, -0.5f, 0).setUv(f2, f5).setColor(1.0f, 1.0f, 1.0f, 0.21f);
		builder.addVertex(pose, 0.5f, -0.5f, 0).setUv(f3, f5).setColor(1.0f, 1.0f, 1.0f, 0.21f);
		builder.addVertex(pose, 0.5f, 0.5f, 0).setUv(f3, f4).setColor(1.0f, 1.0f, 1.0f, 0.21f);
		builder.addVertex(pose, -0.5f, 0.5f, 0).setUv(f2, f4).setColor(1.0f, 1.0f, 1.0f, 0.21f);
		com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.buildOrThrow());
		com.mojang.blaze3d.systems.RenderSystem.enableCull();
		com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
		com.mojang.blaze3d.systems.RenderSystem.disableBlend();
		poseStack.popPose();
	}

	/** The original's calcPoints and rope draw. */
	private void renderRope(EntityGrapple entity, float pt, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		if (!(entity.getOwner() instanceof net.minecraft.world.entity.LivingEntity thrower)) return;
		double tx = Mth.lerp(pt, thrower.xo, thrower.getX());
		double ty = Mth.lerp(pt, thrower.yo, thrower.getY());
		double tz = Mth.lerp(pt, thrower.zo, thrower.getZ());
		if (net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			double yy = Mth.lerp(pt, thrower.yRotO, thrower.getYRot());
			int side = entity.hand == net.minecraft.world.InteractionHand.MAIN_HAND ? 1 : -1;
			double px = -Mth.cos((float) ((yy - 0.5) / 180.0 * 3.1415929794311523)) * 0.1f * side;
			double pz = -Mth.sin((float) ((yy - 0.5) / 180.0 * 3.1415929794311523)) * 0.1f * side;
			var vl = thrower.getLookAngle();
			tx += px + vl.x / 5.0;
			ty += thrower.getEyeHeight() / 2.6 + vl.y / 5.0;
			tz += pz + vl.z / 5.0;
		}
		double gx = Mth.lerp(pt, entity.xo, entity.getX());
		double gy = Mth.lerp(pt, entity.yo, entity.getY());
		double gz = Mth.lerp(pt, entity.zo, entity.getZ());
		java.util.List<net.minecraft.world.phys.Vec3> points = new java.util.ArrayList<>();
		net.minecraft.world.phys.Vec3 ve = new net.minecraft.world.phys.Vec3(tx - gx, ty - gy + thrower.getBbHeight() / 2.0f, tz - gz);
		float length = (float) (ve.length() * 5.0);
		int steps = (int) length;
		points.add(net.minecraft.world.phys.Vec3.ZERO);
		for (int a = 1; a < steps - 1; ++a) {
			float dist = a * (length / steps);
			float ss = 1.0f - a / (steps * 1.25f);
			double dx = (tx - gx) / steps * a + Mth.sin(dist / 10.0f) * entity.ampl * ss;
			double dy = (ty - gy + thrower.getBbHeight() / 2.0f) / steps * a + Mth.sin(dist / 8.0f) * entity.ampl * ss;
			double dz = (tz - gz) / steps * a + Mth.sin(dist / 6.0f) * entity.ampl * ss;
			points.add(new net.minecraft.world.phys.Vec3(dx, dy, dz));
		}
		points.add(ve);
		points.add(ve);
		int n = points.size();
		if (n <= 2) return;
		float[][] colours = new float[n][];
		double[] radii = new double[n];
		for (int a = 0; a < n; a++) {
			colours[a] = new float[] { 1, 1, 1, 1 };
			radii[a] = 0.025;
		}
		var consumer = bufferSource.getBuffer(RenderType.entityTranslucentCull(ROPE));
		PoseStack.Pose pose = poseStack.last();
		thaumcraft.client.fx.GleTube.polyCone((x, y, z, u, v, r, g, b, al) -> consumer.addVertex(pose, (float) x, (float) y, (float) z)
				.setColor(r, g, b, al).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0f, 1.0f, 0.0f),
				points.toArray(new net.minecraft.world.phys.Vec3[0]), colours, radii, 4, 2.0f, length - n, true, false);
	}

	@Override
	public boolean shouldRender(EntityGrapple entity, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ) {
		return true; // the rope reaches back to the thrower, well outside the hook's own box
	}

	@Override
	public ResourceLocation getTextureLocation(EntityGrapple entity) {
		return TEXTURE;
	}
}

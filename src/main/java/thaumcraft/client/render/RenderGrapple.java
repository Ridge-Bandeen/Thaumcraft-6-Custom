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
 * Real, documented cut: the original's own tether-rope visual (a wavy, textured, real-time polygon tube
 * connecting the hook back to its thrower) is dropped - it was built on a real third-party GL-tube library
 * (`com.sasmaster.glelwjgl`) this port doesn't have and doesn't need to rebuild for a cosmetic connecting
 * line; only the actual hook geometry is real here. {@link EntityGrapple}'s own real server-side pull/anchor
 * behavior is unaffected either way - the rope was only ever a visual, never a physics attachment.
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
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityGrapple entity) {
		return TEXTURE;
	}
}

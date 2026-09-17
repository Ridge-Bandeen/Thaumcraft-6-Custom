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
import thaumcraft.common.entities.projectile.EntityFocusMine;

/**
 * Renders {@link EntityFocusMine} - a real, previously-invisible gap (this entity shared the no-visible-model
 * {@code RenderFocusProjectile} placeholder before, but the true original's own real {@code RenderFocusMine}
 * draws a real caltrop-shaped model with a pulsing red-tinted beam texture, not just a particle trail; found
 * during a real cross-check of every entity type hiding behind that placeholder - see the Custom Mob
 * Models/Renderers TODO entry).
 *
 * <p>Same real 4-box caltrop geometry as {@link RenderGrapple}'s own already-ported {@code ModelGrappler}
 * translation (a cube core plus three mutually-perpendicular prongs) - the true original's own real
 * {@code ModelGrappler} class is shared between {@code EntityGrapple} and {@code EntityFocusMine} in the same
 * way, just with a different bound texture and, here, an animated colour tint. Real pulsing white-to-red tint
 * restored too, matching the true original's own real {@code (counter+partialTicks)%8/8} formula - substitutes
 * the entity's own real (and here, for free) {@link net.minecraft.world.entity.Entity#tickCount} for the
 * original's now-dropped dedicated {@code counter} field, since both simply count real ticks since spawn.
 */
public class RenderFocusMine extends EntityRenderer<EntityFocusMine> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/mine.png");

	private final ModelPart core;
	private final ModelPart prong1;
	private final ModelPart prong2;
	private final ModelPart prong3;

	public RenderFocusMine(EntityRendererProvider.Context context) {
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
	public void render(EntityFocusMine entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(entityYaw - 90.0f));
		poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));

		float f = (entity.tickCount + partialTick) % 8.0f / 8.0f;
		int g = Mth.clamp((int) ((1.0f - f) * 255.0f), 0, 255);
		int color = (255 << 24) | (255 << 16) | (g << 8) | g;

		var consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
		core.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, color);
		prong1.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, color);
		prong2.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, color);
		prong3.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, color);
		poseStack.popPose();
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityFocusMine entity) {
		return TEXTURE;
	}
}

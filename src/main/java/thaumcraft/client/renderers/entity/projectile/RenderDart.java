package thaumcraft.client.renderers.entity.projectile;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.projectile.EntityGolemDart;

/**
 * The original's {@code RenderDart}: vanilla's arrow, same texture and shape, drawn at 0.033 per pixel instead of the
 * arrow's 0.05625 - a golem's dart is a little over half an arrow's size.
 */
public class RenderDart extends ArrowRenderer<EntityGolemDart> {

	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");
	private static final float SCALE = 0.033f / 0.05625f;

	public RenderDart(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(EntityGolemDart dart, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		poseStack.scale(SCALE, SCALE, SCALE);
		super.render(dart, entityYaw, partialTick, poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(EntityGolemDart dart) {
		return TEXTURE;
	}
}

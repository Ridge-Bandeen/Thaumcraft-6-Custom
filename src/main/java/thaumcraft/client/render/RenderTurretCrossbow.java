package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.construct.EntityTurretCrossbow;

/**
 * Renders {@link EntityTurretCrossbow} via the real, faithful {@link ModelCrossbow} - see that class's own
 * doc comment. Real 0.5x scale, matching the original's own {@code RenderLiving} constructor call exactly.
 */
public class RenderTurretCrossbow extends MobRenderer<EntityTurretCrossbow, ModelCrossbow> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/crossbow.png");

	public RenderTurretCrossbow(EntityRendererProvider.Context context) {
		super(context, new ModelCrossbow(), 0.5f);
	}

	@Override
	public void render(EntityTurretCrossbow entity, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
			net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
		// as the original: the body never turns, the head alone aims
		entity.yBodyRot = 0.0f;
		entity.yBodyRotO = 0.0f;
		super.render(entity, 0.0f, partialTick, poseStack, bufferSource, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityTurretCrossbow entity) {
		return TEXTURE;
	}
}

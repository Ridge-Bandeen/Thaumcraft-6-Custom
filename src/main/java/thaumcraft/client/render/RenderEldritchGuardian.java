package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;

/**
 * Renders {@link EntityEldritchGuardian} via the temporary {@link ModelEldritchPlaceholder} - see that
 * class's own doc comment for why this isn't the real mesh yet. Sized to the entity's own real 0.8x2.25
 * hitbox.
 *
 * <p>Also draws the entity's own real "sonic scream" dome effect on top - see {@link EldritchSonicFx}'s own
 * doc comment for the real technique.
 */
public class RenderEldritchGuardian extends MobRenderer<EntityEldritchGuardian, ModelEldritchPlaceholder<EntityEldritchGuardian>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/eldritch_guardian.png");

	public RenderEldritchGuardian(EntityRendererProvider.Context context) {
		super(context, new ModelEldritchPlaceholder<>(0.8f, 2.25f), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityEldritchGuardian entity) {
		return TEXTURE;
	}

	@Override
	public void render(EntityEldritchGuardian entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
		EldritchSonicFx.render(entity, entity.getSonicStartTick(), entity.getSonicDuration(), entity.getSonicYaw(), entity.getSonicPitch(),
				partialTick, poseStack, bufferSource, packedLight);
	}
}

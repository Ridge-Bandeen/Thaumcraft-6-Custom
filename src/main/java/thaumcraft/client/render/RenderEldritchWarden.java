package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.models.entity.ModelEldritchGuardian;
import thaumcraft.common.entities.monster.boss.EntityEldritchWarden;

/**
 * Renders {@link EntityEldritchWarden} via the real {@link ModelEldritchGuardian} mesh (restored 2026-09-24,
 * replacing the temporary {@code ModelEldritchPlaceholder}, since deleted) - shared with {@code EntityEldritchGuardian},
 * matching the true original's own real class sharing (the Warden had no model of its own). Sized to the
 * entity's own real 0.8x2.25 hitbox, its own real texture keeps it visually distinct from the Guardian, and
 * its own real Warden-only glowing hood eye (see {@link ModelEldritchGuardian}'s own doc comment).
 *
 * <p>Also draws the entity's own real "sonic scream" dome effect on top - see {@link EldritchSonicFx}'s own
 * doc comment for the real technique.
 */
public class RenderEldritchWarden extends MobRenderer<EntityEldritchWarden, ModelEldritchGuardian<EntityEldritchWarden>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/eldritch_warden.png");

	public RenderEldritchWarden(EntityRendererProvider.Context context) {
		super(context, new ModelEldritchGuardian<>(), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityEldritchWarden entity) {
		return TEXTURE;
	}

	@Override
	public void render(EntityEldritchWarden entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		// as the original: risen out of the ground over the spawn timer
		poseStack.pushPose();
		poseStack.translate(0.0, -entity.getBbHeight() * (entity.getSpawnTimer() / 150.0f), 0.0);
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
		poseStack.popPose();
		EldritchSonicFx.render(entity, entity.getSonicStartTick(), entity.getSonicDuration(), entity.getSonicYaw(), entity.getSonicPitch(),
				partialTick, poseStack, bufferSource, packedLight);
	}
}

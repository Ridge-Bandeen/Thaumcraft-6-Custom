package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.models.entity.ModelEldritchGuardian;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;

/**
 * Renders {@link EntityEldritchGuardian} via the real {@link ModelEldritchGuardian} mesh (restored
 * 2026-09-24, replacing the temporary {@code ModelEldritchPlaceholder}, since deleted). Sized to the entity's own real
 * 0.8x2.25 hitbox.
 *
 * <p>Also draws the entity's own real "sonic scream" dome effect on top - see {@link EldritchSonicFx}'s own
 * doc comment for the real technique.
 */
public class RenderEldritchGuardian extends MobRenderer<EntityEldritchGuardian, ModelEldritchGuardian<EntityEldritchGuardian>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/eldritch_guardian.png");

	public RenderEldritchGuardian(EntityRendererProvider.Context context) {
		super(context, new ModelEldritchGuardian<>(), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityEldritchGuardian entity) {
		return TEXTURE;
	}

	@Override
	public void render(EntityEldritchGuardian entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		getModel().alpha = fade(entity);
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
		EldritchSonicFx.render(entity, entity.getSonicStartTick(), entity.getSonicDuration(), entity.getSonicYaw(), entity.getSonicPitch(),
				partialTick, poseStack, bufferSource, packedLight);
	}

	/** The original's ghostly Guardian: 0.6 alpha within 16 blocks of the viewer, fading to nothing by 32 (24 on hard),
	 * except in the Outer Lands, where it is solid. */
	private static float fade(EntityEldritchGuardian entity) {
		var viewer = net.minecraft.client.Minecraft.getInstance().getCameraEntity();
		if (viewer == null) return 0.6f;
		try {
			if (entity.level().dimension().location().toString().equals(thaumcraft.common.config.ThaumcraftConfig.DIMENSION_OUTER_ID.get())) {
				return 1.0f;
			}
		} catch (IllegalStateException e) {
			// config not loaded yet
		}
		float far = entity.level().getDifficulty() == net.minecraft.world.Difficulty.HARD ? 576.0f : 1024.0f;
		float near = 256.0f;
		double d = entity.distanceToSqr(viewer);
		if (d < near) return 0.6f;
		return (float) (1.0 - Math.min(far - near, d - near) / (far - near)) * 0.6f;
	}
}

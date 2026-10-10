package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Minecart;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.construct.EntityTurretCrossbow;

/**
 * Renders the advanced turret from {@code crossbow_advanced.obj}, read by {@link ObjModels} as the original's own OBJ
 * loader did, and drawn group by group as the original's {@code renderTurret}: {@code legs} (squashed to 0.66 / 0.75 /
 * 0.66 while riding a minecart), then the head groups ({@code mech}, {@code box}, {@code shield}, {@code brain}) turned
 * by the head yaw and pitch, the {@code loader} sliding back and forth through a reload, and the two bows ({@code bow1},
 * {@code bow2}) flexing apart through each shot (vanilla's attack swing, which runs the original's 6 ticks). While
 * hurt, the head is tinted red (1, 0.5, 0.5) and jiggles. A 0.5 shadow as the original's {@code RenderLiving}.
 */
public class RenderTurretCrossbowAdvanced extends EntityRenderer<EntityTurretCrossbow> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/entity/crossbow_advanced.png");
	private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "models/obj/crossbow_advanced.obj");

	public RenderTurretCrossbowAdvanced(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.5f;
	}

	@Override
	public void render(EntityTurretCrossbow entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		ObjModels.Model model = ObjModels.get(MODEL);
		VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
		int overlay = OverlayTexture.NO_OVERLAY;
		poseStack.pushPose();
		poseStack.translate(0.0, 0.75, 0.0);

		poseStack.pushPose();
		if (entity.isPassenger() && entity.getVehicle() instanceof Minecart) {
			poseStack.scale(0.66f, 0.75f, 0.66f);
		}
		model.renderGroup("legs", poseStack.last(), consumer, packedLight, overlay, 1.0f, 1.0f, 1.0f, 1.0f);
		poseStack.popPose();

		poseStack.pushPose();
		float g = 1.0f;
		float b = 1.0f;
		if (entity.hurtTime > 0) {
			g = 0.5f;
			b = 0.5f;
			float jiggle = entity.hurtTime / 500.0f;
			poseStack.translate(entity.getRandom().nextGaussian() * jiggle, entity.getRandom().nextGaussian() * jiggle,
					entity.getRandom().nextGaussian() * jiggle);
		}
		poseStack.mulPose(Axis.YN.rotationDegrees(Mth.lerp(partialTick, entity.yHeadRotO, entity.yHeadRot)));
		poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
		for (String group : new String[] { "mech", "box", "shield", "brain" }) {
			model.renderGroup(group, poseStack.last(), consumer, packedLight, overlay, 1.0f, g, b, 1.0f);
		}
		poseStack.pushPose();
		poseStack.translate(0.0, 0.0, Mth.sin(Mth.sqrt(entity.getLoadProgress(partialTick)) * Mth.PI * 2.0f) / 12.0f);
		model.renderGroup("loader", poseStack.last(), consumer, packedLight, overlay, 1.0f, g, b, 1.0f);
		poseStack.popPose();
		float swing = Mth.sin(Mth.sqrt(entity.getAttackAnim(partialTick)) * Mth.PI * 2.0f) * 20.0f;
		poseStack.translate(0.0, 0.0, 0.375);
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(swing));
		model.renderGroup("bow1", poseStack.last(), consumer, packedLight, overlay, 1.0f, g, b, 1.0f);
		poseStack.popPose();
		poseStack.pushPose();
		poseStack.mulPose(Axis.YN.rotationDegrees(swing));
		model.renderGroup("bow2", poseStack.last(), consumer, packedLight, overlay, 1.0f, g, b, 1.0f);
		poseStack.popPose();
		poseStack.popPose();

		poseStack.popPose();
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
	}

	@Override
	protected boolean shouldShowName(EntityTurretCrossbow entity) {
		return false; // the original's renderTurret replaced RenderLiving's whole doRender, name tag included
	}

	@Override
	public ResourceLocation getTextureLocation(EntityTurretCrossbow entity) {
		return TEXTURE;
	}
}

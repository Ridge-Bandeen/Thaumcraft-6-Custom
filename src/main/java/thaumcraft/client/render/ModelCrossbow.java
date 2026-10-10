package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.construct.EntityTurretCrossbow;

/**
 * Real, faithful geometry translation of the original's own {@code ModelCrossbow} - a tripod-mounted
 * ballista built from a rotating "crossbow" head (ammo box, barrel, load-bar frame, and two 3-segment bow
 * arms) sat atop 4 splayed legs and a small tripod hub, same real box coordinates/texture offsets/rotation
 * points ported here via the modern {@link LayerDefinition}/{@link CubeListBuilder} builder system. The
 * original's own children-of-"crossbow" all had a zero rotation point (they share the crossbow head's own
 * already-translated origin, only their own fixed rotations fan them out into the bow-arm/frame shape) -
 * reproduced here the same way via {@link PartPose#ZERO} children of the "crossbow" part.
 *
 * Real {@link #setupAnim} head tracking (the whole crossbow head pivots to face its target) - the original's
 * own override names its 4th/5th {@code setRotationAngles} params {@code headpitch}/{@code headyaw}, but
 * {@code ModelBase}'s real parameter order there is actually {@code netHeadYaw}/{@code headPitch} (just
 * confusingly named locally) - {@code rotateAngleY} really is fed the real net head yaw and {@code
 * rotateAngleX} the real head pitch, the ordinary yaw-around-Y/pitch-around-X convention, not an actual
 * swap. Reproduced here with the real (correctly-named) modern params.
 *
 * As the original: the renderer holds the body yaw at 0 ({@link RenderTurretCrossbow}), so the tripod never turns
 * and the head alone aims. The bow arms flex through each shot ({@link #attackTime}, vanilla's attack swing, which
 * runs the original's 6 ticks) and the load bar swings through a reload ({@link EntityTurretCrossbow#getLoadProgress});
 * at rest they sit at the original's -0.2 / -0.5, set every frame, not the model's built-in angles. Riding a minecart,
 * the legs sit half a block higher and fold in (0.1 instead of 0.5).
 */
public class ModelCrossbow extends EntityModel<EntityTurretCrossbow> {

	private final ModelPart crossbow;
	private final ModelPart tripod;
	private final ModelPart leg1;
	private final ModelPart leg2;
	private final ModelPart leg3;
	private final ModelPart leg4;
	private final ModelPart[] armsLeft;
	private final ModelPart[] armsRight;
	private final ModelPart[] loadBar;
	private float loadProgress;

	public ModelCrossbow() {
		super(RenderType::entityCutout);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition crossbowDef = root.addOrReplaceChild("crossbow",
				CubeListBuilder.create().texOffs(28, 14).addBox(-2.0f, 0.0f, -7.0f, 4, 2, 14),
				PartPose.offset(0.0f, 10.0f, 0.0f));
		crossbowDef.addOrReplaceChild("ammobox",
				CubeListBuilder.create().texOffs(38, 0).addBox(-2.0f, -5.0f, -6.0f, 4, 5, 9), PartPose.ZERO);
		crossbowDef.addOrReplaceChild("barrel",
				CubeListBuilder.create().texOffs(20, 28).addBox(-1.0f, -1.0f, -8.0f, 2, 2, 2), PartPose.ZERO);
		crossbowDef.addOrReplaceChild("basebarcross",
				CubeListBuilder.create().texOffs(0, 13).addBox(-2.0f, 0.5f, 10.0f, 4, 1, 1), PartPose.ZERO);
		crossbowDef.addOrReplaceChild("basebarr",
				CubeListBuilder.create().texOffs(40, 23).addBox(-1.0f, 0.0f, 7.0f, 1, 2, 5), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, -0.1396263f, 0.0f));
		crossbowDef.addOrReplaceChild("basebarl",
				CubeListBuilder.create().texOffs(40, 23).addBox(0.0f, 0.0f, 7.0f, 1, 2, 5), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.1396263f, 0.0f));
		crossbowDef.addOrReplaceChild("loadbarcross",
				CubeListBuilder.create().texOffs(0, 13).addBox(-2.0f, -8.5f, -0.5f, 4, 1, 1), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.5585054f, 0.0f, 0.0f));
		crossbowDef.addOrReplaceChild("loadbarl",
				CubeListBuilder.create().texOffs(0, 15).addBox(2.0f, -9.0f, -1.0f, 1, 11, 2), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.5585054f, 0.0f, 0.0f));
		crossbowDef.addOrReplaceChild("loadbarr",
				CubeListBuilder.create().texOffs(0, 15).addBox(-3.0f, -9.0f, -1.0f, 1, 11, 2), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.5585054f, 0.0f, 0.0f));
		crossbowDef.addOrReplaceChild("crossl1",
				CubeListBuilder.create().texOffs(0, 0).addBox(0.0f, 0.0f, -6.0f, 5, 2, 1), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, -0.2443461f, 0.0f));
		crossbowDef.addOrReplaceChild("crossl2",
				CubeListBuilder.create().texOffs(0, 0).addBox(4.0f, 0.0f, -5.0f, 3, 2, 1), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, -0.2443461f, 0.0f));
		crossbowDef.addOrReplaceChild("crossl3",
				CubeListBuilder.create().texOffs(0, 0).addBox(6.0f, 0.0f, -4.0f, 2, 2, 1), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, -0.2443461f, 0.0f));
		crossbowDef.addOrReplaceChild("crossr1",
				CubeListBuilder.create().texOffs(0, 0).addBox(-5.0f, 0.0f, -6.0f, 5, 2, 1), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.2443461f, 0.0f));
		crossbowDef.addOrReplaceChild("crossr2",
				CubeListBuilder.create().texOffs(0, 0).addBox(-7.0f, 0.0f, -5.0f, 3, 2, 1), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.2443461f, 0.0f));
		crossbowDef.addOrReplaceChild("crossr3",
				CubeListBuilder.create().texOffs(0, 0).addBox(-8.0f, 0.0f, -4.0f, 2, 2, 1), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.2443461f, 0.0f));

		root.addOrReplaceChild("tripod",
				CubeListBuilder.create().texOffs(13, 0).addBox(-1.5f, 0.0f, -1.5f, 3, 2, 3),
				PartPose.offset(0.0f, 12.0f, 0.0f));
		root.addOrReplaceChild("leg1",
				CubeListBuilder.create().texOffs(20, 10).addBox(-1.0f, 1.0f, -1.0f, 2, 13, 2),
				PartPose.offsetAndRotation(0.0f, 12.0f, 0.0f, 0.5235988f, 0.0f, 0.0f));
		root.addOrReplaceChild("leg2",
				CubeListBuilder.create().texOffs(20, 10).addBox(-1.0f, 1.0f, -1.0f, 2, 13, 2),
				PartPose.offsetAndRotation(0.0f, 12.0f, 0.0f, 0.5235988f, 1.570796f, 0.0f));
		root.addOrReplaceChild("leg3",
				CubeListBuilder.create().texOffs(20, 10).addBox(-1.0f, 1.0f, -1.0f, 2, 13, 2),
				PartPose.offsetAndRotation(0.0f, 12.0f, 0.0f, 0.5235988f, 3.141593f, 0.0f));
		root.addOrReplaceChild("leg4",
				CubeListBuilder.create().texOffs(20, 10).addBox(-1.0f, 1.0f, -1.0f, 2, 13, 2),
				PartPose.offsetAndRotation(0.0f, 12.0f, 0.0f, 0.5235988f, 4.712389f, 0.0f));

		ModelPart baked = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		crossbow = baked.getChild("crossbow");
		tripod = baked.getChild("tripod");
		leg1 = baked.getChild("leg1");
		leg2 = baked.getChild("leg2");
		leg3 = baked.getChild("leg3");
		leg4 = baked.getChild("leg4");
		armsLeft = new ModelPart[] { crossbow.getChild("crossl1"), crossbow.getChild("crossl2"), crossbow.getChild("crossl3") };
		armsRight = new ModelPart[] { crossbow.getChild("crossr1"), crossbow.getChild("crossr2"), crossbow.getChild("crossr3") };
		loadBar = new ModelPart[] { crossbow.getChild("loadbarcross"), crossbow.getChild("loadbarl"), crossbow.getChild("loadbarr") };
	}

	@Override
	public void prepareMobModel(EntityTurretCrossbow entity, float limbSwing, float limbSwingAmount, float partialTick) {
		loadProgress = entity.getLoadProgress(partialTick);
	}

	@Override
	public void setupAnim(EntityTurretCrossbow entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		crossbow.yRot = netHeadYaw / Mth.RAD_TO_DEG;
		crossbow.xRot = headPitch / Mth.RAD_TO_DEG;
		float flex = Mth.sin(Mth.sqrt(attackTime) * Mth.PI * 2.0f) * 0.2f;
		for (ModelPart arm : armsLeft) arm.yRot = -0.2f + flex;
		for (ModelPart arm : armsRight) arm.yRot = 0.2f - flex;
		float load = -0.5f + Mth.sin(Mth.sqrt(loadProgress) * Mth.PI * 2.0f) * 0.5f;
		for (ModelPart bar : loadBar) bar.xRot = load;
		boolean onCart = entity.isPassenger() && entity.getVehicle() instanceof net.minecraft.world.entity.vehicle.AbstractMinecart;
		for (ModelPart leg : new ModelPart[] { leg1, leg2, leg3, leg4 }) {
			// the original's offsetY of -0.5 (blocks) on top of the 12-pixel pivot
			leg.y = onCart ? 4.0f : 12.0f;
			leg.xRot = onCart ? 0.1f : 0.5f;
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		crossbow.render(poseStack, buffer, packedLight, packedOverlay, color);
		tripod.render(poseStack, buffer, packedLight, packedOverlay, color);
		leg1.render(poseStack, buffer, packedLight, packedOverlay, color);
		leg2.render(poseStack, buffer, packedLight, packedOverlay, color);
		leg3.render(poseStack, buffer, packedLight, packedOverlay, color);
		leg4.render(poseStack, buffer, packedLight, packedOverlay, color);
	}
}

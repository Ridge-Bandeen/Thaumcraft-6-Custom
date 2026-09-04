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
import thaumcraft.common.entities.monster.EntityEldritchCrab;

/**
 * Real, faithful geometry+animation translation of the original's own {@code ModelEldritchCrab} - 21
 * independent (unparented) cuboids, same real box coordinates/texture offsets/pivots, built here via the
 * modern {@link LayerDefinition}/{@link CubeListBuilder} builder system. Every part is set explicitly every
 * frame in {@link #setupAnim} (not just the ones the original's own {@code setRotationAngles} touched) -
 * this model doesn't use {@link net.minecraft.client.model.HierarchicalModel}'s automatic per-frame pose
 * reset, so parts the original only ever rotated once at construction time (the arms/torso/tail's own base
 * tilt) get that same fixed rotation re-asserted here instead, to stay correct regardless of state left over
 * from a previous frame.
 *
 * Real walk-cycle leg swing (four leg pairs, front/back mirrored), real tail sway, and real independent claw
 * twitching (driven directly by {@code entity.tickCount}, matching the original's own direct {@code
 * ticksExisted} use rather than the interpolated {@code ageInTicks} parameter). Real helm-vs-bare tail choice
 * via {@link EntityEldritchCrab#hasHelm()}, cached from {@link #setupAnim} for {@link #renderToBuffer} to
 * pick between.
 */
public class ModelEldritchCrab extends EntityModel<EntityEldritchCrab> {

	private final ModelPart tailHelm;
	private final ModelPart tailBare;
	private final ModelPart rfLeg1;
	private final ModelPart rClaw1;
	private final ModelPart head1;
	private final ModelPart rClaw0;
	private final ModelPart rClaw2;
	private final ModelPart lClaw2;
	private final ModelPart lClaw1;
	private final ModelPart rArm;
	private final ModelPart torso;
	private final ModelPart rrLeg1;
	private final ModelPart head0;
	private final ModelPart lrLeg1;
	private final ModelPart lfLeg1;
	private final ModelPart rrLeg0;
	private final ModelPart rfLeg0;
	private final ModelPart lfLeg0;
	private final ModelPart lrLeg0;
	private final ModelPart lClaw0;
	private final ModelPart lArm;

	private boolean hasHelm;

	public ModelEldritchCrab() {
		super(RenderType::entityCutout);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("tail_helm", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -4.5f, -0.4f, 9, 9, 9),
				PartPose.offsetAndRotation(0.0f, 18.0f, 0.0f, 0.1047198f, 0.0f, 0.0f));
		root.addOrReplaceChild("tail_bare", CubeListBuilder.create().texOffs(64, 0).addBox(-4.0f, -4.0f, -0.4f, 8, 8, 8),
				PartPose.offsetAndRotation(0.0f, 18.0f, 0.0f, 0.1047198f, 0.0f, 0.0f));
		root.addOrReplaceChild("r_claw1", CubeListBuilder.create().texOffs(0, 47).addBox(-2.0f, -1.0f, -5.066667f, 4, 3, 5),
				PartPose.offset(-6.0f, 15.5f, -10.0f));
		root.addOrReplaceChild("head1", CubeListBuilder.create().texOffs(0, 38).addBox(-2.0f, -1.5f, -9.066667f, 4, 4, 1),
				PartPose.offset(0.0f, 18.0f, 0.0f));
		root.addOrReplaceChild("r_claw0", CubeListBuilder.create().texOffs(0, 55).addBox(-2.0f, -2.5f, -3.066667f, 4, 5, 3),
				PartPose.offset(-6.0f, 17.0f, -7.0f));
		root.addOrReplaceChild("r_claw2", CubeListBuilder.create().texOffs(14, 54).addBox(-1.5f, -1.0f, -4.066667f, 3, 2, 5),
				PartPose.offsetAndRotation(-6.0f, 18.5f, -10.0f, 0.3141593f, 0.0f, 0.0f));
		root.addOrReplaceChild("r_arm", CubeListBuilder.create().texOffs(44, 4).addBox(-1.0f, -1.0f, -5.066667f, 2, 2, 6),
				PartPose.offsetAndRotation(-3.0f, 17.0f, -4.0f, 0.0f, 0.7504916f, 0.0f));
		root.addOrReplaceChild("l_claw2", CubeListBuilder.create().texOffs(14, 54).addBox(-1.5f, -1.0f, -4.066667f, 3, 2, 5),
				PartPose.offsetAndRotation(6.0f, 18.5f, -10.0f, 0.3141593f, 0.0f, 0.0f));
		root.addOrReplaceChild("l_claw1", CubeListBuilder.create().texOffs(0, 47).mirror().addBox(-2.0f, -1.0f, -5.066667f, 4, 3, 5),
				PartPose.offset(6.0f, 15.5f, -10.0f));
		root.addOrReplaceChild("l_claw0", CubeListBuilder.create().texOffs(0, 55).mirror().addBox(-2.0f, -2.5f, -3.066667f, 4, 5, 3),
				PartPose.offset(6.0f, 17.0f, -7.0f));
		root.addOrReplaceChild("l_arm", CubeListBuilder.create().texOffs(44, 4).addBox(-1.0f, -1.0f, -4.066667f, 2, 2, 6),
				PartPose.offsetAndRotation(4.0f, 17.0f, -5.0f, 0.0f, -0.7504916f, 0.0f));
		root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 18).addBox(-3.5f, -3.5f, -6.066667f, 7, 7, 6),
				PartPose.offsetAndRotation(0.0f, 18.0f, 0.0f, 0.0523599f, 0.0f, 0.0f));
		root.addOrReplaceChild("head0", CubeListBuilder.create().texOffs(0, 31).addBox(-2.5f, -2.0f, -8.066667f, 5, 5, 2),
				PartPose.offset(0.0f, 18.0f, 0.0f));
		root.addOrReplaceChild("rr_leg1", CubeListBuilder.create().texOffs(36, 4).addBox(-4.5f, 1.0f, -0.9f, 2, 5, 2),
				PartPose.offset(-4.0f, 20.0f, -1.5f));
		root.addOrReplaceChild("rf_leg1", CubeListBuilder.create().texOffs(36, 4).addBox(-5.0f, 1.0f, -1.066667f, 2, 5, 2),
				PartPose.offset(-4.0f, 20.0f, -3.5f));
		root.addOrReplaceChild("lr_leg1", CubeListBuilder.create().texOffs(36, 4).addBox(2.5f, 1.0f, -0.9f, 2, 5, 2),
				PartPose.offset(4.0f, 20.0f, -1.5f));
		root.addOrReplaceChild("lf_leg1", CubeListBuilder.create().texOffs(36, 4).addBox(3.0f, 1.0f, -1.066667f, 2, 5, 2),
				PartPose.offset(4.0f, 20.0f, -3.5f));
		root.addOrReplaceChild("rr_leg0", CubeListBuilder.create().texOffs(36, 0).addBox(-4.5f, -1.0f, -0.9f, 6, 2, 2),
				PartPose.offset(-4.0f, 20.0f, -1.5f));
		root.addOrReplaceChild("rf_leg0", CubeListBuilder.create().texOffs(36, 0).addBox(-5.0f, -1.0f, -1.066667f, 6, 2, 2),
				PartPose.offset(-4.0f, 20.0f, -3.5f));
		root.addOrReplaceChild("lf_leg0", CubeListBuilder.create().texOffs(36, 0).addBox(-1.0f, -1.0f, -1.066667f, 6, 2, 2),
				PartPose.offset(4.0f, 20.0f, -3.5f));
		root.addOrReplaceChild("lr_leg0", CubeListBuilder.create().texOffs(36, 0).addBox(-1.5f, -1.0f, -0.9f, 6, 2, 2),
				PartPose.offset(4.0f, 20.0f, -1.5f));

		ModelPart baked = LayerDefinition.create(mesh, 128, 64).bakeRoot();
		tailHelm = baked.getChild("tail_helm");
		tailBare = baked.getChild("tail_bare");
		rClaw1 = baked.getChild("r_claw1");
		head1 = baked.getChild("head1");
		rClaw0 = baked.getChild("r_claw0");
		rClaw2 = baked.getChild("r_claw2");
		rArm = baked.getChild("r_arm");
		lClaw2 = baked.getChild("l_claw2");
		lClaw1 = baked.getChild("l_claw1");
		lClaw0 = baked.getChild("l_claw0");
		lArm = baked.getChild("l_arm");
		torso = baked.getChild("torso");
		head0 = baked.getChild("head0");
		rrLeg1 = baked.getChild("rr_leg1");
		rfLeg1 = baked.getChild("rf_leg1");
		lrLeg1 = baked.getChild("lr_leg1");
		lfLeg1 = baked.getChild("lf_leg1");
		rrLeg0 = baked.getChild("rr_leg0");
		rfLeg0 = baked.getChild("rf_leg0");
		lfLeg0 = baked.getChild("lf_leg0");
		lrLeg0 = baked.getChild("lr_leg0");
	}

	@Override
	public void setupAnim(EntityEldritchCrab entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		hasHelm = entity.hasHelm();

		// Parts the original only ever rotated once at construction time - re-asserted every frame here
		// since this model has no automatic per-frame pose reset.
		head1.setRotation(0.0f, 0.0f, 0.0f);
		rClaw0.setRotation(0.0f, 0.0f, 0.0f);
		rArm.setRotation(0.0f, 0.7504916f, 0.0f);
		lClaw0.setRotation(0.0f, 0.0f, 0.0f);
		lArm.setRotation(0.0f, -0.7504916f, 0.0f);
		torso.setRotation(0.0523599f, 0.0f, 0.0f);
		head0.setRotation(0.0f, 0.0f, 0.0f);

		// Real walk cycle - four leg pairs, front/back mirrored, same real base stance angles as the
		// original plus the real per-tick swing offset.
		float f9 = -(Mth.cos(limbSwing * 0.6662f * 2.0f) * 0.4f) * limbSwingAmount;
		float f10 = -(Mth.cos(limbSwing * 0.6662f * 2.0f + Mth.PI) * 0.4f) * limbSwingAmount;
		rrLeg1.setRotation(0.0f, 0.2094395f + f9, 0.4363323f + f9);
		rrLeg0.setRotation(0.0f, 0.2094395f + f9, 0.4363323f + f9);
		lrLeg1.setRotation(0.0f, -0.2094395f - f9, -0.4363323f - f9);
		lrLeg0.setRotation(0.0f, -0.2094395f - f9, -0.4363323f - f9);
		rfLeg1.setRotation(0.0f, -0.2094395f + f10, 0.4363323f + f10);
		rfLeg0.setRotation(0.0f, -0.2094395f + f10, 0.4363323f + f10);
		lfLeg1.setRotation(0.0f, 0.2094395f - f10, -0.4363323f - f10);
		lfLeg0.setRotation(0.0f, 0.2094395f - f10, -0.4363323f - f10);

		// Real tail sway.
		float n = Mth.cos(limbSwing * 0.6662f) * 2.0f * limbSwingAmount * 0.125f;
		float n2 = Mth.cos(limbSwing * 0.6662f) * limbSwingAmount * 0.125f;
		tailHelm.setRotation(0.1047198f, n, n2);
		tailBare.setRotation(0.1047198f, n, n2);

		// Real independent claw twitching, driven directly by tick count (matching the original's own
		// direct ticksExisted use, not the interpolated ageInTicks parameter).
		rClaw2.setRotation(0.3141593f - Mth.sin(entity.tickCount / 4.0f) * 0.25f, 0.0f, 0.0f);
		lClaw2.setRotation(0.3141593f + Mth.sin(entity.tickCount / 4.1f) * 0.25f, 0.0f, 0.0f);
		rClaw1.setRotation(Mth.sin(entity.tickCount / 4.0f) * 0.125f, 0.0f, 0.0f);
		lClaw1.setRotation(-Mth.sin(entity.tickCount / 4.1f) * 0.125f, 0.0f, 0.0f);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		(hasHelm ? tailHelm : tailBare).render(poseStack, buffer, packedLight, packedOverlay, color);
		rfLeg1.render(poseStack, buffer, packedLight, packedOverlay, color);
		rClaw1.render(poseStack, buffer, packedLight, packedOverlay, color);
		head1.render(poseStack, buffer, packedLight, packedOverlay, color);
		rClaw0.render(poseStack, buffer, packedLight, packedOverlay, color);
		rClaw2.render(poseStack, buffer, packedLight, packedOverlay, color);
		lClaw2.render(poseStack, buffer, packedLight, packedOverlay, color);
		lClaw1.render(poseStack, buffer, packedLight, packedOverlay, color);
		rArm.render(poseStack, buffer, packedLight, packedOverlay, color);
		torso.render(poseStack, buffer, packedLight, packedOverlay, color);
		rrLeg1.render(poseStack, buffer, packedLight, packedOverlay, color);
		head0.render(poseStack, buffer, packedLight, packedOverlay, color);
		lrLeg1.render(poseStack, buffer, packedLight, packedOverlay, color);
		lfLeg1.render(poseStack, buffer, packedLight, packedOverlay, color);
		rrLeg0.render(poseStack, buffer, packedLight, packedOverlay, color);
		rfLeg0.render(poseStack, buffer, packedLight, packedOverlay, color);
		lfLeg0.render(poseStack, buffer, packedLight, packedOverlay, color);
		lrLeg0.render(poseStack, buffer, packedLight, packedOverlay, color);
		lClaw0.render(poseStack, buffer, packedLight, packedOverlay, color);
		lArm.render(poseStack, buffer, packedLight, packedOverlay, color);
	}
}

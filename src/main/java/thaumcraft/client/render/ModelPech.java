package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.monster.EntityPech;

/**
 * Real port of the true original's {@code ModelPech} - added 2026-09-25 after the first playtest showed the
 * pech drawn on the vanilla player-proportioned humanoid (a documented cut in {@code EntityPech}'s doc comment,
 * but it looks nothing like the original: a short, hunched forager with a big head, jowls and a huge
 * back-pack). Same 128x64 sheet, same boxes, rotation points and rotations as the original constructor
 * (body tilted 0.31 rad forward; head at y=8; the two back-pack boxes as free-standing parts), and the same
 * hand-written animation: limb swing, the mirrored idle arm sway, the pack wobble, and the "mumble" jaw flap
 * driven by {@link EntityPech#mumble}.
 *
 * <p>Built as a {@link HumanoidModel} so vanilla's armor and held-item layers keep working. The jowls hang off
 * the head part (the original copied the head's rotation onto them and added the jaw motion, which is the same
 * thing as a child part with only the jaw rotation); the two packs are separate parts drawn after the rest.
 * Not reproduced: the original's custom held-item nudge ({@code LayerHeldItemPech}: -0.1 down, extra for bows).
 */
public class ModelPech extends HumanoidModel<EntityPech> {

	private final ModelPart jowls;
	private final ModelPart lowerPack;
	private final ModelPart upperPack;

	public ModelPech(ModelPart root) {
		super(root);
		this.jowls = head.getChild("jowls");
		this.lowerPack = root.getChild("lower_pack");
		this.upperPack = root.getChild("upper_pack");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
		PartDefinition root = mesh.getRoot();

		PartDefinition head = root.addOrReplaceChild("head",
				CubeListBuilder.create().texOffs(2, 11).addBox(-3.5f, -5.0f, -5.0f, 7, 5, 5), PartPose.offset(0.0f, 8.0f, 0.0f));
		head.addOrReplaceChild("jowls", CubeListBuilder.create().texOffs(1, 21).addBox(-4.0f, -1.0f, -6.0f, 8, 3, 5), PartPose.ZERO);
		root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offset(0.0f, 8.0f, 0.0f));
		root.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(34, 12).addBox(-3.0f, 0.0f, 0.0f, 6, 10, 6),
				PartPose.offsetAndRotation(0.0f, 9.0f, -3.0f, 0.3129957f, 0.0f, 0.0f));
		root.addOrReplaceChild("right_leg",
				CubeListBuilder.create().texOffs(35, 1).mirror(true).addBox(-2.9f, 0.0f, 0.0f, 3, 6, 3), PartPose.offset(0.0f, 18.0f, 0.0f));
		root.addOrReplaceChild("left_leg",
				CubeListBuilder.create().texOffs(35, 1).addBox(-0.1f, 0.0f, 0.0f, 3, 6, 3), PartPose.offset(0.0f, 18.0f, 0.0f));
		root.addOrReplaceChild("right_arm",
				CubeListBuilder.create().texOffs(52, 2).mirror(true).addBox(-2.0f, 0.0f, -1.0f, 2, 6, 2), PartPose.offset(-3.0f, 10.0f, -1.0f));
		root.addOrReplaceChild("left_arm",
				CubeListBuilder.create().texOffs(52, 2).addBox(0.0f, 0.0f, -1.0f, 2, 6, 2), PartPose.offset(3.0f, 10.0f, -1.0f));
		root.addOrReplaceChild("lower_pack",
				CubeListBuilder.create().texOffs(0, 0).addBox(-5.0f, 0.0f, 0.0f, 10, 5, 5),
				PartPose.offsetAndRotation(0.0f, 10.0f, 3.5f, 0.3013602f, 0.0f, 0.0f));
		root.addOrReplaceChild("upper_pack",
				CubeListBuilder.create().texOffs(64, 1).addBox(-7.5f, -14.0f, 0.0f, 15, 14, 11),
				PartPose.offsetAndRotation(0.0f, 10.0f, 3.0f, 0.4537856f, 0.0f, 0.0f));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(EntityPech entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		head.yRot = netHeadYaw * ((float) Math.PI / 180.0f);
		head.xRot = headPitch * ((float) Math.PI / 180.0f);
		// jaw flap, relative to the head (the original added the head's own pitch on top, which the parent does)
		jowls.xRot = 0.2617994f + Mth.cos(limbSwing * 0.6662f) * limbSwingAmount * 0.25f + 0.34906587f * Math.abs(Mth.sin(entity.mumble / 8.0f));

		rightArm.xRot = Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * 2.0f * limbSwingAmount * 0.5f;
		leftArm.xRot = Mth.cos(limbSwing * 0.6662f) * 2.0f * limbSwingAmount * 0.5f;
		rightArm.zRot = 0.0f;
		leftArm.zRot = 0.0f;
		rightLeg.xRot = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
		leftLeg.xRot = Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * 1.4f * limbSwingAmount;
		rightLeg.yRot = 0.0f;
		leftLeg.yRot = 0.0f;
		lowerPack.yRot = Mth.cos(limbSwing * 0.6662f) * 2.0f * limbSwingAmount * 0.125f;
		lowerPack.zRot = Mth.cos(limbSwing * 0.6662f) * 2.0f * limbSwingAmount * 0.125f;

		if (riding) {
			rightArm.xRot -= 0.62831855f;
			leftArm.xRot -= 0.62831855f;
			rightLeg.xRot = -1.2566371f;
			leftLeg.xRot = -1.2566371f;
			rightLeg.yRot = 0.31415927f;
			leftLeg.yRot = -0.31415927f;
		}
		rightArm.yRot = 0.0f;
		leftArm.yRot = 0.0f;

		if (attackTime > 0.0f) {
			float progress = 1.0f - attackTime;
			progress *= progress;
			progress *= progress;
			progress = 1.0f - progress;
			float f7 = Mth.sin(progress * (float) Math.PI);
			float f8 = Mth.sin(attackTime * (float) Math.PI) * -(head.xRot - 0.7f) * 0.75f;
			rightArm.xRot -= (float) (f7 * 1.2 + f8);
			rightArm.zRot = Mth.sin(attackTime * (float) Math.PI) * -0.4f;
		}
		if (crouching) {
			rightArm.xRot += 0.4f;
			leftArm.xRot += 0.4f;
		}
		rightArm.zRot += Mth.cos(ageInTicks * 0.09f) * 0.05f + 0.05f;
		leftArm.zRot -= Mth.cos(ageInTicks * 0.09f) * 0.05f + 0.05f;
		rightArm.xRot += Mth.sin(ageInTicks * 0.067f) * 0.05f;
		leftArm.xRot -= Mth.sin(ageInTicks * 0.067f) * 0.05f;
	}

	/** The two back-pack parts aren't reachable from {@link HumanoidModel}'s head/body part lists - draw them here. */
	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
		lowerPack.render(poseStack, buffer, packedLight, packedOverlay, color);
		upperPack.render(poseStack, buffer, packedLight, packedOverlay, color);
	}
}

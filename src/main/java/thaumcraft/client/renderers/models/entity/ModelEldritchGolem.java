package thaumcraft.client.renderers.models.entity;

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
import thaumcraft.common.entities.monster.boss.EntityEldritchGolem;

/**
 * Real, faithful geometry+animation translation of the true original's own real {@code ModelEldritchGolem} -
 * 36 real parented cuboids (matching the true original's own real count exactly). Replaces the now-deleted
 * {@code ModelEldritchPlaceholder} single-box stand-in for {@link thaumcraft.client.render.RenderEldritchGolem}
 * - see {@link ModelEldritchGuardian}'s own doc comment for the real shared technique/conventions this class
 * also follows (every part's rotation explicitly
 * reasserted every frame in {@link #setupAnim}, no {@link net.minecraft.client.model.HierarchicalModel} auto
 * pose reset).
 *
 * <p>Real, faithfully-kept true-original quirk: {@code frontcloth1} really is rendered twice in {@link
 * #renderToBuffer} (once early, once again after the collar) - harmless (same transform both times), but kept
 * rather than "fixed", matching this whole port's own established precedent of faithfully reproducing real
 * true-original behavior rather than silently improving it.
 *
 * <p>Not reproduced, being dead in the original: the head "nod" while {@code getSpawnTimer()} runs. The original's
 * client only learned the timer from event 18, sent when the server's timer read 150, but the golem's server timer is
 * only ever set to 100, so its client always read 0.
 * <p>Real, previously-unwired gap found and restored alongside this mesh: the true original's own real 10-tick
 * attack-swing arm animation needs a real client-mirrored countdown, which needs the true original's own real
 * {@code handleStatusUpdate} case 4 entity-event broadcast - this port's own {@code EntityEldritchGolem} had
 * the real server-side attack-cooldown gate ({@code hurtTimer}) but never actually broadcast the matching
 * real client entity-event, since nothing client-side needed it before this mesh existed. See {@code
 * EntityEldritchGolem#doHurtTarget}'s own doc comment for the real fix. The swing is smoothed between ticks as
 * the original ({@code timer - partialTick}, the partial tick taken in {@link #prepareMobModel}).
 */
public class ModelEldritchGolem extends EntityModel<EntityEldritchGolem> {

	private float partialTick;

	@Override
	public void prepareMobModel(EntityEldritchGolem entity, float limbSwing, float limbSwingAmount, float partialTick) {
		this.partialTick = partialTick;
	}

	private final ModelPart frontcloth0;
	private final ModelPart frontcloth1;
	private final ModelPart frontcloth2;
	private final ModelPart collarL;
	private final ModelPart collarR;
	private final ModelPart collarB;
	private final ModelPart collarF;
	private final ModelPart collarBlack;
	private final ModelPart cloak1;
	private final ModelPart cloak2;
	private final ModelPart cloak3;
	private final ModelPart cloakCL;
	private final ModelPart cloakCR;
	private final ModelPart head;
	private final ModelPart head2;
	private final ModelPart torso;
	private final ModelPart armR;
	private final ModelPart shoulderR;
	private final ModelPart shoulderR0;
	private final ModelPart shoulderR1;
	private final ModelPart shoulderR2;
	private final ModelPart armL;
	private final ModelPart shoulderL;
	private final ModelPart shoulderL0;
	private final ModelPart shoulderL1;
	private final ModelPart shoulderL2;
	private final ModelPart backpanelR1;
	private final ModelPart waistR1;
	private final ModelPart waistR2;
	private final ModelPart waistR3;
	private final ModelPart legR;
	private final ModelPart waistL1;
	private final ModelPart waistL2;
	private final ModelPart waistL3;
	private final ModelPart backpanelL1;
	private final ModelPart legL;

	private boolean headless;

	public ModelEldritchGolem() {
		super(RenderType::entityCutout);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		root.addOrReplaceChild("cloak1", CubeListBuilder.create().texOffs(0, 47).addBox(-5.0f, 1.5f, 4.0f, 10, 12, 1),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.1396263f, 0.0f, 0.0f));
		root.addOrReplaceChild("cloak3", CubeListBuilder.create().texOffs(0, 37).addBox(-5.0f, 17.5f, -0.8f, 10, 4, 1),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.4465716f, 0.0f, 0.0f));
		root.addOrReplaceChild("cloak2", CubeListBuilder.create().texOffs(0, 59).addBox(-5.0f, 13.5f, 1.7f, 10, 4, 1),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.3069452f, 0.0f, 0.0f));
		root.addOrReplaceChild("cloak_cl", CubeListBuilder.create().texOffs(0, 43).addBox(3.0f, 0.5f, 2.0f, 2, 1, 3),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.1396263f, 0.0f, 0.0f));
		root.addOrReplaceChild("cloak_cr", CubeListBuilder.create().texOffs(0, 43).addBox(-5.0f, 0.5f, 2.0f, 2, 1, 3),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.1396263f, 0.0f, 0.0f));
		root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(47, 12).addBox(-3.5f, -6.0f, -2.5f, 7, 7, 5),
				PartPose.offsetAndRotation(0.0f, 4.5f, -3.8f, -0.1047198f, 0.0f, 0.0f));
		root.addOrReplaceChild("head2", CubeListBuilder.create().texOffs(26, 16).addBox(-2.0f, -2.0f, -2.0f, 4, 4, 4),
				PartPose.offsetAndRotation(0.0f, 0.0f, -5.0f, -0.1047198f, 0.0f, 0.0f));
		root.addOrReplaceChild("collar_l", CubeListBuilder.create().texOffs(75, 50).addBox(3.5f, -0.5f, -7.0f, 1, 4, 10),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.837758f, 0.0f, 0.0f));
		root.addOrReplaceChild("collar_r", CubeListBuilder.create().texOffs(67, 50).addBox(-4.5f, -0.5f, -7.0f, 1, 4, 10),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.837758f, 0.0f, 0.0f));
		root.addOrReplaceChild("collar_b", CubeListBuilder.create().texOffs(77, 59).addBox(-3.5f, -0.5f, 2.0f, 7, 4, 1),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.837758f, 0.0f, 0.0f));
		root.addOrReplaceChild("collar_f", CubeListBuilder.create().texOffs(77, 59).addBox(-3.5f, -0.5f, -7.0f, 7, 4, 1),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.837758f, 0.0f, 0.0f));
		root.addOrReplaceChild("collar_black", CubeListBuilder.create().texOffs(22, 0).addBox(-3.5f, 0.0f, -6.0f, 7, 1, 8),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.837758f, 0.0f, 0.0f));
		root.addOrReplaceChild("frontcloth0", CubeListBuilder.create().texOffs(114, 52).addBox(-3.0f, 3.2f, -3.5f, 6, 10, 1),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.1745329f, 0.0f, 0.0f));
		root.addOrReplaceChild("frontcloth1", CubeListBuilder.create().texOffs(114, 39).addBox(-1.0f, 1.5f, -3.5f, 6, 6, 1),
				PartPose.offsetAndRotation(-2.0f, 12.0f, 0.0f, -0.1047198f, 0.0f, 0.0f));
		root.addOrReplaceChild("frontcloth2", CubeListBuilder.create().texOffs(114, 47).addBox(-1.0f, 8.5f, -1.5f, 6, 3, 1),
				PartPose.offsetAndRotation(-2.0f, 11.0f, 0.0f, -0.3316126f, 0.0f, 0.0f));
		root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(34, 45).addBox(-5.0f, 2.5f, -3.0f, 10, 10, 6),
				PartPose.offsetAndRotation(0.0f, 0.0f, -2.5f, 0.1745329f, 0.0f, 0.0f));

		PartDefinition armRDef = root.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(78, 32).addBox(-3.5f, 1.5f, -2.0f, 4, 13, 5),
				PartPose.offsetAndRotation(-5.0f, 3.0f, -2.0f, 0.0f, 0.0f, 0.1047198f));
		armRDef.addOrReplaceChild("shoulder_r", CubeListBuilder.create().texOffs(0, 0).addBox(-4.3f, -1.0f, -3.0f, 4, 5, 7),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.186824f));
		armRDef.addOrReplaceChild("shoulder_r0", CubeListBuilder.create().texOffs(56, 31).addBox(-4.5f, -1.5f, -2.5f, 5, 6, 6), PartPose.ZERO);
		armRDef.addOrReplaceChild("shoulder_r1", CubeListBuilder.create().texOffs(0, 23).addBox(-3.3f, 4.0f, -2.5f, 1, 2, 6),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.186824f));
		armRDef.addOrReplaceChild("shoulder_r2", CubeListBuilder.create().texOffs(0, 12).addBox(-2.3f, 4.0f, -3.0f, 2, 3, 7),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.186824f));

		PartDefinition armLDef = root.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(78, 32).mirror().addBox(-0.5f, 1.5f, -2.0f, 4, 13, 5),
				PartPose.offsetAndRotation(5.0f, 3.0f, -2.0f, 0.0f, 0.0f, -0.1047198f));
		armLDef.addOrReplaceChild("shoulder_l", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(0.3f, -1.0f, -3.0f, 4, 5, 7),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.186824f));
		armLDef.addOrReplaceChild("shoulder_l0", CubeListBuilder.create().texOffs(56, 31).mirror().addBox(-0.5f, -1.5f, -2.5f, 5, 6, 6), PartPose.ZERO);
		armLDef.addOrReplaceChild("shoulder_l1", CubeListBuilder.create().texOffs(0, 23).mirror().addBox(2.3f, 4.0f, -2.5f, 1, 2, 6),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.186824f));
		armLDef.addOrReplaceChild("shoulder_l2", CubeListBuilder.create().texOffs(0, 12).mirror().addBox(0.3f, 4.0f, -3.0f, 2, 3, 7),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.186824f));

		root.addOrReplaceChild("backpanel_r1", CubeListBuilder.create().texOffs(96, 7).addBox(0.0f, 2.5f, -2.5f, 2, 2, 5),
				PartPose.offsetAndRotation(-2.0f, 12.0f, 0.0f, 0.0f, 0.0f, 0.1396263f));
		root.addOrReplaceChild("waist_r1", CubeListBuilder.create().texOffs(96, 14).addBox(-3.0f, -0.5f, -2.5f, 5, 3, 5),
				PartPose.offsetAndRotation(-2.0f, 12.0f, 0.0f, 0.0f, 0.0f, 0.1396263f));
		root.addOrReplaceChild("waist_r2", CubeListBuilder.create().texOffs(116, 13).addBox(-3.0f, 2.5f, -2.5f, 1, 4, 5),
				PartPose.offsetAndRotation(-2.0f, 12.0f, 0.0f, 0.0f, 0.0f, 0.1396263f));
		root.addOrReplaceChild("waist_r3", CubeListBuilder.create().texOffs(114, 5).mirror().addBox(-2.0f, 2.5f, -2.5f, 2, 3, 5),
				PartPose.offsetAndRotation(-2.0f, 12.0f, 0.0f, 0.0f, 0.0f, 0.1396263f));
		root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(79, 19).addBox(-2.5f, 2.5f, -2.0f, 4, 9, 4),
				PartPose.offset(-2.0f, 12.5f, 0.0f));
		root.addOrReplaceChild("waist_l1", CubeListBuilder.create().texOffs(96, 14).addBox(-2.0f, -0.5f, -2.5f, 5, 3, 5),
				PartPose.offsetAndRotation(2.0f, 12.0f, 0.0f, 0.0f, 0.0f, -0.1396263f));
		root.addOrReplaceChild("waist_l2", CubeListBuilder.create().texOffs(116, 13).addBox(2.0f, 2.5f, -2.5f, 1, 4, 5),
				PartPose.offsetAndRotation(2.0f, 12.0f, 0.0f, 0.0f, 0.0f, -0.1396263f));
		root.addOrReplaceChild("waist_l3", CubeListBuilder.create().texOffs(114, 5).addBox(0.0f, 2.5f, -2.5f, 2, 3, 5),
				PartPose.offsetAndRotation(2.0f, 12.0f, 0.0f, 0.0f, 0.0f, -0.1396263f));
		root.addOrReplaceChild("backpanel_l1", CubeListBuilder.create().texOffs(96, 7).addBox(-2.0f, 2.5f, -2.5f, 2, 2, 5),
				PartPose.offsetAndRotation(2.0f, 12.0f, 0.0f, 0.0f, 0.0f, -0.1396263f));
		root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(79, 19).addBox(-1.5f, 2.5f, -2.0f, 4, 9, 4),
				PartPose.offset(2.0f, 12.5f, 0.0f));

		ModelPart baked = LayerDefinition.create(mesh, 128, 64).bakeRoot();
		cloak1 = baked.getChild("cloak1");
		cloak3 = baked.getChild("cloak3");
		cloak2 = baked.getChild("cloak2");
		cloakCL = baked.getChild("cloak_cl");
		cloakCR = baked.getChild("cloak_cr");
		head = baked.getChild("head");
		head2 = baked.getChild("head2");
		collarL = baked.getChild("collar_l");
		collarR = baked.getChild("collar_r");
		collarB = baked.getChild("collar_b");
		collarF = baked.getChild("collar_f");
		collarBlack = baked.getChild("collar_black");
		frontcloth0 = baked.getChild("frontcloth0");
		frontcloth1 = baked.getChild("frontcloth1");
		frontcloth2 = baked.getChild("frontcloth2");
		torso = baked.getChild("torso");
		armR = baked.getChild("arm_r");
		shoulderR = armR.getChild("shoulder_r");
		shoulderR0 = armR.getChild("shoulder_r0");
		shoulderR1 = armR.getChild("shoulder_r1");
		shoulderR2 = armR.getChild("shoulder_r2");
		armL = baked.getChild("arm_l");
		shoulderL = armL.getChild("shoulder_l");
		shoulderL0 = armL.getChild("shoulder_l0");
		shoulderL1 = armL.getChild("shoulder_l1");
		shoulderL2 = armL.getChild("shoulder_l2");
		backpanelR1 = baked.getChild("backpanel_r1");
		waistR1 = baked.getChild("waist_r1");
		waistR2 = baked.getChild("waist_r2");
		waistR3 = baked.getChild("waist_r3");
		legR = baked.getChild("leg_r");
		waistL1 = baked.getChild("waist_l1");
		waistL2 = baked.getChild("waist_l2");
		waistL3 = baked.getChild("waist_l3");
		backpanelL1 = baked.getChild("backpanel_l1");
		legL = baked.getChild("leg_l");
	}

	@Override
	public void setupAnim(EntityEldritchGolem entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		headless = entity.isHeadless();

		// Parts the true original only ever rotated once at construction time - reasserted every frame here,
		// same real defensive convention ModelEldritchGuardian/ModelEldritchCrab already established.
		cloakCL.setRotation(0.1396263f, 0.0f, 0.0f);
		cloakCR.setRotation(0.1396263f, 0.0f, 0.0f);
		collarL.setRotation(0.837758f, 0.0f, 0.0f);
		collarR.setRotation(0.837758f, 0.0f, 0.0f);
		collarB.setRotation(0.837758f, 0.0f, 0.0f);
		collarF.setRotation(0.837758f, 0.0f, 0.0f);
		collarBlack.setRotation(0.837758f, 0.0f, 0.0f);
		frontcloth0.setRotation(0.1745329f, 0.0f, 0.0f);
		torso.setRotation(0.1745329f, 0.0f, 0.0f);
		shoulderR.setRotation(0.0f, 0.0f, 1.186824f);
		shoulderR0.setRotation(0.0f, 0.0f, 0.0f);
		shoulderR1.setRotation(0.0f, 0.0f, 1.186824f);
		shoulderR2.setRotation(0.0f, 0.0f, 1.186824f);
		shoulderL.setRotation(0.0f, 0.0f, -1.186824f);
		shoulderL0.setRotation(0.0f, 0.0f, 0.0f);
		shoulderL1.setRotation(0.0f, 0.0f, -1.186824f);
		shoulderL2.setRotation(0.0f, 0.0f, -1.186824f);
		backpanelR1.setRotation(0.0f, 0.0f, 0.1396263f);
		waistR1.setRotation(0.0f, 0.0f, 0.1396263f);
		waistR2.setRotation(0.0f, 0.0f, 0.1396263f);
		waistR3.setRotation(0.0f, 0.0f, 0.1396263f);
		waistL1.setRotation(0.0f, 0.0f, -0.1396263f);
		waistL2.setRotation(0.0f, 0.0f, -0.1396263f);
		waistL3.setRotation(0.0f, 0.0f, -0.1396263f);
		backpanelL1.setRotation(0.0f, 0.0f, -0.1396263f);

		// Real head look - see the class doc comment for the real, disclosed spawn-timer-nod simplification.
		head.yRot = netHeadYaw / 4.0f / Mth.RAD_TO_DEG;
		head.xRot = headPitch / 2.0f / Mth.RAD_TO_DEG;
		head2.yRot = netHeadYaw / Mth.RAD_TO_DEG;
		head2.xRot = headPitch / Mth.RAD_TO_DEG;

		// Real leg walk cycle.
		legR.xRot = Mth.cos(limbSwing * 0.4662f) * 1.4f * limbSwingAmount;
		legL.xRot = Mth.cos(limbSwing * 0.4662f + Mth.PI) * 1.4f * limbSwingAmount;

		// Real cloak/frontcloth sway, walk-driven (the true original computed this directly in its own real
		// render() rather than setRotationAngles - same real inputs, ported as-is).
		float a = Mth.cos(limbSwing * 0.44f) * 1.4f * limbSwingAmount;
		float b = Mth.cos(limbSwing * 0.44f + Mth.PI) * 1.4f * limbSwingAmount;
		float c = Math.min(a, b);
		frontcloth1.setRotation(c - 0.1047198f, 0.0f, 0.0f);
		frontcloth2.setRotation(c - 0.3316126f, 0.0f, 0.0f);
		cloak1.setRotation(-c / 3.0f + 0.1396263f, 0.0f, 0.0f);
		cloak2.setRotation(-c / 3.0f + 0.3069452f, 0.0f, 0.0f);
		cloak3.setRotation(-c / 3.0f + 0.4465716f, 0.0f, 0.0f);

		// Real attack-swing arm animation vs. real idle sway - see the class doc comment for the real,
		// previously-unwired client entity-event restoration and its own real sub-tick simplification.
		int swing = entity.getHurtTimer();
		if (swing > 0) {
			float t = -2.0f + 1.5f * doAbs(swing - partialTick, 10.0f);
			armR.setRotation(t, 0.0f, 0.1047198f);
			armL.setRotation(t, 0.0f, -0.1047198f);
		} else {
			armR.setRotation(Mth.cos(limbSwing * 0.4f + Mth.PI) * 2.0f * limbSwingAmount * 0.5f, 0.0f, 0.1047198f);
			armL.setRotation(Mth.cos(limbSwing * 0.4f) * 2.0f * limbSwingAmount * 0.5f, 0.0f, -0.1047198f);
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		frontcloth1.render(poseStack, buffer, packedLight, packedOverlay, color);
		collarL.render(poseStack, buffer, packedLight, packedOverlay, color);
		collarBlack.render(poseStack, buffer, packedLight, packedOverlay, color);
		cloak1.render(poseStack, buffer, packedLight, packedOverlay, color);
		cloakCL.render(poseStack, buffer, packedLight, packedOverlay, color);
		cloakCR.render(poseStack, buffer, packedLight, packedOverlay, color);
		cloak3.render(poseStack, buffer, packedLight, packedOverlay, color);
		cloak2.render(poseStack, buffer, packedLight, packedOverlay, color);
		(headless ? head2 : head).render(poseStack, buffer, packedLight, packedOverlay, color);
		frontcloth0.render(poseStack, buffer, packedLight, packedOverlay, color);
		collarB.render(poseStack, buffer, packedLight, packedOverlay, color);
		torso.render(poseStack, buffer, packedLight, packedOverlay, color);
		collarR.render(poseStack, buffer, packedLight, packedOverlay, color);
		collarF.render(poseStack, buffer, packedLight, packedOverlay, color);
		frontcloth1.render(poseStack, buffer, packedLight, packedOverlay, color);
		armL.render(poseStack, buffer, packedLight, packedOverlay, color);
		armR.render(poseStack, buffer, packedLight, packedOverlay, color);
		backpanelR1.render(poseStack, buffer, packedLight, packedOverlay, color);
		waistR1.render(poseStack, buffer, packedLight, packedOverlay, color);
		waistR2.render(poseStack, buffer, packedLight, packedOverlay, color);
		waistR3.render(poseStack, buffer, packedLight, packedOverlay, color);
		legR.render(poseStack, buffer, packedLight, packedOverlay, color);
		waistL1.render(poseStack, buffer, packedLight, packedOverlay, color);
		waistL2.render(poseStack, buffer, packedLight, packedOverlay, color);
		waistL3.render(poseStack, buffer, packedLight, packedOverlay, color);
		frontcloth2.render(poseStack, buffer, packedLight, packedOverlay, color);
		backpanelL1.render(poseStack, buffer, packedLight, packedOverlay, color);
		legL.render(poseStack, buffer, packedLight, packedOverlay, color);
	}

	/** Faithful translation of the true original's own real helper - real "triangle wave" easing used for the
	 * real attack-swing arm animation. */
	private static float doAbs(float a, float b) {
		return (Math.abs(a % b - b * 0.5f) - b * 0.25f) / (b * 0.25f);
	}
}

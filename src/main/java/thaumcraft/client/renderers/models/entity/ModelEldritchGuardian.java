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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;
import thaumcraft.common.entities.monster.boss.EntityEldritchWarden;

/**
 * Real, faithful geometry+animation translation of the true original's own real {@code ModelEldritchGuardian}
 * - 54 real parented cuboids (matching the true original's own real count exactly), shared between {@link
 * thaumcraft.client.render.RenderEldritchGuardian}/{@link thaumcraft.client.render.RenderEldritchWarden} the
 * same real way the true original shared one model class between both entities. Replaces the now-deleted
 * {@code ModelEldritchPlaceholder} single-box stand-in for both (GameTest can't verify a mesh port at all;
 * this needs a real human {@code runClient} screenshot check, tracked in the UI visual-parity checklist).
 *
 * <p>Every part's rotation is explicitly reasserted every frame in {@link #setupAnim} (not just the ones the
 * true original's own {@code setRotationAngles} touched) - same real defensive convention {@link
 * thaumcraft.client.render.ModelEldritchCrab} already established in this port, since this model doesn't use
 * {@link net.minecraft.client.model.HierarchicalModel}'s automatic per-frame pose reset.
 *
 * <p>Real, disclosed simplification for {@code HoodEye} (the Warden-only glowing eye box hidden inside
 * {@code Hood1}'s own geometry): the true original's own real technique directly hacked the fixed-function
 * lightmap texture coordinate (a pulsing ~195-225 raw coordinate, decoded here as a real pulsing block-light
 * level via {@link LightTexture#pack}) AND additively blended it slightly scaled up to read as a glow through
 * the hood. This port keeps the real pulsing-lightmap mechanic (draws {@code HoodEye} at a computed bright,
 * pulsing {@code packedLight} rather than the model's own real ambient light) but renders it through the same
 * single {@code entityCutout} buffer as every other part rather than a second additive pass - {@link
 * #renderToBuffer} only receives one already-selected {@link VertexConsumer}, and standing up a genuinely
 * separate additive {@link RenderType} pass would need the entity renderer's own {@link
 * net.minecraft.client.renderer.MultiBufferSource}, not available from inside a model class. Same real
 * "pulsing glow via a brighter part" visual goal, simpler real single-pass mechanism.
 */
public class ModelEldritchGuardian<T extends Mob> extends EntityModel<T> {

	/** Set by the renderer each frame: the Guardian's ghostly fade (1 for the Warden). */
	public float alpha = 1.0f;

	private final ModelPart beltR;
	private final ModelPart mbelt;
	private final ModelPart mbeltL;
	private final ModelPart mbeltR;
	private final ModelPart beltL;
	private final ModelPart chestplate;
	private final ModelPart hoodEye;
	private final ModelPart hood1;
	private final ModelPart hood2;
	private final ModelPart hood3;
	private final ModelPart hood4;
	private final ModelPart backplate;
	private final ModelPart shoulderplateTopR;
	private final ModelPart shoulderplateR1;
	private final ModelPart shoulderplateR2;
	private final ModelPart shoulderplateR3;
	private final ModelPart shoulderR;
	private final ModelPart armL1;
	private final ModelPart armL2;
	private final ModelPart armL3;
	private final ModelPart armR1;
	private final ModelPart armR2;
	private final ModelPart armR3;
	private final ModelPart shoulderL;
	private final ModelPart shoulderplateLtop;
	private final ModelPart shoulderplateL1;
	private final ModelPart shoulderplateL2;
	private final ModelPart shoulderplateL3;
	private final ModelPart legpanelR4;
	private final ModelPart legpanelR5;
	private final ModelPart legpanelR6;
	private final ModelPart backpanelR1;
	private final ModelPart backpanelR2;
	private final ModelPart backpanelR3;
	private final ModelPart backpanelL3;
	private final ModelPart legpanelL4;
	private final ModelPart legpanelL5;
	private final ModelPart legpanelL6;
	private final ModelPart backpanelL1;
	private final ModelPart backpanelL2;
	private final ModelPart sidepanelL1;
	private final ModelPart sidepanelR1;
	private final ModelPart sidepanelR2;
	private final ModelPart sidepanelR3;
	private final ModelPart sidepanelR4;
	private final ModelPart sidepanelL2;
	private final ModelPart sidepanelL3;
	private final ModelPart sidepanelL4;
	private final ModelPart legpanelC1;
	private final ModelPart legpanelC2;
	private final ModelPart legpanelC3;
	private final ModelPart cloak1;
	private final ModelPart cloak2;
	private final ModelPart cloak3;

	private boolean isWarden;
	private int hoodEyeLight;

	public ModelEldritchGuardian() {
		super(RenderType::entityTranslucent);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		root.addOrReplaceChild("belt_r", CubeListBuilder.create().texOffs(76, 44).addBox(-5.0f, 4.0f, -3.0f, 1, 3, 6),
				PartPose.offset(0.0f, -6.0f, 0.0f));
		root.addOrReplaceChild("mbelt", CubeListBuilder.create().texOffs(56, 55).addBox(-4.0f, 8.0f, -3.0f, 8, 4, 1),
				PartPose.offset(0.0f, -6.0f, 0.0f));
		root.addOrReplaceChild("mbelt_l", CubeListBuilder.create().texOffs(76, 44).addBox(4.0f, 8.0f, -3.0f, 1, 3, 6),
				PartPose.offset(0.0f, -6.0f, 0.0f));
		root.addOrReplaceChild("mbelt_r", CubeListBuilder.create().texOffs(76, 44).addBox(-5.0f, 8.0f, -3.0f, 1, 3, 6),
				PartPose.offset(0.0f, -6.0f, 0.0f));
		root.addOrReplaceChild("belt_l", CubeListBuilder.create().texOffs(76, 44).addBox(4.0f, 4.0f, -3.0f, 1, 3, 6),
				PartPose.offset(0.0f, -6.0f, 0.0f));
		root.addOrReplaceChild("chestplate", CubeListBuilder.create().texOffs(56, 45).addBox(-4.0f, 1.0f, -4.0f, 8, 7, 2),
				PartPose.offset(0.0f, -6.0f, 0.0f));
		root.addOrReplaceChild("hood_eye", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8),
				PartPose.offset(0.0f, -6.0f, 0.0f));

		PartDefinition hood1Def = root.addOrReplaceChild("hood1", CubeListBuilder.create().texOffs(40, 12).addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8),
				PartPose.offset(0.0f, -6.0f, 0.0f));
		hood1Def.addOrReplaceChild("hood2", CubeListBuilder.create().texOffs(36, 28).addBox(-3.5f, -8.7f, 2.0f, 7, 7, 3),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.2268928f, 0.0f, 0.0f));
		hood1Def.addOrReplaceChild("hood3", CubeListBuilder.create().texOffs(22, 19).addBox(-3.0f, -9.0f, 2.5f, 6, 6, 3),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.3490659f, 0.0f, 0.0f));
		hood1Def.addOrReplaceChild("hood4", CubeListBuilder.create().texOffs(40, 4).addBox(-2.5f, -9.7f, 3.5f, 5, 5, 3),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.5759587f, 0.0f, 0.0f));

		root.addOrReplaceChild("backplate", CubeListBuilder.create().texOffs(36, 45).addBox(-4.0f, 1.0f, 2.0f, 8, 11, 2),
				PartPose.offset(0.0f, -6.0f, 0.0f));
		root.addOrReplaceChild("shoulderplate_top_r", CubeListBuilder.create().texOffs(110, 37).addBox(-5.5f, -2.5f, -3.5f, 2, 1, 7),
				PartPose.offsetAndRotation(-5.0f, -4.0f, 0.0f, -0.3665191f, 0.3141593f, 0.4363323f));
		root.addOrReplaceChild("shoulderplate_r1", CubeListBuilder.create().texOffs(110, 45).addBox(3.5f, -1.5f, -3.5f, 1, 4, 7),
				PartPose.offsetAndRotation(5.0f, -4.0f, 0.0f, -0.3665191f, -0.3141593f, -0.4363323f));
		root.addOrReplaceChild("shoulderplate_r2", CubeListBuilder.create().texOffs(94, 45).addBox(-3.5f, 1.5f, -3.5f, 1, 3, 7),
				PartPose.offsetAndRotation(-5.0f, -4.0f, 0.0f, -0.3665191f, 0.3141593f, 0.4363323f));
		root.addOrReplaceChild("shoulderplate_r3", CubeListBuilder.create().texOffs(94, 45).addBox(-2.5f, 3.5f, -3.5f, 1, 3, 7),
				PartPose.offsetAndRotation(-5.0f, -4.0f, 0.0f, -0.3665191f, 0.3141593f, 0.4363323f));
		root.addOrReplaceChild("shoulder_r", CubeListBuilder.create().texOffs(56, 35).addBox(-3.5f, -2.5f, -2.5f, 5, 5, 5),
				PartPose.offsetAndRotation(-5.0f, -4.0f, 0.0f, -0.3665191f, 0.122173f, 0.0349066f));

		PartDefinition armL1Def = root.addOrReplaceChild("arm_l1", CubeListBuilder.create().texOffs(72, 8).addBox(-1.0f, 2.5f, -1.5f, 4, 10, 5),
				PartPose.offsetAndRotation(5.0f, -4.0f, 0.0f, -0.9599311f, -0.1047198f, -0.1919862f));
		armL1Def.addOrReplaceChild("arm_l2", CubeListBuilder.create().texOffs(76, 28).addBox(-1.0f, 9.5f, 3.5f, 4, 3, 3), PartPose.ZERO);
		armL1Def.addOrReplaceChild("arm_l3", CubeListBuilder.create().texOffs(76, 23).addBox(-1.0f, 6.5f, 3.5f, 4, 3, 2), PartPose.ZERO);

		PartDefinition armR1Def = root.addOrReplaceChild("arm_r1", CubeListBuilder.create().texOffs(72, 8).addBox(-3.0f, 2.5f, -1.5f, 4, 10, 5),
				PartPose.offsetAndRotation(-5.0f, -4.0f, 0.0f, -0.9599311f, 0.1047198f, 0.1919862f));
		armR1Def.addOrReplaceChild("arm_r2", CubeListBuilder.create().texOffs(76, 28).addBox(-3.0f, 9.5f, 3.5f, 4, 3, 3), PartPose.ZERO);
		armR1Def.addOrReplaceChild("arm_r3", CubeListBuilder.create().texOffs(76, 23).addBox(-3.0f, 6.5f, 3.5f, 4, 3, 2), PartPose.ZERO);

		root.addOrReplaceChild("shoulder_l", CubeListBuilder.create().texOffs(56, 35).mirror().addBox(-1.5f, -2.5f, -2.5f, 5, 5, 5),
				PartPose.offsetAndRotation(5.0f, -4.0f, 0.0f, -0.3665191f, -0.122173f, -0.0349066f));
		root.addOrReplaceChild("shoulderplate_ltop", CubeListBuilder.create().texOffs(110, 37).addBox(3.5f, -2.5f, -3.5f, 2, 1, 7),
				PartPose.offsetAndRotation(5.0f, -4.0f, 0.0f, -0.3665191f, -0.3141593f, -0.4363323f));
		root.addOrReplaceChild("shoulderplate_l1", CubeListBuilder.create().texOffs(110, 45).addBox(-4.5f, -1.5f, -3.5f, 1, 4, 7),
				PartPose.offsetAndRotation(-5.0f, -4.0f, 0.0f, -0.3665191f, 0.3141593f, 0.4363323f));
		root.addOrReplaceChild("shoulderplate_l2", CubeListBuilder.create().texOffs(94, 45).addBox(2.5f, 1.5f, -3.5f, 1, 3, 7),
				PartPose.offsetAndRotation(5.0f, -4.0f, 0.0f, -0.3665191f, -0.3141593f, -0.4363323f));
		root.addOrReplaceChild("shoulderplate_l3", CubeListBuilder.create().texOffs(94, 45).addBox(1.5f, 3.5f, -3.5f, 1, 3, 7),
				PartPose.offsetAndRotation(5.0f, -4.0f, 0.0f, -0.3665191f, -0.3141593f, -0.4363323f));

		root.addOrReplaceChild("legpanel_r4", CubeListBuilder.create().texOffs(0, 43).addBox(-3.0f, 0.5f, -3.5f, 2, 3, 1),
				PartPose.offsetAndRotation(-2.0f, 6.0f, 0.0f, -0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("legpanel_r5", CubeListBuilder.create().texOffs(0, 47).addBox(-3.0f, 2.5f, -2.5f, 2, 3, 1),
				PartPose.offsetAndRotation(-2.0f, 6.0f, 0.0f, -0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("legpanel_r6", CubeListBuilder.create().texOffs(6, 43).addBox(-3.0f, 4.5f, -1.5f, 2, 3, 1),
				PartPose.offsetAndRotation(-2.0f, 6.0f, 0.0f, -0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("backpanel_r1", CubeListBuilder.create().texOffs(0, 18).addBox(-3.0f, 0.5f, 2.5f, 5, 3, 1),
				PartPose.offsetAndRotation(-2.0f, 6.0f, 0.0f, 0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("backpanel_r2", CubeListBuilder.create().texOffs(0, 18).addBox(-3.0f, 2.5f, 1.5f, 5, 3, 1),
				PartPose.offsetAndRotation(-2.0f, 6.0f, 0.0f, 0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("backpanel_r3", CubeListBuilder.create().texOffs(0, 18).addBox(-3.0f, 4.5f, 0.5f, 5, 3, 1),
				PartPose.offsetAndRotation(-2.0f, 6.0f, 0.0f, 0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("backpanel_l3", CubeListBuilder.create().texOffs(0, 18).addBox(-2.0f, 4.5f, 0.5f, 5, 3, 1),
				PartPose.offsetAndRotation(2.0f, 6.0f, 0.0f, 0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("legpanel_l4", CubeListBuilder.create().texOffs(0, 43).addBox(1.0f, 0.5f, -3.5f, 2, 3, 1),
				PartPose.offsetAndRotation(2.0f, 6.0f, 0.0f, -0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("legpanel_l5", CubeListBuilder.create().texOffs(0, 47).addBox(1.0f, 2.5f, -2.5f, 2, 3, 1),
				PartPose.offsetAndRotation(2.0f, 6.0f, 0.0f, -0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("legpanel_l6", CubeListBuilder.create().texOffs(6, 43).addBox(1.0f, 4.5f, -1.5f, 2, 3, 1),
				PartPose.offsetAndRotation(2.0f, 6.0f, 0.0f, -0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("backpanel_l1", CubeListBuilder.create().texOffs(0, 18).addBox(-2.0f, 0.5f, 2.5f, 5, 3, 1),
				PartPose.offsetAndRotation(2.0f, 6.0f, 0.0f, 0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("backpanel_l2", CubeListBuilder.create().texOffs(0, 18).addBox(-2.0f, 2.5f, 1.5f, 5, 3, 1),
				PartPose.offsetAndRotation(2.0f, 6.0f, 0.0f, 0.4363323f, 0.0f, 0.0f));
		root.addOrReplaceChild("sidepanel_l1", CubeListBuilder.create().texOffs(0, 22).addBox(1.5f, 0.5f, -2.5f, 1, 4, 5),
				PartPose.offsetAndRotation(2.0f, 6.0f, 0.0f, 0.0f, 0.0f, -0.4363323f));
		root.addOrReplaceChild("sidepanel_r1", CubeListBuilder.create().texOffs(0, 22).addBox(-2.5f, 0.5f, -2.5f, 1, 4, 5),
				PartPose.offsetAndRotation(-2.0f, 6.0f, 0.0f, 0.0f, 0.0f, 0.4363323f));

		PartDefinition sidepanelR2Def = root.addOrReplaceChild("sidepanel_r2", CubeListBuilder.create().texOffs(0, 54).addBox(0.0f, 0.0f, -0.5f, 1, 5, 5),
				PartPose.offsetAndRotation(-4.5f, 9.5f, -2.0f, 0.0f, 0.0f, 0.122173f));
		PartDefinition sidepanelR3Def = sidepanelR2Def.addOrReplaceChild("sidepanel_r3", CubeListBuilder.create().texOffs(0, 35).addBox(0.0f, 0.0f, -0.5f, 1, 3, 5),
				PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.296706f));
		sidepanelR3Def.addOrReplaceChild("sidepanel_r4", CubeListBuilder.create().texOffs(24, 35).addBox(0.0f, 0.0f, -0.5f, 1, 3, 5),
				PartPose.offsetAndRotation(0.0f, 3.0f, 0.0f, 0.0f, 0.0f, 0.5235988f));

		PartDefinition sidepanelL2Def = root.addOrReplaceChild("sidepanel_l2", CubeListBuilder.create().texOffs(0, 54).addBox(0.0f, 0.0f, -0.5f, 1, 5, 5),
				PartPose.offsetAndRotation(4.5f, 9.5f, -2.0f, 0.0f, 0.0f, -0.122173f));
		PartDefinition sidepanelL3Def = sidepanelL2Def.addOrReplaceChild("sidepanel_l3", CubeListBuilder.create().texOffs(0, 35).addBox(0.0f, 0.0f, -0.5f, 1, 3, 5),
				PartPose.offsetAndRotation(0.0f, 5.0f, 0.0f, 0.0f, 0.0f, -0.296706f));
		sidepanelL3Def.addOrReplaceChild("sidepanel_l4", CubeListBuilder.create().texOffs(24, 35).addBox(0.0f, 0.0f, -0.5f, 1, 3, 5),
				PartPose.offsetAndRotation(0.0f, 3.0f, 0.0f, 0.0f, 0.0f, -0.5235988f));

		PartDefinition legpanelC1Def = root.addOrReplaceChild("legpanel_c1", CubeListBuilder.create().texOffs(16, 45).addBox(-3.0f, 0.0f, -0.5f, 6, 8, 1),
				PartPose.offset(0.0f, 5.5f, -3.0f));
		PartDefinition legpanelC2Def = legpanelC1Def.addOrReplaceChild("legpanel_c2", CubeListBuilder.create().texOffs(16, 54).addBox(-3.0f, 0.0f, -0.5f, 6, 4, 1),
				PartPose.offset(0.0f, 8.0f, 0.0f));
		legpanelC2Def.addOrReplaceChild("legpanel_c3", CubeListBuilder.create().texOffs(32, 59).addBox(-3.0f, 0.0f, -0.5f, 6, 4, 1),
				PartPose.offset(0.0f, 4.0f, 0.0f));

		PartDefinition cloak1Def = root.addOrReplaceChild("cloak1", CubeListBuilder.create().texOffs(106, 0).addBox(0.0f, 0.0f, -0.5f, 10, 18, 1),
				PartPose.offset(-5.0f, -6.0f, 4.0f));
		PartDefinition cloak2Def = cloak1Def.addOrReplaceChild("cloak2", CubeListBuilder.create().texOffs(106, 19).addBox(0.0f, 0.0f, -0.5f, 10, 4, 1),
				PartPose.offset(0.0f, 18.0f, 0.0f));
		cloak2Def.addOrReplaceChild("cloak3", CubeListBuilder.create().texOffs(106, 24).addBox(0.0f, 0.0f, -0.5f, 10, 4, 1),
				PartPose.offset(0.0f, 4.0f, 0.0f));

		ModelPart baked = LayerDefinition.create(mesh, 128, 64).bakeRoot();
		beltR = baked.getChild("belt_r");
		mbelt = baked.getChild("mbelt");
		mbeltL = baked.getChild("mbelt_l");
		mbeltR = baked.getChild("mbelt_r");
		beltL = baked.getChild("belt_l");
		chestplate = baked.getChild("chestplate");
		hoodEye = baked.getChild("hood_eye");
		hood1 = baked.getChild("hood1");
		hood2 = hood1.getChild("hood2");
		hood3 = hood1.getChild("hood3");
		hood4 = hood1.getChild("hood4");
		backplate = baked.getChild("backplate");
		shoulderplateTopR = baked.getChild("shoulderplate_top_r");
		shoulderplateR1 = baked.getChild("shoulderplate_r1");
		shoulderplateR2 = baked.getChild("shoulderplate_r2");
		shoulderplateR3 = baked.getChild("shoulderplate_r3");
		shoulderR = baked.getChild("shoulder_r");
		armL1 = baked.getChild("arm_l1");
		armL2 = armL1.getChild("arm_l2");
		armL3 = armL1.getChild("arm_l3");
		armR1 = baked.getChild("arm_r1");
		armR2 = armR1.getChild("arm_r2");
		armR3 = armR1.getChild("arm_r3");
		shoulderL = baked.getChild("shoulder_l");
		shoulderplateLtop = baked.getChild("shoulderplate_ltop");
		shoulderplateL1 = baked.getChild("shoulderplate_l1");
		shoulderplateL2 = baked.getChild("shoulderplate_l2");
		shoulderplateL3 = baked.getChild("shoulderplate_l3");
		legpanelR4 = baked.getChild("legpanel_r4");
		legpanelR5 = baked.getChild("legpanel_r5");
		legpanelR6 = baked.getChild("legpanel_r6");
		backpanelR1 = baked.getChild("backpanel_r1");
		backpanelR2 = baked.getChild("backpanel_r2");
		backpanelR3 = baked.getChild("backpanel_r3");
		backpanelL3 = baked.getChild("backpanel_l3");
		legpanelL4 = baked.getChild("legpanel_l4");
		legpanelL5 = baked.getChild("legpanel_l5");
		legpanelL6 = baked.getChild("legpanel_l6");
		backpanelL1 = baked.getChild("backpanel_l1");
		backpanelL2 = baked.getChild("backpanel_l2");
		sidepanelL1 = baked.getChild("sidepanel_l1");
		sidepanelR1 = baked.getChild("sidepanel_r1");
		sidepanelR2 = baked.getChild("sidepanel_r2");
		sidepanelR3 = sidepanelR2.getChild("sidepanel_r3");
		sidepanelR4 = sidepanelR3.getChild("sidepanel_r4");
		sidepanelL2 = baked.getChild("sidepanel_l2");
		sidepanelL3 = sidepanelL2.getChild("sidepanel_l3");
		sidepanelL4 = sidepanelL3.getChild("sidepanel_l4");
		legpanelC1 = baked.getChild("legpanel_c1");
		legpanelC2 = legpanelC1.getChild("legpanel_c2");
		legpanelC3 = legpanelC2.getChild("legpanel_c3");
		cloak1 = baked.getChild("cloak1");
		cloak2 = cloak1.getChild("cloak2");
		cloak3 = cloak2.getChild("cloak3");
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		isWarden = entity instanceof EntityEldritchWarden;

		// Parts the true original only ever rotated once at construction time - reasserted every frame here
		// since this model has no automatic per-frame pose reset (same real defensive convention already
		// established for ModelEldritchCrab).
		setRotation(beltR, 0.0f, 0.0f, 0.0f);
		setRotation(mbelt, 0.0f, 0.0f, 0.0f);
		setRotation(mbeltL, 0.0f, 0.0f, 0.0f);
		setRotation(mbeltR, 0.0f, 0.0f, 0.0f);
		setRotation(beltL, 0.0f, 0.0f, 0.0f);
		setRotation(chestplate, 0.0f, 0.0f, 0.0f);
		setRotation(backplate, 0.0f, 0.0f, 0.0f);
		setRotation(shoulderplateTopR, -0.3665191f, 0.3141593f, 0.4363323f);
		setRotation(shoulderplateR1, -0.3665191f, -0.3141593f, -0.4363323f);
		setRotation(shoulderplateR2, -0.3665191f, 0.3141593f, 0.4363323f);
		setRotation(shoulderplateR3, -0.3665191f, 0.3141593f, 0.4363323f);
		setRotation(shoulderR, -0.3665191f, 0.122173f, 0.0349066f);
		setRotation(armL2, 0.0f, 0.0f, 0.0f);
		setRotation(armL3, 0.0f, 0.0f, 0.0f);
		setRotation(armR2, 0.0f, 0.0f, 0.0f);
		setRotation(armR3, 0.0f, 0.0f, 0.0f);
		setRotation(shoulderL, -0.3665191f, -0.122173f, -0.0349066f);
		setRotation(shoulderplateLtop, -0.3665191f, -0.3141593f, -0.4363323f);
		setRotation(shoulderplateL1, -0.3665191f, 0.3141593f, 0.4363323f);
		setRotation(shoulderplateL2, -0.3665191f, -0.3141593f, -0.4363323f);
		setRotation(shoulderplateL3, -0.3665191f, -0.3141593f, -0.4363323f);
		setRotation(legpanelR4, -0.4363323f, 0.0f, 0.0f);
		setRotation(legpanelR5, -0.4363323f, 0.0f, 0.0f);
		setRotation(legpanelR6, -0.4363323f, 0.0f, 0.0f);
		setRotation(backpanelR1, 0.4363323f, 0.0f, 0.0f);
		setRotation(backpanelR2, 0.4363323f, 0.0f, 0.0f);
		setRotation(backpanelR3, 0.4363323f, 0.0f, 0.0f);
		setRotation(backpanelL3, 0.4363323f, 0.0f, 0.0f);
		setRotation(legpanelL4, -0.4363323f, 0.0f, 0.0f);
		setRotation(legpanelL5, -0.4363323f, 0.0f, 0.0f);
		setRotation(legpanelL6, -0.4363323f, 0.0f, 0.0f);
		setRotation(backpanelL1, 0.4363323f, 0.0f, 0.0f);
		setRotation(backpanelL2, 0.4363323f, 0.0f, 0.0f);
		setRotation(sidepanelL1, 0.0f, 0.0f, -0.4363323f);
		setRotation(sidepanelR1, 0.0f, 0.0f, 0.4363323f);
		setRotation(hood2, -0.2268928f, 0.0f, 0.0f);
		setRotation(hood3, -0.3490659f, 0.0f, 0.0f);
		setRotation(hood4, -0.5759587f, 0.0f, 0.0f);

		// Real head look - Hood1's own rotation, HoodEye copies it exactly (matching the true original).
		hood1.yRot = netHeadYaw / Mth.RAD_TO_DEG;
		hood1.xRot = headPitch / Mth.RAD_TO_DEG;
		hoodEye.xRot = hood1.xRot;
		hoodEye.yRot = hood1.yRot;

		// Real arm-lift (fired by a real entity-event on ranged-attack orb release, decayed client-side) plus
		// the real constant idle sway both arms always have.
		float armLiftR = 0.0f;
		float armLiftL = 0.0f;
		if (entity instanceof EntityEldritchGuardian guardian) {
			armLiftR = guardian.getArmLiftR();
			armLiftL = guardian.getArmLiftL();
		} else if (entity instanceof EntityEldritchWarden warden) {
			armLiftR = warden.getArmLiftR();
			armLiftL = warden.getArmLiftL();
		}
		armL1.setRotation(-1.0f - armLiftL + Mth.sin((ageInTicks + 20.0f) / 10.0f) * 0.08f, -0.1047198f, -0.1919862f);
		armR1.setRotation(-1.0f - armLiftR + Mth.sin(ageInTicks / 10.0f) * 0.08f, 0.1047198f, 0.1919862f);

		// Real leg-panel, cloak, and side-panel sway - all driven off the same real interpolated age.
		legpanelC1.setRotation(-0.15f + Mth.sin(ageInTicks / 8.0f) * 0.12f, 0.0f, 0.0f);
		legpanelC2.setRotation(Mth.sin((ageInTicks - 5.0f) / 8.0f) * 0.13f, 0.0f, 0.0f);
		legpanelC3.setRotation(Mth.sin((ageInTicks - 10.0f) / 8.0f) * 0.14f, 0.0f, 0.0f);
		cloak1.setRotation(0.2f + Mth.sin(ageInTicks / 7.0f) * 0.08f, 0.0f, 0.0f);
		cloak2.setRotation(Mth.sin((ageInTicks - 5.0f) / 7.0f) * 0.1f, 0.0f, 0.0f);
		cloak3.setRotation(Mth.sin((ageInTicks - 10.0f) / 7.0f) * 0.12f, 0.0f, 0.0f);
		sidepanelL2.setRotation(0.0f, 0.0f, -0.2f + Mth.sin((ageInTicks + 10.0f) / 8.0f) * 0.12f);
		sidepanelL3.setRotation(0.0f, 0.0f, Mth.sin((ageInTicks + 5.0f) / 8.0f) * 0.13f);
		sidepanelL4.setRotation(0.0f, 0.0f, Mth.sin(ageInTicks / 8.0f) * 0.14f);
		sidepanelR2.setRotation(0.0f, 0.0f, 0.2f + Mth.sin((ageInTicks - 5.0f) / 8.0f) * 0.12f);
		sidepanelR3.setRotation(0.0f, 0.0f, Mth.sin((ageInTicks - 10.0f) / 8.0f) * 0.13f);
		sidepanelR4.setRotation(0.0f, 0.0f, Mth.sin((ageInTicks - 15.0f) / 8.0f) * 0.14f);

		// Real pulsing eye glow (Warden only) - see the class doc comment for the real single-pass
		// simplification vs. the true original's own real second additive draw call.
		if (isWarden) {
			float pulse = 195.0f + Mth.sin(entity.tickCount / 3.0f) * 15.0f + 15.0f;
			int blockLight = Mth.clamp(Math.round((pulse - 8.0f) / 16.0f), 0, 15);
			hoodEyeLight = LightTexture.pack(blockLight, 0);
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		if (alpha < 1.0f) {
			color = net.minecraft.util.FastColor.ARGB32.color((int) (net.minecraft.util.FastColor.ARGB32.alpha(color) * alpha), color);
		}
		beltR.render(poseStack, buffer, packedLight, packedOverlay, color);
		mbelt.render(poseStack, buffer, packedLight, packedOverlay, color);
		mbeltL.render(poseStack, buffer, packedLight, packedOverlay, color);
		mbeltR.render(poseStack, buffer, packedLight, packedOverlay, color);
		beltL.render(poseStack, buffer, packedLight, packedOverlay, color);
		chestplate.render(poseStack, buffer, packedLight, packedOverlay, color);
		hood1.render(poseStack, buffer, packedLight, packedOverlay, color);
		backplate.render(poseStack, buffer, packedLight, packedOverlay, color);
		shoulderplateTopR.render(poseStack, buffer, packedLight, packedOverlay, color);
		shoulderplateR1.render(poseStack, buffer, packedLight, packedOverlay, color);
		shoulderplateR2.render(poseStack, buffer, packedLight, packedOverlay, color);
		shoulderplateR3.render(poseStack, buffer, packedLight, packedOverlay, color);
		shoulderR.render(poseStack, buffer, packedLight, packedOverlay, color);
		armL1.render(poseStack, buffer, packedLight, packedOverlay, color);
		armR1.render(poseStack, buffer, packedLight, packedOverlay, color);
		shoulderL.render(poseStack, buffer, packedLight, packedOverlay, color);
		shoulderplateLtop.render(poseStack, buffer, packedLight, packedOverlay, color);
		shoulderplateL1.render(poseStack, buffer, packedLight, packedOverlay, color);
		shoulderplateL2.render(poseStack, buffer, packedLight, packedOverlay, color);
		shoulderplateL3.render(poseStack, buffer, packedLight, packedOverlay, color);
		legpanelR4.render(poseStack, buffer, packedLight, packedOverlay, color);
		legpanelR5.render(poseStack, buffer, packedLight, packedOverlay, color);
		legpanelR6.render(poseStack, buffer, packedLight, packedOverlay, color);
		backpanelR1.render(poseStack, buffer, packedLight, packedOverlay, color);
		backpanelR2.render(poseStack, buffer, packedLight, packedOverlay, color);
		backpanelR3.render(poseStack, buffer, packedLight, packedOverlay, color);
		backpanelL3.render(poseStack, buffer, packedLight, packedOverlay, color);
		legpanelL4.render(poseStack, buffer, packedLight, packedOverlay, color);
		legpanelL5.render(poseStack, buffer, packedLight, packedOverlay, color);
		legpanelL6.render(poseStack, buffer, packedLight, packedOverlay, color);
		backpanelL1.render(poseStack, buffer, packedLight, packedOverlay, color);
		backpanelL2.render(poseStack, buffer, packedLight, packedOverlay, color);
		cloak1.render(poseStack, buffer, packedLight, packedOverlay, color);
		sidepanelR1.render(poseStack, buffer, packedLight, packedOverlay, color);
		sidepanelL1.render(poseStack, buffer, packedLight, packedOverlay, color);
		sidepanelL2.render(poseStack, buffer, packedLight, packedOverlay, color);
		sidepanelR2.render(poseStack, buffer, packedLight, packedOverlay, color);
		legpanelC1.render(poseStack, buffer, packedLight, packedOverlay, color);

		if (isWarden) {
			poseStack.pushPose();
			poseStack.scale(1.01f, 1.01f, 1.01f);
			hoodEye.render(poseStack, buffer, hoodEyeLight, packedOverlay, color);
			poseStack.popPose();
		}
	}

	private static void setRotation(ModelPart part, float x, float y, float z) {
		part.setRotation(x, y, z);
	}
}

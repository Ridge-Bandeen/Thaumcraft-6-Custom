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
import net.minecraft.world.entity.Mob;
import thaumcraft.common.entities.monster.EntityFireBat;

/**
 * Real, faithful geometry+animation translation of the original's own {@code ModelFireBat} - a plain
 * {@code ModelBase}/{@code ModelRenderer} hierarchy (head with two ears, body with two wings each carrying
 * an outer-wing segment) built here via the modern {@link LayerDefinition}/{@link CubeListBuilder} builder
 * system, same real box coordinates/texture offsets. Shared by both {@link
 * thaumcraft.common.entities.monster.EntityFireBat} and {@link thaumcraft.common.entities.monster.EntitySpellBat}
 * (real, same reuse the original itself already did - one model file, two renderers), so this is typed to
 * plain {@link Mob} rather than either specific class; only the real {@code isHanging()}-driven pose branch
 * is guarded by a real {@code instanceof EntityFireBat} check, matching the original's own exact same guard
 * (spell bats never hang, same as the original).
 *
 * Real two-pose {@link #setupAnim}: a real "hanging from the ceiling" pose (head flipped upside-down toward
 * the surface, wings folded in) when a real fire bat is really hanging, otherwise a real wing-flap/body-bob
 * flying animation - both faithfully ported from the original's own exact trig formulas.
 */
public class ModelFireBat<T extends Mob> extends EntityModel<T> {

	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightWing;
	private final ModelPart leftWing;
	private final ModelPart outerRightWing;
	private final ModelPart outerLeftWing;

	public ModelFireBat() {
		super(RenderType::entityCutout);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition headDef = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -3.0f, -3.0f, 6, 6, 6), PartPose.ZERO);
		headDef.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(24, 0).addBox(-4.0f, -6.0f, -2.0f, 3, 4, 1), PartPose.ZERO);
		headDef.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(24, 0).mirror().addBox(1.0f, -6.0f, -2.0f, 3, 4, 1), PartPose.ZERO);

		PartDefinition bodyDef = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 16).addBox(-3.0f, 4.0f, -3.0f, 6, 12, 6)
				.texOffs(0, 34).addBox(-5.0f, 16.0f, 0.0f, 10, 6, 1), PartPose.ZERO);
		PartDefinition rightWingDef = bodyDef.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(42, 0).addBox(-12.0f, 1.0f, 1.5f, 10, 16, 1), PartPose.ZERO);
		rightWingDef.addOrReplaceChild("outer_right_wing", CubeListBuilder.create().texOffs(24, 16).addBox(-8.0f, 1.0f, 0.0f, 8, 12, 1), PartPose.offset(-12.0f, 1.0f, 1.5f));
		PartDefinition leftWingDef = bodyDef.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(42, 0).mirror().addBox(2.0f, 1.0f, 1.5f, 10, 16, 1), PartPose.ZERO);
		leftWingDef.addOrReplaceChild("outer_left_wing", CubeListBuilder.create().texOffs(24, 16).mirror().addBox(0.0f, 1.0f, 0.0f, 8, 12, 1), PartPose.offset(12.0f, 1.0f, 1.5f));

		ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
		head = baked.getChild("head");
		body = baked.getChild("body");
		rightWing = body.getChild("right_wing");
		leftWing = body.getChild("left_wing");
		outerRightWing = rightWing.getChild("outer_right_wing");
		outerLeftWing = leftWing.getChild("outer_left_wing");
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		if (entity instanceof EntityFireBat fireBat && fireBat.isHanging()) {
			head.xRot = headPitch / Mth.RAD_TO_DEG;
			head.yRot = Mth.PI - netHeadYaw / Mth.RAD_TO_DEG;
			head.zRot = Mth.PI;
			head.setPos(0.0f, -2.0f, 0.0f);
			rightWing.setPos(-3.0f, 0.0f, 3.0f);
			leftWing.setPos(3.0f, 0.0f, 3.0f);
			body.xRot = Mth.PI;
			rightWing.xRot = -0.15707964f;
			rightWing.yRot = -1.2566371f;
			outerRightWing.yRot = -1.7278761f;
			leftWing.xRot = rightWing.xRot;
			leftWing.yRot = -rightWing.yRot;
			outerLeftWing.yRot = -outerRightWing.yRot;
		} else {
			head.xRot = headPitch / Mth.RAD_TO_DEG;
			head.yRot = netHeadYaw / Mth.RAD_TO_DEG;
			head.zRot = 0.0f;
			head.setPos(0.0f, 0.0f, 0.0f);
			rightWing.setPos(0.0f, 0.0f, 0.0f);
			leftWing.setPos(0.0f, 0.0f, 0.0f);
			body.xRot = 0.7853982f + Mth.cos(ageInTicks * 0.1f) * 0.15f;
			body.yRot = 0.0f;
			rightWing.yRot = Mth.cos(ageInTicks * 1.3f) * Mth.PI * 0.25f;
			leftWing.yRot = -rightWing.yRot;
			outerRightWing.yRot = rightWing.yRot * 0.5f;
			outerLeftWing.yRot = -rightWing.yRot * 0.5f;
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		head.render(poseStack, buffer, packedLight, packedOverlay, color);
		body.render(poseStack, buffer, packedLight, packedOverlay, color);
	}
}

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
import thaumcraft.common.entities.construct.EntityArcaneBore;

/**
 * Replaces the old (1.12.2) {@code ModelArcaneBore} - a real, faithful geometry translation via the modern
 * {@link LayerDefinition}/{@link CubeListBuilder} builder system, same real box coordinates/texture offsets/
 * rotation points. Shares its own real tripod/leg shape with {@link ModelCrossbow} (the original's own two
 * models really did reuse the exact same 4-leg/tripod geometry) - kept as its own separate copy rather than
 * factored out, matching how the original itself never shared a class between the two either.
 *
 * <p>Real {@link #setupAnim} head tracking - same real net-head-yaw/head-pitch convention as {@link
 * ModelCrossbow#setupAnim}, see that class's own doc comment for why the original's own 4th/5th parameter
 * names there are misleading rather than actually swapped.
 *
 * <p>Real, documented cut: the original's own {@code RenderArcaneBore} drew a real animated mining-beam
 * VFX (a hand-tessellated swirling particle billboard plus a 3-blade rotating beam quad fan) directly with
 * raw immediate-mode {@code Tessellator} calls on top of this model - a genuine custom vertex-buffer effect,
 * not a model part, and not a quick port to the modern {@link VertexConsumer}/{@link RenderType} pipeline.
 * Dropped for now, same real "bespoke Tessellator FX, real separate follow-up" reasoning {@code EntityGrapple}'s
 * own tether-rope visual already carries - the real static geometry (tripod/legs/rotating head/crystal/dome)
 * still renders correctly without it.
 */
public class ModelArcaneBore extends EntityModel<EntityArcaneBore> {

	private final ModelPart base;
	private final ModelPart tripod;
	private final ModelPart leg1;
	private final ModelPart leg2;
	private final ModelPart leg3;
	private final ModelPart leg4;

	public ModelArcaneBore() {
		super(RenderType::entityCutout);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition baseDef = root.addOrReplaceChild("base",
				CubeListBuilder.create().texOffs(32, 0).addBox(-3.0f, -6.0f, -3.0f, 6, 6, 6),
				PartPose.offset(0.0f, 13.0f, 0.0f));
		baseDef.addOrReplaceChild("crystal",
				CubeListBuilder.create().texOffs(32, 25).addBox(-1.0f, -4.0f, 5.0f, 2, 2, 2), PartPose.ZERO);
		baseDef.addOrReplaceChild("dome",
				CubeListBuilder.create().texOffs(44, 16).addBox(-2.0f, -5.0f, 4.0f, 4, 4, 4), PartPose.ZERO);
		baseDef.addOrReplaceChild("domebase",
				CubeListBuilder.create().texOffs(32, 19).addBox(-2.0f, -5.0f, 3.0f, 4, 4, 1), PartPose.ZERO);
		baseDef.addOrReplaceChild("magbase",
				CubeListBuilder.create().texOffs(0, 18).mirror().addBox(-1.0f, -4.0f, -6.0f, 2, 2, 3), PartPose.ZERO);
		baseDef.addOrReplaceChild("tip",
				CubeListBuilder.create().texOffs(0, 9).mirror().addBox(-1.5f, 0.0f, -1.5f, 3, 3, 3),
				PartPose.offsetAndRotation(0.0f, -3.0f, -6.0f, -1.570796f, 0.0f, 0.0f));

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
		base = baked.getChild("base");
		tripod = baked.getChild("tripod");
		leg1 = baked.getChild("leg1");
		leg2 = baked.getChild("leg2");
		leg3 = baked.getChild("leg3");
		leg4 = baked.getChild("leg4");
	}

	@Override
	public void setupAnim(EntityArcaneBore entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		base.yRot = netHeadYaw / Mth.RAD_TO_DEG;
		base.xRot = headPitch / Mth.RAD_TO_DEG;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		leg1.render(poseStack, buffer, packedLight, packedOverlay, color);
		leg2.render(poseStack, buffer, packedLight, packedOverlay, color);
		leg3.render(poseStack, buffer, packedLight, packedOverlay, color);
		leg4.render(poseStack, buffer, packedLight, packedOverlay, color);
		tripod.render(poseStack, buffer, packedLight, packedOverlay, color);
		base.render(poseStack, buffer, packedLight, packedOverlay, color);
	}
}

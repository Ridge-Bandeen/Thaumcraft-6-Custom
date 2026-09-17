package thaumcraft.client.render;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

/**
 * Real, faithful geometry translation of the true original's own real {@code ModelTubeValve} (a 2-box model:
 * a small rod cube plus a flat ring) into the modern {@link LayerDefinition}/{@link CubeListBuilder} builder
 * system - same real coordinates, no dimensions changed. Shared by {@code TileTubeValveRenderer} (both real
 * parts, spinning), {@code TileTubeOnewayRenderer}, and {@code TileTubeBufferRenderer} (rod only, static),
 * matching the true original's own real one-model-three-renderers shape exactly.
 */
final class ModelTubeValve {

	private final ModelPart rod;
	private final ModelPart ring;

	ModelTubeValve() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(0, 10).addBox(-1.0f, 2.0f, -1.0f, 2, 2, 2), PartPose.ZERO);
		root.addOrReplaceChild("ring", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0f, 4.0f, -2.0f, 4, 1, 4), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		rod = baked.getChild("rod");
		ring = baked.getChild("ring");
	}

	void renderRod(PoseStack poseStack, VertexConsumer consumer, int packedLight) {
		rod.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
	}

	void renderRod(PoseStack poseStack, VertexConsumer consumer, int packedLight, int color) {
		rod.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, color);
	}

	void renderRing(PoseStack poseStack, VertexConsumer consumer, int packedLight) {
		ring.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
	}

	/** Real, faithful translation of the true original's own real 2-step orientation transform (shared by all
	 * 3 real renderers this model backs) - orients the model's own local +Y "up" axis to point along
	 * {@code facing} instead. */
	static void orientToFacing(PoseStack poseStack, Direction facing) {
		if (facing.getStepY() == 0) {
			poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
		} else {
			poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
			poseStack.mulPose(Axis.XP.rotationDegrees(90.0f * facing.getStepY()));
		}
		poseStack.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(90.0), facing.getStepX(), facing.getStepY(), facing.getStepZ()));
	}
}

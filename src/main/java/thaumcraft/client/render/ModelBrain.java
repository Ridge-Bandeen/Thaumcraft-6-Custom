package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Real, faithful geometry translation of the true original's own real {@code ModelBrain} (a 3-box "Brain in a
 * Jar" mesh) into the modern {@link LayerDefinition}/{@link CubeListBuilder} builder system - same real
 * coordinates, including {@code Shape3}'s own real fixed X-axis tilt (baked into its own {@link PartPose}
 * here rather than applied at render time, since it never changes).
 */
final class ModelBrain {

	private final ModelPart shape1;
	private final ModelPart shape2;
	private final ModelPart shape3;

	ModelBrain() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("shape1", CubeListBuilder.create().texOffs(0, 0).addBox(0.0f, 0.0f, 0.0f, 12, 10, 16), PartPose.offset(-6.0f, 8.0f, -8.0f));
		root.addOrReplaceChild("shape2", CubeListBuilder.create().texOffs(64, 0).addBox(0.0f, 0.0f, 0.0f, 8, 3, 7), PartPose.offset(-4.0f, 18.0f, 0.0f));
		root.addOrReplaceChild("shape3", CubeListBuilder.create().texOffs(0, 32).addBox(0.0f, 0.0f, 0.0f, 2, 6, 2),
				PartPose.offsetAndRotation(-1.0f, 18.0f, -2.0f, 0.4089647f, 0.0f, 0.0f));
		ModelPart baked = LayerDefinition.create(mesh, 128, 64).bakeRoot();
		shape1 = baked.getChild("shape1");
		shape2 = baked.getChild("shape2");
		shape3 = baked.getChild("shape3");
	}

	void render(PoseStack poseStack, VertexConsumer consumer, int packedLight) {
		shape1.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		shape2.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		shape3.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
	}
}

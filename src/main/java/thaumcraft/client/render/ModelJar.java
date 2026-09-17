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
 * Real, faithful geometry translation of the true original's own real {@code ModelJar} into the modern
 * {@link LayerDefinition}/{@link CubeListBuilder} builder system - same real coordinates, no dimensions
 * changed. The true original's own {@code Core} box is declared but never actually rendered by its own real
 * {@code TileJarRenderer} (dead weight, same shape as {@code ModelBellows}'s own unused parts elsewhere in
 * this port) - not ported here either.
 */
final class ModelJar {

	private final ModelPart lid;
	private final ModelPart lidExtension;
	private final ModelPart brine;

	ModelJar() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(32, 24).addBox(-3.0f, 0.0f, -3.0f, 6, 2, 6), PartPose.offset(0.0f, -14.0f, 0.0f));
		root.addOrReplaceChild("lidExtension", CubeListBuilder.create().texOffs(0, 23).addBox(-2.0f, -16.0f, -2.0f, 4, 2, 4), PartPose.ZERO);
		root.addOrReplaceChild("brine", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -11.0f, -4.0f, 8, 10, 8), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		lid = baked.getChild("lid");
		lidExtension = baked.getChild("lidExtension");
		brine = baked.getChild("brine");
	}

	void renderLidBrace(PoseStack poseStack, VertexConsumer consumer, int packedLight) {
		lid.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
	}

	void renderLidExtension(PoseStack poseStack, VertexConsumer consumer, int packedLight) {
		lidExtension.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
	}

	void renderBrine(PoseStack poseStack, VertexConsumer consumer, int packedLight) {
		brine.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
	}
}

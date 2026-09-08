package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.items.IScribeTools;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/**
 * Renders {@link TileResearchTable}'s own real inkwell (visible whenever a real scribing tool is inserted -
 * shows the real inserted item floating above it, a real modern substitute for the original's own hardcoded
 * "quill" 2D icon, adapting to whatever real {@link IScribeTools} item is actually present) and scroll (
 * visible whenever a real theory is in progress, its ribbon tinted the real {@link Aspect#ALCHEMY} color,
 * matching the original exactly) - real geometry translation of the original's own {@code
 * ModelResearchTable} (a plain 3-part cuboid model), built here via the modern {@link LayerDefinition}/
 * {@link CubeListBuilder} cuboid system, same real box coordinates. No facing rotation needed - the real
 * modern {@code BlockResearchTable} is non-directional (see its own doc comment).
 */
public class TileResearchTableRenderer implements BlockEntityRenderer<TileResearchTable> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/blocks/research_table_model.png");

	private final ModelPart inkwell;
	private final ModelPart scrollTube;
	private final ModelPart scrollRibbon;

	public TileResearchTableRenderer(BlockEntityRendererProvider.Context context) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("inkwell", CubeListBuilder.create().texOffs(0, 16).addBox(0.0f, 0.0f, 0.0f, 3, 2, 3),
				PartPose.offsetAndRotation(-6.0f, -2.0f, 3.0f, 0, 0, 0));
		root.addOrReplaceChild("scrollTube", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0f, -0.5f, 0.0f, 8, 2, 2),
				PartPose.offsetAndRotation(-2.0f, -2.0f, 2.0f, 0, (float) Math.toRadians(10.0), 0));
		root.addOrReplaceChild("scrollRibbon", CubeListBuilder.create().texOffs(0, 4).addBox(-4.25f, -0.275f, 0.0f, 1, 2, 2),
				PartPose.offsetAndRotation(-2.0f, -2.0f, 2.0f, 0, (float) Math.toRadians(10.0), 0));
		ModelPart baked = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		inkwell = baked.getChild("inkwell");
		scrollTube = baked.getChild("scrollTube");
		scrollRibbon = baked.getChild("scrollRibbon");
	}

	@Override
	public void render(TileResearchTable tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		poseStack.pushPose();
		poseStack.translate(0.5, 1.0, 0.5);
		poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0f));

		var vertexConsumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
		if (tile.data != null) {
			scrollTube.render(poseStack, vertexConsumer, packedLight, packedOverlay);
			poseStack.pushPose();
			poseStack.scale(1.2f, 1.2f, 1.2f);
			scrollRibbon.render(poseStack, vertexConsumer, packedLight, packedOverlay, 0xFF000000 | Aspect.ALCHEMY.getColor());
			poseStack.popPose();
		}

		ItemStack ink = tile.getInk();
		if (!ink.isEmpty() && ink.getItem() instanceof IScribeTools) {
			inkwell.render(poseStack, vertexConsumer, packedLight, packedOverlay);

			poseStack.pushPose();
			poseStack.translate(-0.5, 0.1, 0.125);
			poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(60.0f));
			poseStack.scale(0.5f, 0.5f, 0.5f);
			Minecraft.getInstance().getItemRenderer().renderStatic(ink, ItemDisplayContext.FIXED, packedLight, packedOverlay,
					poseStack, bufferSource, tile.getLevel(), 0);
			poseStack.popPose();
		}

		poseStack.popPose();
	}
}

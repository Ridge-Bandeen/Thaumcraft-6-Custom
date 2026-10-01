package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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
 * {@link CubeListBuilder} cuboid system, same real box coordinates, turned with the table's facing. The quill is
 * the original's own: research/quill.png drawn upright in the inkwell as a 1/16-thick extruded sprite (its
 * UtilsFX.renderItemIn2D), not the Scribing Tools item (which the port drew here, sideways off the table's edge).
 */
public class TileResearchTableRenderer implements BlockEntityRenderer<TileResearchTable> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/blocks/research_table_model.png");

	private static final ResourceLocation QUILL = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/quill.png");

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
		switch (tile.getBlockState().getOptionalValue(thaumcraft.common.blocks.crafting.BlockResearchTable.FACING)
				.orElse(net.minecraft.core.Direction.NORTH)) {
			case EAST -> poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0f));
			case WEST -> poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(270.0f));
			case SOUTH -> poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f));
			default -> { }
		}

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
			poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0f));
			poseStack.translate(-0.5, 0.1, 0.125);
			poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(60.0f));
			poseStack.scale(0.5f, 0.5f, 0.5f);
			renderSpriteIn3D(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(QUILL)), 16, 16, 0.0625f, packedLight, packedOverlay);
			poseStack.popPose();
		}

		poseStack.popPose();
	}

	/** The original's UtilsFX.renderTextureIn3D for a whole standalone texture: front and back faces (mirrored in u, as
	 * 1.12 drew them) spanning 0..1, and a 1-pixel strip of side faces per column and row, {@code thickness} deep. */
	private static void renderSpriteIn3D(PoseStack poseStack, VertexConsumer vc, int width, int height, float thickness, int light, int overlay) {
		PoseStack.Pose pose = poseStack.last();
		float maxu = 1f, minu = 0f, top = 0f, bottom = 1f;
		quad(vc, pose, light, overlay, 0, 0, 1,
				0, 0, 0, maxu, bottom, 1, 0, 0, minu, bottom, 1, 1, 0, minu, top, 0, 1, 0, maxu, top);
		quad(vc, pose, light, overlay, 0, 0, -1,
				0, 1, -thickness, maxu, top, 1, 1, -thickness, minu, top, 1, 0, -thickness, minu, bottom, 0, 0, -thickness, maxu, bottom);
		float du = 0.5f * (maxu - minu) / width;
		float dv = 0.5f * (bottom - top) / height;
		for (int k = 0; k < width; ++k) {
			float x0 = k / (float) width;
			float x1 = x0 + 1.0f / width;
			float u = maxu + (minu - maxu) * x0 - du;
			quad(vc, pose, light, overlay, -1, 0, 0,
					x0, 0, -thickness, u, bottom, x0, 0, 0, u, bottom, x0, 1, 0, u, top, x0, 1, -thickness, u, top);
			quad(vc, pose, light, overlay, 1, 0, 0,
					x1, 1, -thickness, u, top, x1, 1, 0, u, top, x1, 0, 0, u, bottom, x1, 0, -thickness, u, bottom);
		}
		for (int k = 0; k < height; ++k) {
			float y0 = k / (float) height;
			float y1 = y0 + 1.0f / height;
			float v = bottom + (top - bottom) * y0 - dv;
			quad(vc, pose, light, overlay, 0, 1, 0,
					0, y1, 0, maxu, v, 1, y1, 0, minu, v, 1, y1, -thickness, minu, v, 0, y1, -thickness, maxu, v);
			quad(vc, pose, light, overlay, 0, -1, 0,
					1, y0, 0, minu, v, 0, y0, 0, maxu, v, 0, y0, -thickness, maxu, v, 1, y0, -thickness, minu, v);
		}
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, int light, int overlay, float nx, float ny, float nz, float... v) {
		for (int i = 0; i < 4; i++) {
			int o = i * 5;
			vc.addVertex(pose, v[o], v[o + 1], v[o + 2]).setColor(-1).setUv(v[o + 3], v[o + 4]).setOverlay(overlay)
					.setLight(light).setNormal(pose, nx, ny, nz);
		}
	}
}

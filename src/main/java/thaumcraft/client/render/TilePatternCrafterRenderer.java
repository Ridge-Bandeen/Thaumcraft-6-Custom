package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.crafting.TilePatternCrafter;

/**
 * The Arcane Pattern Crafter's face, as the original {@code TilePatternCrafterRenderer}: turned by the facing (south 0,
 * north 180, east 90, west 270), the mode icon (frame {@link TilePatternCrafter#getPatternType()} of the ten on
 * {@code pattern_crafter_modes.png}, half a block across) on the face half a block out, turned a quarter, and two brass
 * gears ({@code gear_brass.png} extruded 0.1 deep, as the original's {@code renderTextureIn3D}) turning opposite ways by
 * {@link TilePatternCrafter#rot}, half a block across, below and to either side of the centre.
 */
public class TilePatternCrafterRenderer implements BlockEntityRenderer<TilePatternCrafter> {

	private static final ResourceLocation MODES = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/blocks/pattern_crafter_modes.png");
	private static final ResourceLocation GEAR = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/gear_brass.png");
	private static final int MODE_FRAMES = 10;

	public TilePatternCrafterRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TilePatternCrafter tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0.75, 0.5);
		switch (tile.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)) {
			case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
			case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(270.0f));
			case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
			default -> {
			}
		}

		// the mode icon: the original's renderQuadCentered(10, 1, type, 0.5)
		poseStack.pushPose();
		poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
		poseStack.translate(0.0, 0.0, -0.5);
		float u0 = (tile.getPatternType() % MODE_FRAMES) / (float) MODE_FRAMES;
		float u1 = u0 + 1.0f / MODE_FRAMES;
		VertexConsumer icon = bufferSource.getBuffer(RenderType.entityTranslucent(MODES));
		PoseStack.Pose pose = poseStack.last();
		vertex(icon, pose, -0.25f, 0.25f, 0.0f, u1, 1.0f, 0.0f, 0.0f, 1.0f, packedLight, packedOverlay);
		vertex(icon, pose, 0.25f, 0.25f, 0.0f, u1, 0.0f, 0.0f, 0.0f, 1.0f, packedLight, packedOverlay);
		vertex(icon, pose, 0.25f, -0.25f, 0.0f, u0, 0.0f, 0.0f, 0.0f, 1.0f, packedLight, packedOverlay);
		vertex(icon, pose, -0.25f, -0.25f, 0.0f, u0, 1.0f, 0.0f, 0.0f, 1.0f, packedLight, packedOverlay);
		poseStack.popPose();

		VertexConsumer gear = bufferSource.getBuffer(RenderType.entityCutout(GEAR));
		for (int side = -1; side <= 1; side += 2) {
			poseStack.pushPose();
			poseStack.translate(0.2f * side, -0.40625f, 0.05f);
			poseStack.mulPose(Axis.ZP.rotationDegrees(side * (tile.rot % 360.0f)));
			poseStack.scale(0.5f, 0.5f, 1.0f);
			poseStack.translate(-0.5f, -0.5f, 0.0f);
			extruded(gear, poseStack.last(), 1.0f, 0.0f, 0.0f, 1.0f, 16, 16, 0.1f, packedLight, packedOverlay);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** The original's UtilsFX.renderTextureIn3D: front and back faces plus a strip per texel column and row. */
	private static void extruded(VertexConsumer vc, PoseStack.Pose pose, float maxu, float maxv, float minu, float minv, int width, int height,
			float thickness, int light, int overlay) {
		float t = -thickness;
		vertex(vc, pose, 0, 0, 0, maxu, minv, 0, 0, 1, light, overlay);
		vertex(vc, pose, 1, 0, 0, minu, minv, 0, 0, 1, light, overlay);
		vertex(vc, pose, 1, 1, 0, minu, maxv, 0, 0, 1, light, overlay);
		vertex(vc, pose, 0, 1, 0, maxu, maxv, 0, 0, 1, light, overlay);
		vertex(vc, pose, 0, 1, t, maxu, maxv, 0, 0, -1, light, overlay);
		vertex(vc, pose, 1, 1, t, minu, maxv, 0, 0, -1, light, overlay);
		vertex(vc, pose, 1, 0, t, minu, minv, 0, 0, -1, light, overlay);
		vertex(vc, pose, 0, 0, t, maxu, minv, 0, 0, -1, light, overlay);
		float f5 = 0.5f * (maxu - minu) / width;
		float f6 = 0.5f * (minv - maxv) / height;
		for (int k = 0; k < width; k++) {
			float f7 = k / (float) width;
			float f8 = maxu + (minu - maxu) * f7 - f5;
			vertex(vc, pose, f7, 0, t, f8, minv, -1, 0, 0, light, overlay);
			vertex(vc, pose, f7, 0, 0, f8, minv, -1, 0, 0, light, overlay);
			vertex(vc, pose, f7, 1, 0, f8, maxv, -1, 0, 0, light, overlay);
			vertex(vc, pose, f7, 1, t, f8, maxv, -1, 0, 0, light, overlay);
			float f9 = f7 + 1.0f / width;
			vertex(vc, pose, f9, 1, t, f8, maxv, 1, 0, 0, light, overlay);
			vertex(vc, pose, f9, 1, 0, f8, maxv, 1, 0, 0, light, overlay);
			vertex(vc, pose, f9, 0, 0, f8, minv, 1, 0, 0, light, overlay);
			vertex(vc, pose, f9, 0, t, f8, minv, 1, 0, 0, light, overlay);
		}
		for (int k = 0; k < height; k++) {
			float f7 = k / (float) height;
			float f8 = minv + (maxv - minv) * f7 - f6;
			float f9 = f7 + 1.0f / height;
			vertex(vc, pose, 0, f9, 0, maxu, f8, 0, 1, 0, light, overlay);
			vertex(vc, pose, 1, f9, 0, minu, f8, 0, 1, 0, light, overlay);
			vertex(vc, pose, 1, f9, t, minu, f8, 0, 1, 0, light, overlay);
			vertex(vc, pose, 0, f9, t, maxu, f8, 0, 1, 0, light, overlay);
			vertex(vc, pose, 1, f7, 0, minu, f8, 0, -1, 0, light, overlay);
			vertex(vc, pose, 0, f7, 0, maxu, f8, 0, -1, 0, light, overlay);
			vertex(vc, pose, 0, f7, t, maxu, f8, 0, -1, 0, light, overlay);
			vertex(vc, pose, 1, f7, t, minu, f8, 0, -1, 0, light, overlay);
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz,
			int light, int overlay) {
		vc.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
	}
}

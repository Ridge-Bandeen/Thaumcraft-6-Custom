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
			TextureIn3D.render(gear, poseStack.last(), 1.0f, 0.0f, 0.0f, 1.0f, 16, 16, 0.1f, packedLight, packedOverlay);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz,
			int light, int overlay) {
		vc.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
	}
}

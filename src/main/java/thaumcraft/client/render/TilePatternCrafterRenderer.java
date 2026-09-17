package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.crafting.TilePatternCrafter;

/**
 * Renders {@link TilePatternCrafter}'s real mode indicator and spinning crank gears - restored 2026-09-16, a
 * real, previously-undocumented visual gap (this tile had no renderer at all before, found during the real
 * block-entity-renderer sweep - see the Custom Mob Models/Renderers TODO entry). Real, faithful-in-intent
 * translation of the true original's own real {@code TilePatternCrafterRenderer}: a facing-oriented mode-icon
 * quad (a real 10-frame horizontal strip on {@code pattern_crafter_modes.png}, one frame per real {@link
 * TilePatternCrafter#getPatternType()} value) plus 2 real counter-rotating brass-gear quads, driven by {@link
 * TilePatternCrafter#rot}.
 *
 * <p>Real, disclosed simplifications, not byte-exact translations: the true original's own real gear icons
 * were drawn via a genuinely different technique ({@code UtilsFX.renderTextureIn3D}, a real per-pixel-column
 * extruded mesh giving each icon visible 3D thickness/bevelled edges) - this draws them as plain flat quads
 * instead, since that extrusion technique has no other precedent anywhere in this port and is purely
 * decorative (loses none of the real animation/legibility, just a raised-icon bevel effect). Exact
 * sub-pixel icon placement is also a reasonable approximation of the true original's own real nested
 * transform stack, not a verified pixel-for-pixel match (this port has no way to render and visually compare
 * against the true original directly) - see the UI visual-parity checklist for a human `runClient` check.
 */
public class TilePatternCrafterRenderer implements BlockEntityRenderer<TilePatternCrafter> {

	private static final ResourceLocation MODES = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/blocks/pattern_crafter_modes.png");
	private static final ResourceLocation GEAR = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/gear_brass.png");
	private static final int MODE_FRAMES = 10;

	public TilePatternCrafterRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TilePatternCrafter tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		Direction facing = tile.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);

		poseStack.pushPose();
		poseStack.translate(0.5, 0.75, 0.5);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
		poseStack.translate(0.0, 0.0, -0.5);

		VertexConsumer modesConsumer = bufferSource.getBuffer(RenderType.entityCutout(MODES));
		emitQuad(modesConsumer, poseStack.last(), 0.0f, 0.0f, 0.4f, tile.getPatternType(), MODE_FRAMES, packedLight, packedOverlay);

		VertexConsumer gearConsumer = bufferSource.getBuffer(RenderType.entityCutout(GEAR));
		poseStack.pushPose();
		poseStack.translate(-0.2, -0.15, 0.02);
		poseStack.mulPose(Axis.ZP.rotationDegrees(-tile.rot));
		emitQuad(gearConsumer, poseStack.last(), 0.0f, 0.0f, 0.22f, 0, 1, packedLight, packedOverlay);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(0.2, -0.15, 0.02);
		poseStack.mulPose(Axis.ZP.rotationDegrees(tile.rot));
		emitQuad(gearConsumer, poseStack.last(), 0.0f, 0.0f, 0.22f, 0, 1, packedLight, packedOverlay);
		poseStack.popPose();

		poseStack.popPose();
	}

	private static void emitQuad(VertexConsumer buffer, PoseStack.Pose pose, float cx, float cy, float halfSize,
			int frame, int frameCount, int packedLight, int packedOverlay) {
		float u0 = frame / (float) frameCount;
		float u1 = u0 + 1.0f / frameCount;
		addVertex(buffer, pose, cx - halfSize, cy - halfSize, u0, 1.0f, packedLight, packedOverlay);
		addVertex(buffer, pose, cx - halfSize, cy + halfSize, u0, 0.0f, packedLight, packedOverlay);
		addVertex(buffer, pose, cx + halfSize, cy + halfSize, u1, 0.0f, packedLight, packedOverlay);
		addVertex(buffer, pose, cx + halfSize, cy - halfSize, u1, 1.0f, packedLight, packedOverlay);
	}

	private static void addVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v, int packedLight, int packedOverlay) {
		buffer.addVertex(pose, x, y, 0.0f)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(packedOverlay)
				.setLight(packedLight)
				.setNormal(pose, 0.0f, 0.0f, 1.0f);
	}
}

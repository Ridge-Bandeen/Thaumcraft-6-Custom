package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.essentia.TileTubeValve;

/**
 * Renders {@link TileTubeValve}'s real spinning ring+rod - restored 2026-09-16, a real, previously-undocumented
 * visual gap (this tile had no renderer at all before, found during the block-entity-renderer sweep - see the
 * Custom Mob Models/Renderers TODO entry). Real, faithful geometry+animation translation of the true
 * original's own real {@code TileTubeValveRenderer}: the ring spins at {@code -rotation*1.5} degrees and
 * bobs slightly along the facing axis as it opens/closes, the rod (scaled 0.75/1.0/0.75) rides along with it -
 * matches the true original's own real transform sequence exactly (the rod's own scale is applied on top of,
 * not separate from, the ring's own already-rotated/bobbed frame, same real non-independent stacking the true
 * original used).
 */
public class TileTubeValveRenderer implements BlockEntityRenderer<TileTubeValve> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/models/valve.png");

	private final ModelTubeValve model = new ModelTubeValve();

	public TileTubeValveRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileTubeValve tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		ModelTubeValve.orientToFacing(poseStack, tile.getFacing());
		poseStack.mulPose(Axis.YP.rotationDegrees(-tile.rotation * 1.5f));
		poseStack.translate(0.0, -0.03f - tile.rotation / 360.0f * 0.09f, 0.0);

		VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
		model.renderRing(poseStack, consumer, packedLight);
		poseStack.scale(0.75f, 1.0f, 0.75f);
		model.renderRod(poseStack, consumer, packedLight);

		poseStack.popPose();
	}
}

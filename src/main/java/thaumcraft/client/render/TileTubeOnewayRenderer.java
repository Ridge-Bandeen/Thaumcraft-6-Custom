package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.tiles.essentia.TileTubeOneway;

/**
 * Renders {@link TileTubeOneway}'s real directional flow indicator - restored 2026-09-16 (this class's own
 * doc comment previously called dropping it "keep the mechanic, drop the flourish," which was itself only
 * ever true because no {@code BlockEntityRenderer} precedent existed in this port yet). Real, faithful
 * translation of the true original's own real {@code TileTubeOnewayRenderer}: a single real blue-tinted rod
 * (reusing {@link ModelTubeValve}, no ring) mounted on the tube's own {@link
 * thaumcraft.common.tiles.essentia.TileTube#getFacing} side, shown only while that side actually has a real
 * connectable {@link IEssentiaTransport} neighbor - matches the true original's own real gate exactly.
 */
public class TileTubeOnewayRenderer implements BlockEntityRenderer<TileTubeOneway> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/models/valve.png");
	private static final int TINT = FastColor.ARGB32.color(255, 115, 128, 255);

	private final ModelTubeValve model = new ModelTubeValve();

	public TileTubeOnewayRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileTubeOneway tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (tile.getLevel() == null) return;
		Direction facing = tile.getFacing();
		if (!(tile.getLevel().getBlockEntity(tile.getBlockPos().relative(facing.getOpposite())) instanceof IEssentiaTransport)) {
			return;
		}

		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		ModelTubeValve.orientToFacing(poseStack, facing);
		poseStack.scale(2.0f, 2.0f, 2.0f);
		poseStack.translate(0.0, -0.32, 0.0);

		VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
		model.renderRod(poseStack, consumer, packedLight, TINT);

		poseStack.popPose();
	}
}

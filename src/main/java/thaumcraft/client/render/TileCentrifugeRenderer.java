package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.essentia.TileCentrifuge;

/**
 * Renders {@link TileCentrifuge}'s real spinning axle - restored 2026-09-16, see that class's own doc comment
 * for why this was previously dropped (a real block-entity-renderer subsystem this port had no precedent for
 * at the time) and why it's now a strict improvement rather than a tradeoff once one existed. Real, faithful
 * geometry translation of the true original's own real {@code ModelCentrifuge} (6 boxes: a static top/bottom
 * pair the real baked block model already covers, plus a "spinny bit" - crossbar, 2 dingus arms, a core -
 * that rotates as one unit around {@link TileCentrifuge#rotation}) into the modern {@link LayerDefinition}/
 * {@link CubeListBuilder} builder system, same real coordinates, no dimensions changed - same real technique
 * {@code RenderGrapple}/{@code RenderFocusMine} already established for a hand-cuboid model. Only the real
 * "spinny bit" half is drawn here - the static top/bottom pair is already part of the real baked block model,
 * matching the true original's own real split (its own {@code renderBoxes()}/{@code renderSpinnyBit()} were 2
 * separate calls; the true original always called both from the same TESR, but this port's static model
 * already covers the non-animated half for free).
 */
public class TileCentrifugeRenderer implements BlockEntityRenderer<TileCentrifuge> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/models/centrifuge.png");

	private final ModelPart crossbar;
	private final ModelPart dingus1;
	private final ModelPart dingus2;
	private final ModelPart core;

	public TileCentrifugeRenderer(BlockEntityRendererProvider.Context context) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("crossbar", CubeListBuilder.create().texOffs(16, 0).addBox(-4.0f, -1.0f, -1.0f, 8, 2, 2), PartPose.ZERO);
		root.addOrReplaceChild("dingus1", CubeListBuilder.create().texOffs(0, 16).addBox(4.0f, -3.0f, -2.0f, 4, 6, 4), PartPose.ZERO);
		root.addOrReplaceChild("dingus2", CubeListBuilder.create().texOffs(0, 16).addBox(-8.0f, -3.0f, -2.0f, 4, 6, 4), PartPose.ZERO);
		root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -4.0f, -1.5f, 3, 8, 3), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		crossbar = baked.getChild("crossbar");
		dingus1 = baked.getChild("dingus1");
		dingus2 = baked.getChild("dingus2");
		core = baked.getChild("core");
	}

	@Override
	public void render(TileCentrifuge tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(Axis.YP.rotationDegrees(tile.rotation));
		var consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
		crossbar.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		dingus1.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		dingus2.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		core.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
	}
}

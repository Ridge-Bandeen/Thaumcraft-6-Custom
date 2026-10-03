package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.tiles.essentia.TileAlembic;

/**
 * Renders {@link TileAlembic}'s real "nozzle" indicator - a small brass fitting on every horizontal side that
 * actually has a real connected, receptive {@link IEssentiaTransport} neighbor - real port of the original's
 * own {@code TileAlembicRenderer}, its {@code renderNozzle} half of {@code ModelBoreBase} (the base+pillars
 * half never rendered here either in the original - the block's own static model already covers that).
 * Neighbor connectivity uses this port's own real modern pattern ({@code IEssentiaTransport} neighbor lookup
 * via {@code level.getBlockEntity(pos.relative(dir))}, same as {@code TileTube}), not the stale
 * pre-conversion {@code ThaumcraftApiHelper.getConnectableTile}.
 *
 * <p>As the original, a labelled alembic shows its label - the parchment and the aspect's icon - on the labelled face.
 */
public class TileAlembicRenderer implements BlockEntityRenderer<TileAlembic> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/bore.png");

	private final ModelPart nozzle1;
	private final ModelPart nozzle2;

	public TileAlembicRenderer(BlockEntityRendererProvider.Context context) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("nozzle1", CubeListBuilder.create().texOffs(106, 42).addBox(2.5f, -2.0f, -2.0f, 5, 4, 4), PartPose.offset(0.0f, 8.0f, 0.0f));
		root.addOrReplaceChild("nozzle2", CubeListBuilder.create().texOffs(106, 51).addBox(7.0f, -2.5f, -2.5f, 1, 5, 5), PartPose.offset(0.0f, 8.0f, 0.0f));
		ModelPart baked = LayerDefinition.create(mesh, 128, 64).bakeRoot();
		nozzle1 = baked.getChild("nozzle1");
		nozzle2 = baked.getChild("nozzle2");
	}

	private static final ResourceLocation TEX_LABEL = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/label.png");

	@Override
	public void render(TileAlembic tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		thaumcraft.api.aspects.Aspect filter = tile.getAspectFilter();
		if (filter != null && tile.getLabelFace().getAxis() != Direction.Axis.Y) {
			// the original: centred half a block up, 0.376 out from the centre, the parchment 0.44 across, the icon on top
			poseStack.pushPose();
			poseStack.translate(0.5, 0.5, 0.5);
			poseStack.mulPose(Axis.YP.rotationDegrees(-tile.getLabelFace().toYRot()));
			poseStack.translate(0.0, 0.0, 0.376);
			label(poseStack, bufferSource.getBuffer(RenderType.entityTranslucent(TEX_LABEL)), 0.22f, 0xFFFFFFFF, packedLight, packedOverlay);
			poseStack.translate(0.0, 0.0, 0.001);
			label(poseStack, bufferSource.getBuffer(RenderType.entityTranslucent(filter.getImage())), 0.16f,
					net.minecraft.util.FastColor.ARGB32.opaque(filter.getColor()), packedLight, packedOverlay);
			poseStack.popPose();
		}
		if (tile.getLevel() == null) return;
		VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));

		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (!tile.canOutputTo(dir)) continue;
			if (!(tile.getLevel().getBlockEntity(tile.getBlockPos().relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
			if (!neighbor.canInputFrom(dir.getOpposite())) continue;

			poseStack.pushPose();
			poseStack.translate(0.5, 0.0, 0.5);
			poseStack.mulPose(Axis.YP.rotationDegrees(facingAngle(dir)));
			nozzle1.render(poseStack, vc, packedLight, packedOverlay);
			nozzle2.render(poseStack, vc, packedLight, packedOverlay);
			poseStack.popPose();
		}
	}

	/** Direct port of the original's own per-direction rotation switch (only the 4 horizontal cases - the
	 * original's DOWN/UP cases were real dead code, since the loop only ever iterated horizontal faces). */
	private static float facingAngle(Direction dir) {
		return switch (dir) {
			case NORTH -> 90.0f;
			case SOUTH -> 270.0f;
			case WEST -> 180.0f;
			default -> 0.0f; // EAST
		};
	}

	private static void label(PoseStack poseStack, VertexConsumer vc, float half, int tint, int light, int overlay) {
		PoseStack.Pose pose = poseStack.last();
		vc.addVertex(pose, -half, half, 0.0f).setColor(tint).setUv(0.0f, 0.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
		vc.addVertex(pose, -half, -half, 0.0f).setColor(tint).setUv(0.0f, 1.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
		vc.addVertex(pose, half, -half, 0.0f).setColor(tint).setUv(1.0f, 1.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
		vc.addVertex(pose, half, half, 0.0f).setColor(tint).setUv(1.0f, 0.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
	}
}

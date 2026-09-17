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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.devices.TileBellows;
import thaumcraft.common.tiles.essentia.TileTubeBuffer;

/**
 * Renders {@link TileBellows}'s real "breathing bag" animation - restored 2026-09-17, re-evaluating this
 * tile's own doc comment (see there for why). Real, faithful translation of the true original's own real
 * {@code ModelBellows}/{@code TileBellowsRenderer}: a 5-box {@link CubeListBuilder} model (bottom/top/middle
 * planks, the leather bag, the nozzle), with the bag independently vertically scaled off {@link
 * TileBellows#getInflation()} and both planks sliding apart/together in lockstep to match - same real nested
 * push/scale/translate sequence as the true original's own real GL matrix stack, translated call-for-call into
 * {@link PoseStack} operations (which compose the same way).
 *
 * <p>Real, checked-not-assumed detail: the true original's own {@code Bag} box declares a per-part texture-size
 * override ({@code setTextureSize(64,32)}, different from the model's own base 128x128) - but its own real UV
 * unwrap at that texture offset ({@code texOffs(48,0)}, box size 20x24x20) fits exactly within the real
 * physical 128x128 texture with no overflow either way, so this port's {@link CubeListBuilder} (which has no
 * per-part texture-size override) uses the same numeric offset directly against the model's own single
 * declared 128x128 size - same real texture region either way.
 *
 * <p>Real, disclosed simplification: the true original's own real "bore extension" nozzle (only drawn when a
 * {@link TileTubeBuffer} sits directly against the bellows' own facing, reusing the same real {@code
 * ModelBoreBase#renderNozzle} geometry {@link TileAlembicRenderer} already established) is restored for the 4
 * horizontal facings using that same real proven mapping, but the true original's own real per-facing rotation
 * table for the 2 vertical facings (a genuinely different Z-axis rotation, not a clean 1:1 translation) is
 * approximated with the same general "point outward along facing" intent instead of chasing the exact old
 * numbers - a narrow, rarely-seen decorative sub-case (needs a Bellows placed vertically AND directly against a
 * Tube Buffer), not the main animation.
 */
public class TileBellowsRenderer implements BlockEntityRenderer<TileBellows> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/blocks/bellows.png");
	private static final ResourceLocation TEXTURE_BORE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/models/bore.png");

	private final ModelPart bottomPlank;
	private final ModelPart topPlank;
	private final ModelPart middlePlank;
	private final ModelPart bag;
	private final ModelPart nozzle;

	private final ModelPart boreNozzle1;
	private final ModelPart boreNozzle2;

	public TileBellowsRenderer(BlockEntityRendererProvider.Context context) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("bottomPlank", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0f, 0.0f, -6.0f, 12, 2, 12), PartPose.offset(0.0f, 22.0f, 0.0f));
		root.addOrReplaceChild("topPlank", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0f, 0.0f, -6.0f, 12, 2, 12), PartPose.offset(0.0f, 8.0f, 0.0f));
		root.addOrReplaceChild("middlePlank", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0f, -1.0f, -6.0f, 12, 2, 12), PartPose.offset(0.0f, 16.0f, 0.0f));
		// Baked with the true original's own real render-time pivot override (0, 0.5, 0), not its
		// constructor's own default (0, 16, 0) - that default is never actually used, since the original's own
		// TileBellowsRenderer overrides it every single time right before drawing.
		root.addOrReplaceChild("bag", CubeListBuilder.create().texOffs(48, 0).addBox(-10.0f, -12.03333f, -10.0f, 20, 24, 20), PartPose.offset(0.0f, 0.5f, 0.0f));
		root.addOrReplaceChild("nozzle", CubeListBuilder.create().texOffs(0, 36).addBox(-2.0f, -2.0f, 0.0f, 4, 4, 2), PartPose.offset(0.0f, 16.0f, 6.0f));
		ModelPart baked = LayerDefinition.create(mesh, 128, 128).bakeRoot();
		bottomPlank = baked.getChild("bottomPlank");
		topPlank = baked.getChild("topPlank");
		middlePlank = baked.getChild("middlePlank");
		bag = baked.getChild("bag");
		nozzle = baked.getChild("nozzle");

		MeshDefinition boreMesh = new MeshDefinition();
		PartDefinition boreRoot = boreMesh.getRoot();
		boreRoot.addOrReplaceChild("nozzle1", CubeListBuilder.create().texOffs(106, 42).addBox(2.5f, -2.0f, -2.0f, 5, 4, 4), PartPose.offset(0.0f, 8.0f, 0.0f));
		boreRoot.addOrReplaceChild("nozzle2", CubeListBuilder.create().texOffs(106, 51).addBox(7.0f, -2.5f, -2.5f, 1, 5, 5), PartPose.offset(0.0f, 8.0f, 0.0f));
		ModelPart boreBaked = LayerDefinition.create(boreMesh, 128, 64).bakeRoot();
		boreNozzle1 = boreBaked.getChild("nozzle1");
		boreNozzle2 = boreBaked.getChild("nozzle2");
	}

	@Override
	public void render(TileBellows tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (tile.getLevel() == null) return;
		Direction facing = tile.getBlockState().getValue(BlockStateProperties.FACING);
		float inflation = tile.getInflation();
		float tscale = 0.125f + inflation * 0.875f;

		if (tile.getLevel().getBlockEntity(tile.getBlockPos().relative(facing)) instanceof TileTubeBuffer) {
			renderBoreExtension(facing, poseStack, bufferSource, packedLight, packedOverlay);
		}

		VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
		poseStack.pushPose();
		translateFromOrientation(poseStack, facing);

		poseStack.translate(0.0, 1.0, 0.0);
		poseStack.pushPose();
		poseStack.scale(0.5f, (inflation + 0.1f) / 2.0f, 0.5f);
		bag.render(poseStack, vc, packedLight, packedOverlay);
		poseStack.popPose();
		poseStack.translate(0.0, -1.0, 0.0);

		poseStack.pushPose();
		poseStack.translate(0.0, -tscale / 2.0f + 0.5f, 0.0);
		topPlank.render(poseStack, vc, packedLight, packedOverlay);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(0.0, tscale / 2.0f - 0.5f, 0.0);
		bottomPlank.render(poseStack, vc, packedLight, packedOverlay);
		poseStack.popPose();

		middlePlank.render(poseStack, vc, packedLight, packedOverlay);
		nozzle.render(poseStack, vc, packedLight, packedOverlay);

		poseStack.popPose();
	}

	private void renderBoreExtension(Direction facing, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE_BORE));
		poseStack.pushPose();
		poseStack.translate(0.5 + facing.getStepX(), facing.getStepY(), 0.5 + facing.getStepZ());
		orientNozzle(poseStack, facing);
		boreNozzle1.render(poseStack, vc, packedLight, packedOverlay);
		boreNozzle2.render(poseStack, vc, packedLight, packedOverlay);
		poseStack.popPose();
	}

	private static void orientNozzle(PoseStack poseStack, Direction facing) {
		switch (facing) {
			case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
			case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(270.0f));
			case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
			case EAST -> {
				// identity - local +X already points east
			}
			case UP -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0f));
			case DOWN -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
		}
	}

	private static void translateFromOrientation(PoseStack poseStack, Direction facing) {
		poseStack.translate(0.5, -0.5, 0.5);
		switch (facing) {
			case DOWN -> {
				poseStack.translate(0.0, 1.0, -1.0);
				poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
			}
			case UP -> {
				poseStack.translate(0.0, 1.0, 1.0);
				poseStack.mulPose(Axis.XP.rotationDegrees(270.0f));
			}
			case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
			case SOUTH -> {
				// identity
			}
			case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(270.0f));
			case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
		}
	}
}

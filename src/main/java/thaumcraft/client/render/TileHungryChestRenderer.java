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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ChestBlock;
import thaumcraft.common.tiles.devices.TileHungryChest;

/**
 * Renders {@link TileHungryChest}'s real lid-open animation - the one real gap this device had (its own
 * static {@code block/chest.json} model has no separate movable lid part, and no renderer was registered at
 * all before this). Real port of the original's own {@code TileHungryChestRenderer}, but built directly on
 * vanilla's own real chest geometry/animation machinery instead of a hand-copied one: {@link TileHungryChest}
 * <i>is</i> a real vanilla {@link net.minecraft.world.level.block.entity.ChestBlockEntity} (see that class's
 * own doc comment), so it already carries vanilla's own real, correct {@code getOpenNess(partialTick)} lid
 * progress for free - the exact same real geometry/UV layout vanilla's own {@code ChestRenderer#createSingleBodyLayer}
 * uses, just pointed at this port's own real {@code chesthungry.png}. The geometry and transform are the 1.12 chest's,
 * though: the texture is in the 1.12 layout, which 1.15's flipped chest model no longer matches instead of vanilla's texture via {@code Sheets.chooseMaterial} (which only special-cases
 * {@code EnderChestBlockEntity}/{@code TrappedChestBlockEntity} - a plain reuse of vanilla's own
 * {@code ChestRenderer} would've rendered the wrong, generic vanilla chest texture).
 *
 * <b>Real, deliberate simplification:</b> {@link TileHungryChest} can never actually become part of a double
 * chest (see {@code BlockHungryChest}'s own doc comment - {@code getStateForPlacement}/{@code updateShape}
 * always force {@code ChestType.SINGLE}), so unlike vanilla's own {@code ChestRenderer} this only ever bakes
 * and renders the single-body geometry - no {@code DoubleBlockCombiner} neighbor-lookup, no left/right model
 * variants, both real dead code for a chest that provably never links.
 */
public class TileHungryChestRenderer implements BlockEntityRenderer<TileHungryChest> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/chesthungry.png");

	private final ModelPart bottom;
	private final ModelPart lid;
	private final ModelPart lock;

	public TileHungryChestRenderer(BlockEntityRendererProvider.Context context) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// the 1.12 ModelChest, which the original texture is laid out for (1.15 flipped vanilla's chest model and textures)
		root.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(0, 19).addBox(0.0f, 0.0f, 0.0f, 14, 10, 14), PartPose.offset(1.0f, 6.0f, 1.0f));
		root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(0.0f, -5.0f, -14.0f, 14, 5, 14), PartPose.offset(1.0f, 7.0f, 15.0f));
		root.addOrReplaceChild("lock", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0f, -2.0f, -15.0f, 2, 4, 1), PartPose.offset(8.0f, 7.0f, 15.0f));
		ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
		bottom = baked.getChild("bottom");
		lid = baked.getChild("lid");
		lock = baked.getChild("lock");
	}

	@Override
	public void render(TileHungryChest tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		poseStack.pushPose();
		// the original's transform: the 1.12 chest model drawn upside down and back to front, then turned by the facing
		poseStack.translate(0.0, 1.0, 1.0);
		poseStack.scale(1.0f, -1.0f, -1.0f);
		poseStack.translate(0.5, 0.5, 0.5);
		float turn = switch (tile.getBlockState().getValue(ChestBlock.FACING)) {
			case NORTH -> 180.0f;
			case WEST -> 90.0f;
			case EAST -> -90.0f;
			default -> 0.0f;
		};
		poseStack.mulPose(Axis.YP.rotationDegrees(turn));
		poseStack.translate(-0.5, -0.5, -0.5);

		// Same real easing curve vanilla's own ChestRenderer applies to getOpenNess.
		float openness = 1.0f - tile.getOpenNess(partialTick);
		openness = 1.0f - openness * openness * openness;
		lid.xRot = -(openness * (float) (Math.PI / 2.0));
		lock.xRot = lid.xRot;

		VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
		lid.render(poseStack, vc, packedLight, packedOverlay);
		lock.render(poseStack, vc, packedLight, packedOverlay);
		bottom.render(poseStack, vc, packedLight, packedOverlay);

		poseStack.popPose();
	}
}

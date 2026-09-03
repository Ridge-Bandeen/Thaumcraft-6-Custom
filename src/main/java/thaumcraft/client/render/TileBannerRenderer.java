package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
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
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.tiles.misc.TileBanner;

/**
 * Renders {@link TileBanner} - real, faithful geometry translation of the original's own {@code ModelBanner}
 * (a plain {@code ModelRenderer}-cuboid model: pole/beam/two side-tabs/banner cloth), built here via the
 * modern {@link LayerDefinition}/{@link CubeListBuilder} cuboid system (the same real box coordinates and
 * texture offsets, just re-expressed in the modern builder API - no dimensions changed) instead of the
 * original's own raw {@code ModelBase}/{@code ModelRenderer} classes. This is the first real {@link
 * BlockEntityRenderer} this whole port has needed - every earlier custom-tile-visual case either had no
 * renderer yet or (for entities) reused a real vanilla model.
 *
 * Real facing (0/4/8/12 -> a real Y rotation) and wall-vs-floor pole visibility, matching the original's own
 * {@code renderTileEntityAt} exactly. Real texture swap: the "Crimson Cult" symbol texture normally, a
 * blank banner texture once a real {@link thaumcraft.api.aspects.Aspect} tag is set via {@link
 * thaumcraft.common.blocks.misc.BlockBannerTC}'s own phial interaction.
 *
 * Real, documented cuts: no per-frame cloth-sway animation (the original's own small sine-wave tilt on the
 * banner cloth part) and no aspect-icon overlay drawn on the cloth once tagged (the original's own {@code
 * UtilsFX.drawTag} call, which needs a whole separate icon-atlas texture system this port doesn't have
 * wired up for blocks) - both real, minor, purely cosmetic simplifications on top of an otherwise faithful
 * render; the banner's own real state (facing, wall/floor, tagged aspect) is unaffected.
 */
public class TileBannerRenderer implements BlockEntityRenderer<TileBanner> {

	private static final ResourceLocation TEX_CULT = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/banner_cultist.png");
	private static final ResourceLocation TEX_BLANK = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/banner_blank.png");

	private final ModelPart pole;
	private final ModelPart beam;
	private final ModelPart tab1;
	private final ModelPart tab2;
	private final ModelPart banner;

	public TileBannerRenderer(BlockEntityRendererProvider.Context context) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("tab1", CubeListBuilder.create().texOffs(0, 29).addBox(-5.0f, -7.5f, -1.5f, 2, 3, 3), PartPose.ZERO);
		root.addOrReplaceChild("tab2", CubeListBuilder.create().texOffs(0, 29).addBox(3.0f, -7.5f, -1.5f, 2, 3, 3), PartPose.ZERO);
		root.addOrReplaceChild("beam", CubeListBuilder.create().texOffs(30, 0).addBox(-7.0f, -7.0f, -1.0f, 14, 2, 2), PartPose.ZERO);
		root.addOrReplaceChild("banner", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0f, 0.0f, -0.5f, 14, 28, 1), PartPose.offset(0.0f, -5.0f, 0.0f));
		root.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(62, 0).addBox(0.0f, 0.0f, -1.0f, 2, 31, 2), PartPose.offset(-1.0f, -7.0f, -2.0f));
		ModelPart baked = LayerDefinition.create(mesh, 128, 64).bakeRoot();
		tab1 = baked.getChild("tab1");
		tab2 = baked.getChild("tab2");
		beam = baked.getChild("beam");
		banner = baked.getChild("banner");
		pole = baked.getChild("pole");
	}

	@Override
	public void render(TileBanner tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		poseStack.pushPose();
		poseStack.translate(0.5, 1.5, 0.5);
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
		poseStack.mulPose(Axis.YP.rotationDegrees(tile.getBannerFacing() * 360.0f / 16.0f));

		ResourceLocation texture = tile.getAspect() == null ? TEX_CULT : TEX_BLANK;
		var vertexConsumer = bufferSource.getBuffer(RenderType.entityCutout(texture));

		if (!tile.getWall()) {
			pole.render(poseStack, vertexConsumer, packedLight, packedOverlay);
		}
		else {
			poseStack.translate(0.0, 1.0, -0.4125);
		}
		beam.render(poseStack, vertexConsumer, packedLight, packedOverlay);
		tab1.render(poseStack, vertexConsumer, packedLight, packedOverlay);
		tab2.render(poseStack, vertexConsumer, packedLight, packedOverlay);
		banner.render(poseStack, vertexConsumer, packedLight, packedOverlay);

		poseStack.popPose();
	}
}

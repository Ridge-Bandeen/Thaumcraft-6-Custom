package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.common.tiles.crafting.TileCrucible;

/**
 * Renders {@link TileCrucible}'s real water fill - real port of the original's own
 * {@code TileCrucibleRenderer}: a single translucent quad textured with the real vanilla water sprite, tinted
 * increasingly pink/red as {@link TileCrucible#aspects} fills up (same real formula the original used,
 * against the exact same real 500-vis cap - {@link TileCrucible#MAX_ASPECTS}), sat inside the crucible's own
 * static cavity.
 *
 * Modern technique note: the original bound the vanilla block atlas directly and drew one manual quad via the
 * old immediate-mode Tessellator; this uses the same real modern approach {@code BeaconRenderer}'s own beam
 * quads use - {@link RenderType#translucent()} (which already binds the block atlas), a
 * {@link Minecraft#getBlockRenderer()}-looked-up {@link TextureAtlasSprite} for {@link Blocks#WATER}, and
 * explicit {@link VertexConsumer#addVertex(PoseStack.Pose, float, float, float)} calls.
 *
 * <b>Real, deliberate simplification:</b> {@link TileCrucible#hasWater} is a plain boolean (see that class's
 * own doc comment on why - no real fluid-handler tank), not a fill amount, so unlike the original (which
 * positioned the quad at the tank's own real fluid height) this always sits at a fixed, near-full height -
 * there's no real fill-level data left to vary it by.
 */
public class TileCrucibleRenderer implements BlockEntityRenderer<TileCrucible> {

	/** Pixel-space height (of 16) the water surface sits at - see this class's own doc comment on why it's
	 * fixed instead of fill-level-driven. */
	private static final float WATER_HEIGHT = 12.0f / 16.0f;

	public TileCrucibleRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileCrucible tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (!tile.hasWater) return;

		TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.WATER.defaultBlockState());

		float recolor = tile.aspects.visSize() / (float) TileCrucible.MAX_ASPECTS;
		if (recolor > 0.0f) recolor = 0.5f + recolor / 2.0f;
		recolor = Mth.clamp(recolor, 0.0f, 1.0f);
		int color = FastColor.ARGB32.color(255, 255, (int) (255 * (1.0f - recolor / 3.0f)), (int) (255 * (1.0f - recolor)));

		poseStack.pushPose();
		poseStack.translate(0.0, WATER_HEIGHT, 1.0);
		poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));

		VertexConsumer vc = bufferSource.getBuffer(RenderType.translucent());
		PoseStack.Pose pose = poseStack.last();
		addVertex(vc, pose, 0.0f, 0.0f, sprite.getU1(), sprite.getV1(), color, packedLight);
		addVertex(vc, pose, 1.0f, 0.0f, sprite.getU0(), sprite.getV1(), color, packedLight);
		addVertex(vc, pose, 1.0f, 1.0f, sprite.getU0(), sprite.getV0(), color, packedLight);
		addVertex(vc, pose, 0.0f, 1.0f, sprite.getU1(), sprite.getV0(), color, packedLight);

		poseStack.popPose();
	}

	private void addVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int color, int packedLight) {
		vc.addVertex(pose, x, y, 0.0f)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(packedLight)
				.setNormal(pose, 0.0f, 0.0f, 1.0f);
	}
}

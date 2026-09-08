package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.common.tiles.devices.TileDioptra;

/**
 * Renders {@link TileDioptra}'s real 13x13 scan grid ({@link TileDioptra#gridAmount}) as an undulating mesh -
 * real port of the original's own {@code TileDioptraRenderer} core technique (a dynamic per-vertex-height
 * grid, rebuilt fresh every frame - not a static cuboid model, the first renderer this whole arc has needed a
 * genuinely different technique for). 12x12 quads, one real vertex per scanned chunk, height driven directly
 * by that chunk's real recorded vis/flux reading - same real shimmering tint the original used too (brighter/
 * cooler while {@link net.minecraft.world.level.block.state.properties.BlockStateProperties#ENABLED} scans
 * vis, warmer/dimmer-green while scanning flux).
 *
 * <b>Real, deliberate scope cuts</b> (all purely decorative, not information-bearing - the grid's own real
 * undulating heightmap, which actually shows real scan data, is the one thing kept faithfully):
 * <ul>
 * <li>The original's 4 edge "skirt" quads (closing the grid's outer rim down to the block's own side) and its
 * 4 rotated translucent "hood" panels (a separate {@code dioptra_side.png}-textured shield around the whole
 * device) are both dropped - the grid renders as a standalone rippling plane instead of a fully skirted dish.
 * </li>
 * <li>The original's real per-quad distance-based brightness pulse (a sine wave over each quad's packed
 * lightmap value) is dropped - every quad uses the same real ambient light passed into {@link #render}.</li>
 * <li>The original's {@code calcColorMap} per-vertex color-dimming branch was already real, confirmed dead
 * code in the original itself (its one real caller always passed all-zero inputs, so the branch never fired)
 * - not ported for that reason, not as a new simplification.</li>
 * </ul>
 */
public class TileDioptraRenderer implements BlockEntityRenderer<TileDioptra> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/gridblock.png");
	private static final int GRID_SIZE = 13;
	private static final float INSET = 0.99f;
	private static final float ALPHA = 0.9f;

	public TileDioptraRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileDioptra tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		Minecraft mc = Minecraft.getInstance();
		float ticks = (mc.player != null ? mc.player.tickCount : 0) + partialTick;
		boolean visMode = tile.getBlockState().getValue(BlockStateProperties.ENABLED);

		float rc = Mth.sin(ticks / 12.0f) * 0.05f + 0.85f;
		float gc = visMode ? Mth.sin(ticks / 11.0f) * 0.05f + 0.9f : Mth.sin(ticks / 11.0f) * 0.05f + 0.45f;
		float bc = Mth.sin(ticks / 10.0f) * 0.05f + 0.95f;
		int color = FastColor.ARGB32.color((int) (ALPHA * 255.0f), (int) (rc * 0.8f * 255.0f), (int) (gc * 255.0f), (int) (bc * 255.0f));

		poseStack.pushPose();
		poseStack.translate(-0.495 + (1.0 - INSET) / 2.0, 0.505, -0.495 + (1.0 - INSET) / 2.0);
		poseStack.scale(INSET, 1.0f, INSET);

		VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
		PoseStack.Pose pose = poseStack.last();
		for (int a = 0; a < GRID_SIZE - 1; a++) {
			for (int b = 0; b < GRID_SIZE - 1; b++) {
				float x0 = a / 12.0f;
				float x1 = (a + 1) / 12.0f;
				float z0 = b / 12.0f;
				float z1 = (b + 1) / 12.0f;
				float y00 = height(tile, a, b);
				float y10 = height(tile, a + 1, b);
				float y11 = height(tile, a + 1, b + 1);
				float y01 = height(tile, a, b + 1);
				// flipFace()'d in the original - winding order here matches so the visible face points up.
				addVertex(vc, pose, x0, y00, z0, 0.0f, 1.0f, color, packedLight);
				addVertex(vc, pose, x1, y10, z0, 1.0f, 1.0f, color, packedLight);
				addVertex(vc, pose, x1, y11, z1, 1.0f, 0.0f, color, packedLight);
				addVertex(vc, pose, x0, y01, z1, 0.0f, 0.0f, color, packedLight);
			}
		}
		poseStack.popPose();
	}

	private static float height(TileDioptra tile, int x, int z) {
		return (tile.gridAmount[x + z * GRID_SIZE] & 0xFF) / 64.0f;
	}

	private void addVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int packedLight) {
		vc.addVertex(pose, x, y, z)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(packedLight)
				.setNormal(pose, 0.0f, 1.0f, 0.0f);
	}
}

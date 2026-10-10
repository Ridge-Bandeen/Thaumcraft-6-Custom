package thaumcraft.client.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

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
 * Renders the Dioptra's hologram as the original {@code TileDioptraRenderer}, on top of the block:
 * <ul>
 * <li>the 13x13 scan grid ({@link TileDioptra#gridAmount}, one vertex per chunk) as a 12x12 mesh from y 1.001, each
 * vertex raised by its reading / 96, on {@code gridblock.png}, additive, tinted with the shimmer (cooler while scanning
 * vis, greener-dimmer while scanning flux; red at 0.8) at 0.9 alpha. Each quad is lit by its own pulse, brightness
 * {@code 200 + sin((counter - distance from the centre * 10) / 8) * 15}, so rings run out from the middle;</li>
 * <li>skirts closing the grid's four edges down to the block top, fading from 0.9 alpha at the grid to clear at the
 * bottom;</li>
 * <li>the hood: four {@code dioptra_side.png} panels round the 1x1x1 space above the block, additive at 0.8 alpha in the
 * shimmer tint, brightness 220, drawn from both sides.</li>
 * </ul>
 * The grid and skirts keep the original's face winding and back-face culling. The original's per-vertex colour dimming
 * ({@code calcColorMap}) only ever received zeros, so it never dimmed anything and isn't reproduced.
 */
public class TileDioptraRenderer implements BlockEntityRenderer<TileDioptra> {

	private static final ResourceLocation GRID = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/gridblock.png");
	private static final ResourceLocation SIDE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/dioptra_side.png");
	private static final int GRID_SIZE = 13;

	public TileDioptraRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileDioptra tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		Minecraft mc = Minecraft.getInstance();
		float t = mc.player != null ? mc.player.tickCount + partialTick : 0.0f;
		boolean visMode = tile.getBlockState().getValue(BlockStateProperties.ENABLED);
		float rc = Mth.sin(t / 12.0f) * 0.05f + 0.85f;
		float gc = Mth.sin(t / 11.0f) * 0.05f + (visMode ? 0.9f : 0.45f);
		float bc = Mth.sin(t / 10.0f) * 0.05f + 0.95f;
		int r = (int) (rc * 0.8f * 255.0f);
		int g = (int) (Mth.clamp(gc, 0.0f, 1.0f) * 255.0f);
		int b = (int) (bc * 255.0f);

		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);

		poseStack.pushPose();
		poseStack.translate(-0.495, 0.501, -0.495);
		poseStack.scale(0.99f, 1.0f, 0.99f);
		VertexConsumer grid = bufferSource.getBuffer(Types.additive(GRID, true));
		PoseStack.Pose pose = poseStack.last();
		float[] solid = { 0.9f, 0.9f, 0.9f, 0.9f };
		float[] sideFade = { 0.0f, 0.9f, 0.9f, 0.0f };
		float[] endFade = { 0.0f, 0.0f, 0.9f, 0.9f };
		for (int a = 0; a < 12; a++) {
			for (int c = 0; c < 12; c++) {
				double dx = a - 6;
				double dz = c - 6;
				float s = Mth.sin((float) ((tile.counter - Math.sqrt(dx * dx + dz * dz) * 10.0) / 8.0));
				int light = (int) (200.0f + s * 15.0f);
				float x0 = a / 12.0f;
				float x1 = (a + 1) / 12.0f;
				float z0 = c / 12.0f;
				float z1 = (c + 1) / 12.0f;
				quad(grid, pose, new float[][] { { x0, h(tile, a, c), z0, 0, 1 }, { x1, h(tile, a + 1, c), z0, 1, 1 },
						{ x1, h(tile, a + 1, c + 1), z1, 1, 0 }, { x0, h(tile, a, c + 1), z1, 0, 0 } }, true, r, g, b, solid, light);
				if (a == 0) {
					quad(grid, pose, new float[][] { { 0, 0, z0, 0, 1 }, { 0, h(tile, 0, c), z0, 1, 1 },
							{ 0, h(tile, 0, c + 1), z1, 1, 0 }, { 0, 0, z1, 0, 0 } }, true, r, g, b, sideFade, light);
				}
				if (a == 11) {
					quad(grid, pose, new float[][] { { 1, 0, z0, 0, 1 }, { 1, h(tile, 12, c), z0, 1, 1 },
							{ 1, h(tile, 12, c + 1), z1, 1, 0 }, { 1, 0, z1, 0, 0 } }, false, r, g, b, sideFade, light);
				}
				if (c == 0) {
					quad(grid, pose, new float[][] { { x0, 0, 0, 0, 1 }, { x1, 0, 0, 1, 1 },
							{ x1, h(tile, a + 1, 0), 0, 1, 0 }, { x0, h(tile, a, 0), 0, 0, 0 } }, true, r, g, b, endFade, light);
				}
				if (c == 11) {
					quad(grid, pose, new float[][] { { x0, 0, 1, 0, 1 }, { x1, 0, 1, 1, 1 },
							{ x1, h(tile, a + 1, 12), 1, 1, 0 }, { x0, h(tile, a, 12), 1, 0, 0 } }, false, r, g, b, endFade, light);
				}
			}
		}
		poseStack.popPose();

		// the hood: renderQuadCentered on dioptra_side.png, culling off
		VertexConsumer hood = bufferSource.getBuffer(Types.additive(SIDE, false));
		float[] hoodAlpha = { 0.8f, 0.8f, 0.8f, 0.8f };
		int hr = (int) (rc * 255.0f);
		poseStack.translate(0.0, 1.0, 0.0);
		poseStack.mulPose(Axis.ZP.rotationDegrees(270.0f));
		for (int q = 0; q < 4; q++) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.XP.rotationDegrees(90.0f * q));
			poseStack.translate(0.0, 0.0, -0.5);
			quad(hood, poseStack.last(), new float[][] { { -0.5f, 0.5f, 0, 1, 1 }, { 0.5f, 0.5f, 0, 1, 0 },
					{ 0.5f, -0.5f, 0, 0, 0 }, { -0.5f, -0.5f, 0, 0, 1 } }, false, hr, g, b, hoodAlpha, 220);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** A reading's height: the original's {@code grid_amt / 96}. */
	private static float h(TileDioptra tile, int x, int z) {
		return tile.gridAmount[x + z * GRID_SIZE] / 96.0f;
	}

	/** The original's {@code TexturedQuadTC}: corners {x, y, z, u, v}, reversed when flipped, each keeping its own alpha. */
	private static void quad(VertexConsumer vc, PoseStack.Pose pose, float[][] corners, boolean flipped, int r, int g, int b,
			float[] alphas, int light) {
		for (int i = 0; i < 4; i++) {
			int idx = flipped ? 3 - i : i;
			float[] c = corners[idx];
			vc.addVertex(pose, c[0], c[1], c[2]).setColor(FastColor.ARGB32.color((int) (alphas[idx] * 255.0f), r, g, b))
					.setUv(c[3], c[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
		}
	}

	@Override
	public net.minecraft.world.phys.AABB getRenderBoundingBox(TileDioptra tile) {
		var p = tile.getBlockPos();
		return new net.minecraft.world.phys.AABB(p.getX() - 0.3, p.getY() - 0.3, p.getZ() - 0.3, p.getX() + 1.3, p.getY() + 2.3, p.getZ() + 1.3);
	}

	/** Additive blending (the original's glBlendFunc(770, 1)) with the block's lightmap, culled or not. */
	private static final class Types extends RenderType {
		private static final Map<String, RenderType> CACHE = new HashMap<>();

		static RenderType additive(ResourceLocation texture, boolean cull) {
			return CACHE.computeIfAbsent(texture + "/" + cull, k -> create("thaumcraft_dioptra", DefaultVertexFormat.NEW_ENTITY,
					VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
							.setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
							.setTextureState(new TextureStateShard(texture, false, false))
							.setTransparencyState(ADDITIVE_TRANSPARENCY)
							.setCullState(cull ? CULL : NO_CULL)
							.setLightmapState(LIGHTMAP)
							.setOverlayState(OVERLAY)
							.createCompositeState(false)));
		}

		private Types() {
			super("", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
		}
	}
}

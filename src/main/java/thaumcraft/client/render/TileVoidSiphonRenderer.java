package thaumcraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.common.tiles.crafting.TileVoidSiphon;

/**
 * Renders {@link TileVoidSiphon}'s real 2-ring starfield display - a real, faithful port of the true
 * original's own real {@code TileVoidSiphonRenderer}: a small ring of 6 near-cubic faces near the top, a
 * larger ring of flatter ones lower down, one small quad per {@link Direction} in each ring, only while the
 * tile's own real {@link BlockStateProperties#ENABLED} is set - painted through the same real
 * {@link EnderShader} as {@link TileHoleRenderer}/{@code RenderFluxRift}.
 *
 * <p>Real, checked-not-guessed simplification: the true original's own real per-face in-plane roll is dropped
 * (a real no-op - see {@link TileHoleRenderer}'s own doc comment for why). The per-layer blend function is
 * kept (additive for the small top ring, normal alpha for the large one) - it is NOT a no-op even though the
 * shader's output alpha is always 1, since additive is dst+src while normal blend at alpha 1 is an opaque
 * replace. Depth-write is disabled for both rings, matching the original's own real {@code depthMask(false)}.
 * The large ring's own real anisotropic scale (0.25/0.5/0.25 - flatter on 2 axes than the third) is applied
 * per-face against whichever 2 world axes are actually tangent to that face, not against a naive local-frame
 * guess - checked directly by re-deriving the true original's own real matrix composition order (its own
 * offset translate happens BEFORE the scale, so the scale only ever touches each quad's own drawn size, never
 * its position) rather than assumed.
 */
public class TileVoidSiphonRenderer implements BlockEntityRenderer<TileVoidSiphon> {

	private static final float QUAD_HALF = 0.5f;

	public TileVoidSiphonRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileVoidSiphon tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (!tile.getBlockState().getValue(BlockStateProperties.ENABLED)) return;

		RenderSystem.depthMask(false);

		poseStack.pushPose();
		poseStack.translate(0.5, 0.875, 0.5);
		drawRing(poseStack, true, 0.126f, 0.2f, 0.2f, 0.2f);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(0.5, 0.3125, 0.5);
		drawRing(poseStack, false, 0.26f, 0.25f, 0.5f, 0.25f);
		poseStack.popPose();

		RenderSystem.depthMask(true);
	}

	/** One ring of 6 quads, one per {@link Direction}, {@code offset} units out from the current origin
	 * (unscaled - matches the true original's own real translate-before-scale order), each sized against
	 * whichever 2 of {@code scaleX}/{@code scaleY}/{@code scaleZ} are actually tangent to that face. */
	private static void drawRing(PoseStack poseStack, boolean additive, float offset, float scaleX, float scaleY, float scaleZ) {
		BufferBuilder builder = EnderShader.begin(VertexFormat.Mode.QUADS, additive);
		if (builder == null) return;
		PoseStack.Pose pose = poseStack.last();
		for (Direction face : Direction.values()) {
			float x = face.getStepX() * offset;
			float y = face.getStepY() * offset;
			float z = face.getStepZ() * offset;
			switch (face.getAxis()) {
				case X -> emitQuad(builder, pose, x, y, z, 0, QUAD_HALF * scaleY, 0, 0, 0, QUAD_HALF * scaleZ);
				case Y -> emitQuad(builder, pose, x, y, z, QUAD_HALF * scaleX, 0, 0, 0, 0, QUAD_HALF * scaleZ);
				case Z -> emitQuad(builder, pose, x, y, z, QUAD_HALF * scaleX, 0, 0, 0, QUAD_HALF * scaleY, 0);
			}
		}
		EnderShader.end(builder);
	}

	/** Emits a quad centered at {@code (cx,cy,cz)}, spanning {@code ±(uX,uY,uZ)} along its own first tangent
	 * axis and {@code ±(vX,vY,vZ)} along its second. */
	private static void emitQuad(BufferBuilder builder, PoseStack.Pose pose, float cx, float cy, float cz,
			float uX, float uY, float uZ, float vX, float vY, float vZ) {
		builder.addVertex(pose, cx - uX - vX, cy - uY - vY, cz - uZ - vZ);
		builder.addVertex(pose, cx + uX - vX, cy + uY - vY, cz + uZ - vZ);
		builder.addVertex(pose, cx + uX + vX, cy + uY + vY, cz + uZ + vZ);
		builder.addVertex(pose, cx - uX + vX, cy - uY + vY, cz - uZ + vZ);
	}
}

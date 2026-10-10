package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.blocks.misc.BlockHole;
import thaumcraft.common.tiles.misc.TileHole;

/**
 * Renders {@link TileHole}'s real "starfield walls" - a real, faithful port of the true original's own real
 * {@code TileHoleRenderer}: one flat quad per side that has a real opaque neighbor (skipping open/air sides
 * and any side backed by another {@link BlockHole}, matching the original's own real gate exactly), painted
 * through the real {@link EnderShader} - see that class's own doc comment for the real shader-porting work
 * this and {@link TileVoidSiphonRenderer}/{@code RenderFluxRift} all share.
 *
 * <p>Not a difference: the original's in-plane 90-degree roll (applied
 * after orienting each quad to face outward) is dropped - confirmed by reading the shader's own real math
 * first (see {@link EnderShader}'s own doc comment) that it computes colour purely from each vertex's own
 * camera-relative 3D position, never from the mesh's own UVs/tangent frame, so a quad's own rotation about its
 * own normal axis covers the exact same set of 3D positions either way - a real no-op to skip, not a visual
 * simplification.
 */
public class TileHoleRenderer implements BlockEntityRenderer<TileHole> {

	private static final float HALF = 0.5f;
	private static final float OFFSET = 0.499f;

	public TileHoleRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileHole tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (tile.getLevel() == null) return;
		BlockPos pos = tile.getBlockPos();

		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);

		BufferBuilder builder = EnderShader.begin(VertexFormat.Mode.QUADS);
		if (builder == null) {
			poseStack.popPose();
			return;
		}

		PoseStack.Pose pose = poseStack.last();
		for (Direction face : Direction.values()) {
			BlockState neighbor = tile.getLevel().getBlockState(pos.relative(face));
			if (neighbor.getBlock() instanceof BlockHole) continue;
			if (!neighbor.isSolidRender(tile.getLevel(), pos.relative(face))) continue;
			emitFace(builder, pose, face);
		}

		EnderShader.end(builder);
		poseStack.popPose();
	}

	/** One 1x1 quad flush against {@code face}, facing outward. */
	private static void emitFace(BufferBuilder builder, PoseStack.Pose pose, Direction face) {
		switch (face.getAxis()) {
			case X -> {
				float x = face.getStepX() > 0 ? OFFSET : -OFFSET;
				builder.addVertex(pose, x, -HALF, -HALF);
				builder.addVertex(pose, x, -HALF, HALF);
				builder.addVertex(pose, x, HALF, HALF);
				builder.addVertex(pose, x, HALF, -HALF);
			}
			case Y -> {
				float y = face.getStepY() > 0 ? OFFSET : -OFFSET;
				builder.addVertex(pose, -HALF, y, -HALF);
				builder.addVertex(pose, HALF, y, -HALF);
				builder.addVertex(pose, HALF, y, HALF);
				builder.addVertex(pose, -HALF, y, HALF);
			}
			case Z -> {
				float z = face.getStepZ() > 0 ? OFFSET : -OFFSET;
				builder.addVertex(pose, -HALF, -HALF, z);
				builder.addVertex(pose, HALF, -HALF, z);
				builder.addVertex(pose, HALF, HALF, z);
				builder.addVertex(pose, -HALF, HALF, z);
			}
		}
	}
}

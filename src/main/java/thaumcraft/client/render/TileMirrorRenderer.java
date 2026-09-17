package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.devices.TileMirror;
import thaumcraft.common.tiles.devices.TileMirrorEssentia;

/**
 * Shared renderer for {@link TileMirror} and {@link TileMirrorEssentia} - restored 2026-09-17, re-evaluating
 * both tiles' own doc comments (and {@code BlockMirror}'s), which had deliberately dropped the true original's
 * custom TESR under this port's early, blanket "no custom renderer restored yet" boundary. That boundary no
 * longer holds (this port has since restored a long list of block-entity renderers), so the cut is undone here
 * rather than left standing, per this port's own "re-evaluate past cuts once their blocker resolves" rule.
 *
 * The true original's own {@code TileMirrorRenderer} hand-rolled the exact same 16-layer parallax-tunnel
 * effect as vanilla's End Portal, using raw {@code GL_TEXTURE_GEN} matrix math (628 lines of fixed-function
 * OpenGL, long gone in modern rendering). Modern Minecraft ported that same effect to a dedicated fragment
 * shader ({@code rendertype_end_portal}, see {@link RenderStateShard#RENDERTYPE_END_PORTAL_SHADER}) driven by
 * nothing but a plain position-only quad - so rather than reimplement 628 lines of dead GL calls, this reuses
 * that real vanilla shader wholesale with Thaumcraft's own two textures swapped in for vanilla's
 * {@code end_sky.png}/{@code end_portal.png} (the true original's own {@code tunnel.png} plays the exact same
 * "background" role as vanilla's sky layer; {@code particlefield.png} plays the same "swirl" role vanilla
 * repeats across its 15 foreground layers) - a faithful, not-simplified port of the same real visual technique,
 * just built on the modern equivalent of the exact same trick the original itself used.
 *
 * Only draws the tunnel quad when the tile's own real link is valid ({@link TileMirror#isLinkValid()} /
 * {@link TileMirrorEssentia#isLinkValid()} - the modern equivalent of the original's {@code linked} boolean)
 * and the viewer is within the original's own real 32-block (1024 distance-squared) cutoff. Backface culling is
 * disabled for the quad rather than chasing exact vanilla winding order - harmless here since the mirror's own
 * support block already occludes the one side that would otherwise show through.
 */
public class TileMirrorRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

	private static final ResourceLocation TUNNEL = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/tunnel.png");
	private static final ResourceLocation PARTICLEFIELD = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particlefield.png");

	private static final RenderType MIRROR_PORTAL = RenderType.create(
			"thaumcraft:mirror_portal",
			DefaultVertexFormat.POSITION,
			VertexFormat.Mode.QUADS,
			256,
			false,
			false,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.RENDERTYPE_END_PORTAL_SHADER)
					.setTextureState(RenderStateShard.MultiTextureStateShard.builder()
							.add(TUNNEL, false, false)
							.add(PARTICLEFIELD, false, false)
							.build())
					.setCullState(RenderStateShard.NO_CULL)
					.createCompositeState(false));

	private static final float INSET = 0.0625f;
	private static final float EPSILON = 0.001f;
	private static final double VIEW_DISTANCE_SQR = 1024.0;

	public TileMirrorRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(T tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		boolean linked;
		if (tile instanceof TileMirror mirror) {
			linked = mirror.isLinkValid();
		} else if (tile instanceof TileMirrorEssentia mirrorEssentia) {
			linked = mirrorEssentia.isLinkValid();
		} else {
			return;
		}
		if (!linked) return;

		BlockPos pos = tile.getBlockPos();
		var player = Minecraft.getInstance().player;
		if (player == null || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > VIEW_DISTANCE_SQR) {
			return;
		}

		Direction facing = tile.getBlockState().getValue(BlockStateProperties.FACING);
		Matrix4f pose = poseStack.last().pose();
		VertexConsumer consumer = bufferSource.getBuffer(MIRROR_PORTAL);

		Direction.Axis axis = facing.getAxis();
		int step = switch (axis) {
			case X -> facing.getStepX();
			case Y -> facing.getStepY();
			case Z -> facing.getStepZ();
		};
		float coord = step > 0 ? INSET + EPSILON : 1.0f - INSET - EPSILON;
		float lo = INSET;
		float hi = 1.0f - INSET;

		switch (axis) {
			case Y -> {
				consumer.addVertex(pose, lo, coord, lo);
				consumer.addVertex(pose, lo, coord, hi);
				consumer.addVertex(pose, hi, coord, hi);
				consumer.addVertex(pose, hi, coord, lo);
			}
			case X -> {
				consumer.addVertex(pose, coord, lo, lo);
				consumer.addVertex(pose, coord, lo, hi);
				consumer.addVertex(pose, coord, hi, hi);
				consumer.addVertex(pose, coord, hi, lo);
			}
			case Z -> {
				consumer.addVertex(pose, lo, lo, coord);
				consumer.addVertex(pose, lo, hi, coord);
				consumer.addVertex(pose, hi, hi, coord);
				consumer.addVertex(pose, hi, lo, coord);
			}
		}
	}
}

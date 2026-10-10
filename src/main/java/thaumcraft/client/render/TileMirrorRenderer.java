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
 * As the original (whose block is invisible - its cube model is only for particles and the item): the frame, its
 * block sprite ({@code mirrorframe}, or {@code mirrorframe2} for the essentia mirror) extruded 1/16 deep against the
 * mounting face; the glass pane just inside it ({@code mirrorpane}, or the see-through {@code mirrorpanetrans} while
 * linked and the viewer is within 32 blocks); and in that case the tunnel behind the glass, 0.01 from the mounting face
 * and inset 3 pixels a side.
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

	private static final float INSET = 0.1875f;
	private static final double VIEW_DISTANCE_SQR = 1024.0;

	public TileMirrorRenderer(BlockEntityRendererProvider.Context context) {
	}

	private static final ResourceLocation PANE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/blocks/mirrorpane.png");
	private static final ResourceLocation PANE_TRANS = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/blocks/mirrorpanetrans.png");
	private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "blocks/mirrorframe");
	private static final ResourceLocation FRAME_ESSENTIA = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "blocks/mirrorframe2");

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
		Direction facing = tile.getBlockState().getValue(BlockStateProperties.FACING);

		// the frame
		var sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
				.apply(tile instanceof TileMirrorEssentia ? FRAME_ESSENTIA : FRAME);
		poseStack.pushPose();
		orient(poseStack, facing, 0.01f);
		TextureIn3D.render(bufferSource.getBuffer(RenderType.entityTranslucent(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)), poseStack.last(),
				sprite.getU1(), sprite.getV0(), sprite.getU0(), sprite.getV1(), 16, 16, 0.0625f, packedLight, packedOverlay);
		poseStack.popPose();

		BlockPos pos = tile.getBlockPos();
		var player = Minecraft.getInstance().player;
		boolean open = linked && player != null && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < VIEW_DISTANCE_SQR;

		// the glass: renderQuadCentered, turned a quarter and moved to the 0-1 square
		poseStack.pushPose();
		orient(poseStack, facing, 0.02f);
		poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90.0f));
		poseStack.translate(0.5, -0.5, 0.0);
		VertexConsumer pane = bufferSource.getBuffer(RenderType.entityTranslucent(open ? PANE_TRANS : PANE));
		PoseStack.Pose pp = poseStack.last();
		TextureIn3D.vertex(pane, pp, -0.5f, 0.5f, 0, 1, 1, 0, 0, 1, packedLight, packedOverlay);
		TextureIn3D.vertex(pane, pp, 0.5f, 0.5f, 0, 1, 0, 0, 0, 1, packedLight, packedOverlay);
		TextureIn3D.vertex(pane, pp, 0.5f, -0.5f, 0, 0, 0, 0, 0, 1, packedLight, packedOverlay);
		TextureIn3D.vertex(pane, pp, -0.5f, -0.5f, 0, 0, 1, 0, 0, 1, packedLight, packedOverlay);
		poseStack.popPose();

		if (!open) return;
		Matrix4f pose = poseStack.last().pose();
		VertexConsumer consumer = bufferSource.getBuffer(MIRROR_PORTAL);

		Direction.Axis axis = facing.getAxis();
		int step = switch (axis) {
			case X -> facing.getStepX();
			case Y -> facing.getStepY();
			case Z -> facing.getStepZ();
		};
		// the original's drawPlane*: 0.01 from the mounting face, inset 3 pixels
		float coord = step > 0 ? 0.01f : 0.99f;
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

	/** The original's translateFromOrientation: the frame's plane against the mounting face, {@code off} out from it. */
	private static void orient(PoseStack poseStack, Direction facing, float off) {
		switch (facing) {
			case DOWN -> {
				poseStack.translate(0.0, 1.0, 1.0);
				poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0f));
			}
			case UP -> poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0f));
			case NORTH -> poseStack.translate(0.0, 0.0, 1.0);
			case SOUTH -> {
				poseStack.translate(1.0, 0.0, 0.0);
				poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f));
			}
			case WEST -> {
				poseStack.translate(1.0, 0.0, 1.0);
				poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0f));
			}
			case EAST -> poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90.0f));
		}
		poseStack.translate(0.0, 0.0, -off);
	}
}

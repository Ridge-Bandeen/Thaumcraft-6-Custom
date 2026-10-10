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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.tiles.devices.TileJarBrain;
import thaumcraft.common.tiles.essentia.TileJarFillable;

/**
 * Shared renderer for every jar variant ({@code JAR}/{@code JAR_VOID}/{@code JAR_BRAIN}) - restored
 * 2026-09-17, re-evaluating both {@link TileJarFillable}'s and {@link TileJarBrain}'s own earlier "drop the
 * animation" cuts (see their own doc comments) per this port's own standing "reevaluate past cuts once their
 * blocker resolves" rule. Real, faithful port of the true original's own real {@code TileJarRenderer}: one
 * class, one {@code instanceof} branch matching the true original's own exact split between the essentia
 * ({@link TileJarFillable}, also covers the void variant) and brain ({@link TileJarBrain}) cases.
 *
 * <p>Essentia jar: a lid brace overlay when {@link TileJarFillable#isBlocked}, a lid extension overlay when a
 * real {@link IEssentiaTransport} neighbor sits directly above, a rotatable aspect-label "sticker" (background
 * quad + a tinted aspect-icon quad, both real, faithful translations of the true original's own real
 * {@code UtilsFX.renderQuadCentered}/{@code drawTag} calls - {@link Aspect#getImage()}/{@link Aspect#getColor()}
 * were already ported early in this whole project with exactly this use in mind) when labelled, and a
 * continuously-scaled liquid-fill box (not a discrete 5-model swap - see {@link TileJarFillable}'s own doc
 * comment) tinted by the held aspect's own colour.
 *
 * <p>Brain jar: the real "looks around, sighs occasionally" idle animation ({@link TileJarBrain#getRota()}/
 * {@link TileJarBrain#getRotb()}, linearly interpolated across partial ticks exactly like the true original's
 * own real formula) plus the same real brine-glass overlay the essentia jar's lid brace reuses geometry from.
 *
 * <p>As the original, the label sticker faces whoever placed or labelled the jar ({@link TileJarFillable#getFacing}),
 * with the small per-jar tilt of the {@code crooked} client option.
 */
public class TileJarRenderer implements BlockEntityRenderer<BlockEntity> {

	private static final ResourceLocation TEX_LABEL = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/models/label.png");
	private static final ResourceLocation TEX_BRAIN = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/models/brain2.png");
	private static final ResourceLocation TEX_BRINE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/models/jarbrine.png");

	private final ModelJar model = new ModelJar();
	private final ModelBrain brain = new ModelBrain();

	public TileJarRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(BlockEntity tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (tile.getLevel() == null) return;
		if (tile instanceof TileJarBrain brainTile) {
			renderBrain(brainTile, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
		} else if (tile instanceof TileJarFillable fillable) {
			renderFillable(fillable, poseStack, bufferSource, packedLight, packedOverlay);
		}
	}

	private void renderFillable(TileJarFillable tile, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		BlockPos pos = tile.getBlockPos();

		poseStack.pushPose();
		poseStack.translate(0.5, 0.01, 0.5);
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));

		if (tile.isBlocked()) {
			VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutout(TEX_BRINE));
			poseStack.pushPose();
			poseStack.scale(1.001f, 1.001f, 1.001f);
			model.renderLidBrace(poseStack, vc, packedLight);
			poseStack.popPose();
		}

		if (tile.getLevel().getBlockEntity(pos.above()) instanceof IEssentiaTransport) {
			VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutout(TEX_BRINE));
			poseStack.pushPose();
			poseStack.scale(0.9f, 1.0f, 0.9f);
			model.renderLidExtension(poseStack, vc, packedLight);
			poseStack.popPose();
		}

		Aspect filter = tile.getAspectFilter();
		if (filter != null) {
			// the crooked option: labels stuck on a little askew, per jar
			float rot = thaumcraft.common.config.ThaumcraftConfig.client(thaumcraft.common.config.ThaumcraftConfig.CROOKED)
					? (filter.getTag().hashCode() + pos.getX() + tile.getFacing().get3DDataValue()) % 4 - 2 : 0;
			poseStack.pushPose();
			switch (tile.getFacing()) {
				case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
				case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
				case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(270.0f));
				default -> {
				}
			}

			VertexConsumer labelConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEX_LABEL));
			poseStack.pushPose();
			poseStack.translate(0.0, -0.4, 0.315);
			poseStack.mulPose(Axis.ZP.rotationDegrees(rot));
			emitCenteredQuad(labelConsumer, poseStack.last(), 0.25f, 0xFFFFFFFF, packedLight, packedOverlay);
			poseStack.popPose();

			VertexConsumer iconConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(filter.getImage()));
			poseStack.pushPose();
			poseStack.translate(0.0, -0.4, 0.316);
			poseStack.mulPose(Axis.ZP.rotationDegrees(rot));
			int tint = FastColor.ARGB32.opaque(filter.getColor());
			emitCenteredQuad(iconConsumer, poseStack.last(), 0.168f, tint, packedLight, packedOverlay);
			poseStack.popPose();
			poseStack.popPose();
		}

		poseStack.popPose();

		if (tile.getAmount() > 0) {
			renderLiquid(tile, poseStack, bufferSource, packedLight, packedOverlay);
		}
	}

	private static final ResourceLocation LIQUID_SPRITE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "blocks/animatedglow");

	/** The original's renderLiquid: a box 0.25-0.75 across, from 0.0625 up to its fill level, each face using the animated
	 * {@code animatedglow} block sprite mapped to the box's bounds as a block face would be, in the aspect's colour, at the
	 * fixed brightness 200. */
	private void renderLiquid(TileJarFillable tile, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		float top = 0.0625f + tile.getAmount() / (float) TileJarFillable.getCapacity() * 0.625f;
		Aspect aspect = tile.getAspect();
		int tint = aspect != null ? FastColor.ARGB32.opaque(aspect.getColor()) : 0xFF000000;
		var sprite = net.minecraft.client.Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(LIQUID_SPRITE);
		VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
		PoseStack.Pose pose = poseStack.last();
		float lo = 0.25f, hi = 0.75f, bottom = 0.0625f;
		int light = 200;
		float ul = sprite.getU(lo), uh = sprite.getU(hi);
		float vlo = sprite.getV(lo), vhi = sprite.getV(hi);
		float vTop = sprite.getV(1.0f - top), vBottom = sprite.getV(1.0f - bottom);
		// -Y and +Y (u from x, v from z)
		face(vc, pose, lo, bottom, lo, ul, vlo, lo, bottom, hi, ul, vhi, hi, bottom, hi, uh, vhi, hi, bottom, lo, uh, vlo, tint, light, packedOverlay, 0, -1, 0);
		face(vc, pose, lo, top, hi, ul, vhi, hi, top, hi, uh, vhi, hi, top, lo, uh, vlo, lo, top, lo, ul, vlo, tint, light, packedOverlay, 0, 1, 0);
		// -Z and +Z (u from x, v from height)
		face(vc, pose, hi, top, lo, uh, vTop, hi, bottom, lo, uh, vBottom, lo, bottom, lo, ul, vBottom, lo, top, lo, ul, vTop, tint, light, packedOverlay, 0, 0, -1);
		face(vc, pose, lo, top, hi, ul, vTop, lo, bottom, hi, ul, vBottom, hi, bottom, hi, uh, vBottom, hi, top, hi, uh, vTop, tint, light, packedOverlay, 0, 0, 1);
		// -X and +X (u from z, v from height)
		face(vc, pose, lo, top, lo, ul, vTop, lo, bottom, lo, ul, vBottom, lo, bottom, hi, uh, vBottom, lo, top, hi, uh, vTop, tint, light, packedOverlay, -1, 0, 0);
		face(vc, pose, hi, top, hi, uh, vTop, hi, bottom, hi, uh, vBottom, hi, bottom, lo, ul, vBottom, hi, top, lo, ul, vTop, tint, light, packedOverlay, 1, 0, 0);
	}

	private static void face(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float u0, float v0, float x1, float y1, float z1,
			float u1, float v1, float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3, int tint, int light,
			int overlay, float nx, float ny, float nz) {
		vertex(vc, pose, x0, y0, z0, u0, v0, tint, light, overlay, nx, ny, nz);
		vertex(vc, pose, x1, y1, z1, u1, v1, tint, light, overlay, nx, ny, nz);
		vertex(vc, pose, x2, y2, z2, u2, v2, tint, light, overlay, nx, ny, nz);
		vertex(vc, pose, x3, y3, z3, u3, v3, tint, light, overlay, nx, ny, nz);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose,
			float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
			int tint, int packedLight, int packedOverlay, float nx, float ny, float nz) {
		vertex(vc, pose, x0, y0, z0, 0.0f, 1.0f, tint, packedLight, packedOverlay, nx, ny, nz);
		vertex(vc, pose, x1, y1, z1, 0.0f, 0.0f, tint, packedLight, packedOverlay, nx, ny, nz);
		vertex(vc, pose, x2, y2, z2, 1.0f, 0.0f, tint, packedLight, packedOverlay, nx, ny, nz);
		vertex(vc, pose, x3, y3, z3, 1.0f, 1.0f, tint, packedLight, packedOverlay, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
			int tint, int packedLight, int packedOverlay, float nx, float ny, float nz) {
		vc.addVertex(pose, x, y, z)
				.setColor(tint)
				.setUv(u, v)
				.setOverlay(packedOverlay)
				.setLight(packedLight)
				.setNormal(pose, nx, ny, nz);
	}

	private static void emitCenteredQuad(VertexConsumer vc, PoseStack.Pose pose, float halfSize, int tint, int packedLight, int packedOverlay) {
		vertex(vc, pose, -halfSize, halfSize, 0.0f, 0.0f, 0.0f, tint, packedLight, packedOverlay, 0.0f, 0.0f, 1.0f);
		vertex(vc, pose, -halfSize, -halfSize, 0.0f, 0.0f, 1.0f, tint, packedLight, packedOverlay, 0.0f, 0.0f, 1.0f);
		vertex(vc, pose, halfSize, -halfSize, 0.0f, 1.0f, 1.0f, tint, packedLight, packedOverlay, 0.0f, 0.0f, 1.0f);
		vertex(vc, pose, halfSize, halfSize, 0.0f, 1.0f, 0.0f, tint, packedLight, packedOverlay, 0.0f, 0.0f, 1.0f);
	}

	private void renderBrain(TileJarBrain tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0.01, 0.5);
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));

		float bob = Mth.sin(Minecraft.getInstance().player.tickCount / 14.0f) * 0.03f + 0.03f;
		poseStack.pushPose();
		poseStack.translate(0.0, -0.8 + bob, 0.0);
		float diff = wrapRadians(tile.getRota() - tile.getRotb());
		float yaw = tile.getRotb() + diff * partialTick;
		poseStack.mulPose(Axis.YP.rotationDegrees(yaw * 180.0f / Mth.PI));
		poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
		poseStack.scale(0.4f, 0.4f, 0.4f);
		VertexConsumer brainConsumer = bufferSource.getBuffer(RenderType.entityCutout(TEX_BRAIN));
		brain.render(poseStack, brainConsumer, packedLight);
		poseStack.popPose();

		VertexConsumer brineConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEX_BRINE));
		model.renderBrine(poseStack, brineConsumer, packedLight);

		poseStack.popPose();
	}

	private static float wrapRadians(float angle) {
		while (angle >= Mth.PI) {
			angle -= Mth.TWO_PI;
		}
		while (angle < -Mth.PI) {
			angle += Mth.TWO_PI;
		}
		return angle;
	}
}

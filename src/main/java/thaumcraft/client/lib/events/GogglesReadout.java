package thaumcraft.client.lib.events;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.items.WornGearHelper;

/**
 * The Goggles of Revealing readout: while looking at a block whose tile is an {@link IAspectContainer} that holds
 * aspects (crucible, jars, alembic, ...), and wearing something that shows in-game popups, the aspect icons and
 * their amounts float above it - restored 2026-09-25 (the playtest: "nothing different with the goggles on"; the
 * original's {@code RenderEventHandler#drawTagsOnContainer} was still an uncompiled 1.12 file).
 *
 * <p>Same layout as the original: icons {@code tagscale} blocks wide (growing from 0 to 0.3 while you keep looking,
 * reset when you look away), up to 5 per row, rows stacking downward, centred over the block (over its top face when
 * there's room, otherwise beside the face you're looking at), drawn without depth testing so they show through
 * the block itself. Each icon is tinted by its aspect colour at 75% alpha with the amount at its bottom-right corner.
 * Not ported: the extended text lines ({@code IGogglesDisplayExtended}, no implementers exist yet) and note-block
 * readouts.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class GogglesReadout {

	private static final float MAX_SCALE = 0.3f;
	private static final int ROW_SIZE = 5;

	private static float tagScale;

	private GogglesReadout() {
	}

	@SubscribeEvent
	static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;

		AspectList aspects = null;
		BlockHitResult hit = null;
		if (WornGearHelper.hasGoggles(mc.player) && mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
			BlockEntity tile = mc.level.getBlockEntity(block.getBlockPos());
			if (tile instanceof IAspectContainer container && container.getAspects() != null && container.getAspects().size() > 0) {
				aspects = container.getAspects();
				hit = block;
			}
		}
		if (aspects == null) {
			tagScale = 0.0f;
			return;
		}
		if (tagScale < MAX_SCALE) {
			tagScale = Math.min(MAX_SCALE, tagScale + 0.031f - tagScale / 10.0f);
		}
		draw(mc, event, hit.getBlockPos(), aspects, mc.level.getBlockState(hit.getBlockPos().above()).isAir() ? net.minecraft.core.Direction.UP : hit.getDirection());
	}

	private static void draw(Minecraft mc, RenderLevelStageEvent event, BlockPos pos, AspectList tags, net.minecraft.core.Direction dir) {
		Camera camera = event.getCamera();
		Vec3 cam = camera.getPosition();
		boolean above = dir == net.minecraft.core.Direction.UP;
		double baseY = pos.getY() + (above ? 0.4 : 0.0);

		// Camera-relative positions only: by AFTER_PARTICLES the view rotation is already in RenderSystem's model-view
		// matrix (the shader applies it), so baking it into this pose stack too - as the first version did - applied it
		// twice and threw every icon off-screen ("nothing different with the goggles on", 2026-09-25 playtest).
		PoseStack poseStack = new PoseStack();

		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.disableCull();

		Aspect[] all = tags.getAspects();
		// Two passes: every icon first, then every amount on top - drawn one tag at a time, the next icon painted over the
		// previous amount ("clipping", playtest 2026-09-25).
		for (int pass = 0; pass < 2; pass++) {
			int left = all.length;
			int current = 0;
			float shiftY = 0.0f;
			for (Aspect tag : all) {
				int div = Math.min(left, ROW_SIZE);
				if (current >= ROW_SIZE) {
					current = 0;
					shiftY -= tagScale * 1.05f;
					left -= ROW_SIZE;
					if (left < ROW_SIZE) div = left % ROW_SIZE;
				}
				float shift = (current - div / 2.0f + 0.5f) * tagScale * 4.0f * tagScale;

				poseStack.pushPose();
				poseStack.translate(pos.getX() + 0.5 - cam.x + tagScale * 2.0f * dir.getStepX(),
						baseY - shiftY + 0.5 - cam.y + tagScale * 2.0f * dir.getStepY(),
						pos.getZ() + 0.5 - cam.z + tagScale * 2.0f * dir.getStepZ());
				poseStack.mulPose(camera.rotation());
				poseStack.translate(shift, 0.0, 0.0);
				poseStack.scale(tagScale, tagScale, tagScale);

				if (pass == 0) {
					int color = tag.getColor();
					drawIcon(poseStack.last().pose(), tag, ((color >> 16) & 255) / 255.0f, ((color >> 8) & 255) / 255.0f, (color & 255) / 255.0f);
				} else {
					drawAmount(mc, poseStack, Integer.toString(tags.getAmount(tag)));
				}
				poseStack.popPose();
				current++;
			}
		}

		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.disableBlend();
	}

	private static void drawIcon(Matrix4f matrix, Aspect tag, float r, float g, float b) {
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, tag.getImage());
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		buffer.addVertex(matrix, -0.5f, -0.5f, 0.0f).setUv(0.0f, 1.0f).setColor(r, g, b, 0.75f);
		buffer.addVertex(matrix, 0.5f, -0.5f, 0.0f).setUv(1.0f, 1.0f).setColor(r, g, b, 0.75f);
		buffer.addVertex(matrix, 0.5f, 0.5f, 0.0f).setUv(1.0f, 0.0f).setColor(r, g, b, 0.75f);
		buffer.addVertex(matrix, -0.5f, 0.5f, 0.0f).setUv(0.0f, 0.0f).setColor(r, g, b, 0.75f);
		BufferUploader.drawWithShader(buffer.buildOrThrow());
	}

	/** The amount, bottom-right of the icon: white with the game's own drop shadow (the original drew a dark copy offset behind it). */
	private static void drawAmount(Minecraft mc, PoseStack poseStack, String text) {
		Font font = mc.font;
		poseStack.pushPose();
		poseStack.translate(0.5f, -0.5f, -0.01f);
		// Text space runs right/down; the billboard runs right/up. Only y flips - the original flipped x too, but its pose had
		// already been turned half way round, and the same flip here drew the digits upside down (playtest 2026-09-25).
		poseStack.scale(0.05f, -0.05f, 0.05f);
		float x = -font.width(text);
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		font.drawInBatch(text, x, -8.0f, 0xFFFFFF, true, poseStack.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
		buffers.endBatch();
		poseStack.popPose();
	}
}

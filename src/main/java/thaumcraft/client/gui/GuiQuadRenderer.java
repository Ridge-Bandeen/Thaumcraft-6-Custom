package thaumcraft.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared real "tinted, optionally-additive 2D screen-space quad" draw, extracted 2026-09-24 from {@link
 * KnowledgeGainOverlay}'s own original private copy once {@link FXGenericGui} became this technique's second
 * real caller. A manual {@link Tesselator}/{@link BufferBuilder} draw against the current {@link GuiGraphics}
 * pose stack (translate/rotate already applied by the caller) - {@link GuiGraphics#blit} alone can't do this,
 * since it has no tint parameter and always uses standard alpha blending, never additive.
 */
final class GuiQuadRenderer {

	private GuiQuadRenderer() {
	}

	/** The original's {@code UtilsFX#renderQuadCentered} does not map the texture upright: its vertex order turns it a quarter turn
	 * (u follows y, v follows x). The flying knowledge-gain icons were designed against that, and drawn upright here they came out
	 * sideways (playtest 2026-09-26). */
	static void renderQuadCenteredQuarterTurned(GuiGraphics graphics, ResourceLocation texture, int gridSize, int frame,
			float scale, float r, float g, float b, float alpha, boolean additive) {
		renderQuadCentered(graphics, texture, gridSize, frame, scale, r, g, b, alpha, additive, true);
	}

	static void renderQuadCentered(GuiGraphics graphics, ResourceLocation texture, int gridSize, int frame,
			float scale, float r, float g, float b, float alpha, boolean additive) {
		renderQuadCentered(graphics, texture, gridSize, frame, scale, r, g, b, alpha, additive, false);
	}

	private static void renderQuadCentered(GuiGraphics graphics, ResourceLocation texture, int gridSize, int frame,
			float scale, float r, float g, float b, float alpha, boolean additive, boolean quarterTurn) {
		if (scale <= 0.0f) return;
		int fx = frame % gridSize;
		int fy = frame / gridSize;
		float u0 = fx / (float) gridSize;
		float u1 = u0 + 1.0f / gridSize;
		float v0 = fy / (float) gridSize;
		float v1 = v0 + 1.0f / gridSize;
		float h = scale / 2.0f;

		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.enableBlend();
		if (additive) {
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		} else {
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
		}
		PoseStack.Pose pose = graphics.pose().last();
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		if (quarterTurn) {
			buffer.addVertex(pose, -h, h, 0).setUv(u1, v1).setColor(r, g, b, alpha);
			buffer.addVertex(pose, h, h, 0).setUv(u1, v0).setColor(r, g, b, alpha);
			buffer.addVertex(pose, h, -h, 0).setUv(u0, v0).setColor(r, g, b, alpha);
			buffer.addVertex(pose, -h, -h, 0).setUv(u0, v1).setColor(r, g, b, alpha);
		} else {
			buffer.addVertex(pose, -h, h, 0).setUv(u0, v1).setColor(r, g, b, alpha);
			buffer.addVertex(pose, h, h, 0).setUv(u1, v1).setColor(r, g, b, alpha);
			buffer.addVertex(pose, h, -h, 0).setUv(u1, v0).setColor(r, g, b, alpha);
			buffer.addVertex(pose, -h, -h, 0).setUv(u0, v0).setColor(r, g, b, alpha);
		}
		BufferUploader.drawWithShader(buffer.buildOrThrow());
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}
}

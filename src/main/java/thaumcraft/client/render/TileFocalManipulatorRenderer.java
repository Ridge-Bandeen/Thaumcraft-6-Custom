package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.items.resources.ItemCrystalEssence;
import thaumcraft.common.tiles.crafting.TileFocalManipulator;

/**
 * Renders {@link TileFocalManipulator}'s real work-in-progress state - real port of the original's own
 * {@code TileFocalManipulatorRenderer}: the focus being assembled spins in place above the block, and while a
 * craft is charging (real {@link TileFocalManipulator#crystalsSync} entries staged by {@link
 * TileFocalManipulator#startCraft}), one real {@link ItemCrystalEssence} per required aspect orbits around
 * it, each trailing two small aspect-tinted light-ray cones - same technique
 * {@link TileInfusionMatrixRenderer}'s own halo uses ({@link RenderType#dragonRays()}, ported from the
 * original's raw {@code GL_TRIANGLE_FAN} spike), just one spike per call instead of a burst, tinted by the
 * real {@link Aspect#getColor()} instead of white/magenta.
 *
 * As the original: the focus and crystals are drawn as dropped items would be (lifted by a quarter of their ground
 * scale and bobbed by their hover phase - the focus's swaying with {@code sin(t / 14)}, the crystals' fixed at 0), the
 * rays use the original's turns (and its {@code java.util.Random} seeds) inside the crystal's half scale, and beside
 * each crystal sits a small additive glow from the particle sheet (frames 320-335 of its 64x64 grid, in the aspect's
 * colour at 0.66 alpha), facing the camera.
 */
public class TileFocalManipulatorRenderer implements BlockEntityRenderer<TileFocalManipulator> {

	public TileFocalManipulatorRenderer(BlockEntityRendererProvider.Context context) {
	}

	private static final net.minecraft.resources.ResourceLocation PARTICLES =
			net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/particles.png");

	@Override
	public void render(TileFocalManipulator tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		Minecraft mc = Minecraft.getInstance();
		int age = mc.player != null ? mc.player.tickCount : 0;
		float ticks = age + partialTick;

		ItemStack focus = tile.getFocus();
		if (!focus.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0.5, 0.8, 0.5);
			poseStack.mulPose(Axis.YP.rotationDegrees(ticks % 360.0f));
			renderAsDropped(mc, tile.getLevel(), focus, Mth.sin(ticks / 14.0f) * 0.2f + 0.2f, poseStack, bufferSource, packedLight, packedOverlay);
			poseStack.popPose();
		}

		Aspect[] aspects = tile.crystalsSync.getAspects();
		if (aspects.length == 0) return;

		float anglePerCrystal = (float) (360 / aspects.length);
		for (int i = 0; i < aspects.length; i++) {
			Aspect aspect = aspects[i];
			float angle = (ticks % 720.0f) / 2.0f + anglePerCrystal * i;
			float bob = Mth.sin((ticks + i * 10) / 12.0f) * 0.02f + 0.02f;
			int color = aspect.getColor();
			float r = ((color >> 16) & 0xFF) / 255.0f;
			float g = ((color >> 8) & 0xFF) / 255.0f;
			float b = (color & 0xFF) / 255.0f;

			// the glow beside the crystal
			poseStack.pushPose();
			poseStack.translate(0.5, 1.3, 0.5);
			poseStack.mulPose(Axis.YP.rotationDegrees(angle));
			poseStack.translate(0.0, bob, 0.4);
			poseStack.mulPose(Axis.YP.rotationDegrees(-angle));
			poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
			int frame = 320 + age % 16;
			float u0 = (frame % 64) / 64.0f;
			float u1 = u0 + 1.0f / 64.0f;
			float v0 = (frame / 64) / 64.0f;
			float v1 = v0 + 1.0f / 64.0f;
			float s = 0.175f;
			int glow = FastColor.ARGB32.color((int) (0.66f * 255.0f), (int) (r * 255.0f), (int) (g * 255.0f), (int) (b * 255.0f));
			VertexConsumer gvc = bufferSource.getBuffer(ShieldRunesFx.additive(PARTICLES));
			PoseStack.Pose gp = poseStack.last();
			float[][] corners = { { -s, -s, u1, v1 }, { -s, s, u1, v0 }, { s, s, u0, v0 }, { s, -s, u0, v1 } };
			for (float[] c : corners) {
				gvc.addVertex(gp, c[0], c[1], 0.0f).setColor(glow).setUv(c[2], c[3])
						.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(gp, 0.0f, 1.0f, 0.0f);
			}
			poseStack.popPose();

			// the crystal and its two rays
			poseStack.pushPose();
			poseStack.translate(0.5, 1.05, 0.5);
			poseStack.mulPose(Axis.YP.rotationDegrees(angle));
			poseStack.translate(0.0, bob, 0.4);
			poseStack.scale(0.5f, 0.5f, 0.5f);
			drawRay(poseStack, bufferSource, angle, i, bob, r, g, b, ticks);
			drawRay(poseStack, bufferSource, angle, (i + 1) * 5, bob, r, g, b, ticks);
			renderAsDropped(mc, tile.getLevel(), ItemCrystalEssence.of(aspect), 0.0f, poseStack, bufferSource, packedLight, packedOverlay);
			poseStack.popPose();
		}
	}

	/** The original drew these as a fresh EntityItem: age 0, so lifted by its hover bob plus a quarter of its ground
	 * scale, and turned by its hover phase. */
	static void renderAsDropped(Minecraft mc, net.minecraft.world.level.Level level, ItemStack stack, float hoverStart, PoseStack poseStack,
			MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		var model = mc.getItemRenderer().getModel(stack, level, null, 0);
		float groundScale = model.getTransforms().getTransform(ItemDisplayContext.GROUND).scale.y();
		poseStack.pushPose();
		poseStack.translate(0.0, Mth.sin(hoverStart) * 0.1f + 0.1f + 0.25f * groundScale, 0.0);
		poseStack.mulPose(Axis.YP.rotation(hoverStart));
		mc.getItemRenderer().render(stack, ItemDisplayContext.GROUND, false, poseStack, bufferSource, packedLight, packedOverlay, model);
		poseStack.popPose();
	}

	/** The original's renderRay: a three-sided additive fan, turned as the original turned it. */
	private void drawRay(PoseStack poseStack, MultiBufferSource bufferSource, float angle, int num, float lift, float r, float g, float b, float ticks) {
		java.util.Random random = new java.util.Random(187L + num * num);
		float pan = Mth.sin((ticks + num * 10) / 15.0f) * 15.0f;
		float aperture = Mth.sin((ticks + num * 10) / 14.0f) * 2.0f;

		poseStack.pushPose();
		poseStack.translate(0.0, 0.475f + lift, 0.0);
		poseStack.mulPose(Axis.XN.rotationDegrees(90.0f));
		poseStack.mulPose(Axis.YP.rotationDegrees(angle));
		poseStack.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0f));
		poseStack.mulPose(Axis.XP.rotationDegrees(pan));

		float ramp = 30.0f / (Math.min(ticks, 10.0f) / 10.0f);
		float length = (random.nextFloat() * 20.0f + 10.0f) / ramp;
		float width = (random.nextFloat() * 4.0f + 6.0f + aperture) / ramp;
		int apexColor = FastColor.ARGB32.color((int) (0.66f * 255.0f), (int) (r * 255.0f), (int) (g * 255.0f), (int) (b * 255.0f));
		int rimColor = FastColor.ARGB32.color(0, (int) (r * 255.0f), (int) (g * 255.0f), (int) (b * 255.0f));

		Vector3f apex = new Vector3f(0.0f, 0.0f, 0.0f);
		Vector3f v1 = new Vector3f(-0.8f * width, length, -0.5f * width);
		Vector3f v2 = new Vector3f(0.8f * width, length, -0.5f * width);
		Vector3f v3 = new Vector3f(0.0f, length, width);
		VertexConsumer vc = bufferSource.getBuffer(RenderType.dragonRays());
		PoseStack.Pose pose = poseStack.last();
		vc.addVertex(pose, apex).setColor(apexColor);
		vc.addVertex(pose, v1).setColor(rimColor);
		vc.addVertex(pose, v2).setColor(rimColor);
		vc.addVertex(pose, apex).setColor(apexColor);
		vc.addVertex(pose, v2).setColor(rimColor);
		vc.addVertex(pose, v3).setColor(rimColor);
		vc.addVertex(pose, apex).setColor(apexColor);
		vc.addVertex(pose, v3).setColor(rimColor);
		vc.addVertex(pose, v1).setColor(rimColor);
		poseStack.popPose();
	}
}

package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
 * <b>Real, deliberate scope cut:</b> the original also billboarded a small glowing particle-sheet quad next
 * to each orbiting crystal (a 1.12.2 particle-atlas cell, animated by cycling through 16 frames) - a minor
 * decorative accent layered on top of the crystal item and its two light rays, not an information-bearing
 * one. No modern equivalent atlas/cell exists to sample, and building one from scratch for a single glow dot
 * isn't worth it next to the two elements that actually communicate real state (which item, which aspects) -
 * left out, see the UI-visual-parity checklist for this port's own tracking note on it.
 */
public class TileFocalManipulatorRenderer implements BlockEntityRenderer<TileFocalManipulator> {

	public TileFocalManipulatorRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileFocalManipulator tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		Minecraft mc = Minecraft.getInstance();
		float ticks = (mc.player != null ? mc.player.tickCount : 0) + partialTick;

		ItemStack focus = tile.getFocus();
		if (!focus.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0.5, 0.8, 0.5);
			poseStack.mulPose(Axis.YP.rotationDegrees(ticks % 360.0f));
			mc.getItemRenderer().renderStatic(focus, ItemDisplayContext.GROUND, packedLight, packedOverlay, poseStack, bufferSource, tile.getLevel(), 0);
			poseStack.popPose();
		}

		Aspect[] aspects = tile.crystalsSync.getAspects();
		if (aspects.length == 0) return;

		float anglePerCrystal = 360.0f / aspects.length;
		for (int i = 0; i < aspects.length; i++) {
			Aspect aspect = aspects[i];
			float angle = (ticks % 720.0f) / 2.0f + anglePerCrystal * i;
			float bob = Mth.sin((ticks + i * 10) / 12.0f) * 0.02f + 0.02f;
			int color = aspect.getColor();
			float r = ((color >> 16) & 0xFF) / 255.0f;
			float g = ((color >> 8) & 0xFF) / 255.0f;
			float b = (color & 0xFF) / 255.0f;

			poseStack.pushPose();
			poseStack.translate(0.5, 1.05, 0.5);
			poseStack.mulPose(Axis.YP.rotationDegrees(angle));
			poseStack.translate(0.0, bob, 0.4);

			drawRay(poseStack, bufferSource, ticks, i, r, g, b);
			drawRay(poseStack, bufferSource, ticks, (i + 1) * 5, r, g, b);

			poseStack.mulPose(Axis.YP.rotationDegrees(-angle));
			poseStack.scale(0.5f, 0.5f, 0.5f);
			mc.getItemRenderer().renderStatic(ItemCrystalEssence.of(aspect), ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, tile.getLevel(), 0);
			poseStack.popPose();
		}
	}

	/** One light-ray spike, direct port of the original's own {@code renderRay} - see this class's own doc
	 * comment for the real {@link RenderType#dragonRays()} precedent this is built on. Called from within the
	 * already-positioned-and-oriented crystal transform, same as the original. */
	private void drawRay(PoseStack poseStack, MultiBufferSource bufferSource, float ticks, int seed, float r, float g, float b) {
		RandomSource random = RandomSource.create(187L + (long) seed * seed);
		float pan = Mth.sin((ticks + seed * 10) / 15.0f) * 15.0f;
		float aperture = Mth.sin((ticks + seed * 10) / 14.0f) * 2.0f;
		float scaleRamp = Math.min(ticks, 10.0f) / 10.0f;

		poseStack.pushPose();
		poseStack.translate(0.0, 0.475, 0.0);
		Quaternionf rotation = new Quaternionf()
				.rotateY(Mth.PI + random.nextFloat() * Mth.TWO_PI)
				.rotateX(pan * Mth.DEG_TO_RAD);
		poseStack.mulPose(rotation);

		float length = (random.nextFloat() * 20.0f + 10.0f) * scaleRamp / 30.0f;
		float width = (random.nextFloat() * 4.0f + 6.0f + aperture) * scaleRamp / 30.0f;
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

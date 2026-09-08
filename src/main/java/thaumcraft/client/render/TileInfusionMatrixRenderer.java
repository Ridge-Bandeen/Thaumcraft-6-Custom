package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;

/**
 * Renders {@link TileInfusionMatrix}'s two genuinely dynamic extras - real port of the original's own
 * {@code TileInfusionMatrixRenderer}, scoped down in one real, deliberate way (see below).
 *
 * <b>What's real and ported:</b> a violet emissive glow pulsing over the altar's 8 corner fragments while
 * {@link TileInfusionMatrix#active}, and a burst of small colored light-spikes ("halo") while
 * {@link TileInfusionMatrix#crafting} - both using {@link TileInfusionMatrix#getActivatedTick()}/
 * {@link TileInfusionMatrix#getCraftTick()} for the same real 0-1 startup ease-in and halo-intensity ramps
 * the original computed from its own {@code startUp}/{@code craftCount} fields (see those getters' own doc
 * comments for why they're derived client-side instead of synced). The halo itself is a direct port of the
 * original's raw {@code GL_TRIANGLE_FAN} spike-burst technique, rebuilt on the real modern equivalent vanilla
 * already ships for the exact same shape: {@link RenderType#dragonRays()}, the Ender Dragon's own death-ray
 * burst effect (see {@code EnderDragonRenderer#renderRays} - same random-quaternion-per-spike technique, same
 * apex-to-3-rim-points geometry, even the same real transparent-magenta rim color, right down to the literal
 * hex value). No pillar-texture-swap dead code (see {@link TileInfusionMatrix}'s own doc comment on why the
 * pillar tier system isn't ported) - always the plain "normal" texture.
 *
 * <b>Real, deliberate scope cut:</b> the original's own base 8-cube shape - the whole altar's core visual
 * identity - was <i>always</i> TESR-drawn, with a subtle per-corner sine-wave jitter while active. This port's
 * {@code infusion_matrix.json} block model already bakes that same 8-corner shattered-cube shape statically
 * (matching this renderer's own {@link #CORNERS} table almost exactly - it was clearly built as a stand-in
 * for this renderer before this renderer existed). Redrawing the same 8 cubes here too, just to add a ~0.01-
 * block jitter, would double-render the static model's own geometry and z-fight/ghost against it; properly
 * replacing it would mean suppressing the static model's render (a real, separate, higher-risk change to
 * {@code BlockInfusionMatrix} itself, touching its item-model/break-particle behavior along with it) for a
 * purely cosmetic wobble that carries no gameplay information. Left as a real, static rest-pose instead - see
 * the UI-visual-parity checklist for this port's own tracking note on it.
 */
public class TileInfusionMatrixRenderer implements BlockEntityRenderer<TileInfusionMatrix> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/blocks/infuser_normal.png");

	/** The 8 corner-fragment origins (pixel units, 0-16), matching {@code infusion_matrix.json}'s own 8
	 * elements exactly - every combination of {0 or 9} per axis, each a 7x7x7 box. */
	private static final int[][] CORNERS = {
			{0, 0, 0}, {9, 0, 9}, {9, 9, 0}, {0, 9, 9}, {9, 9, 9}, {0, 0, 9}, {0, 9, 0}, {9, 0, 0}
	};

	private final ModelPart glowCorner;

	public TileInfusionMatrixRenderer(BlockEntityRendererProvider.Context context) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("glow", CubeListBuilder.create().texOffs(0, 32).addBox(0.0f, 0.0f, 0.0f, 7, 7, 7), PartPose.ZERO);
		glowCorner = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("glow");
	}

	@Override
	public void render(TileInfusionMatrix tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		Minecraft mc = Minecraft.getInstance();
		float ticks = (mc.player != null ? mc.player.tickCount : 0) + partialTick;

		if (tile.active) {
			renderGlow(tile, ticks, poseStack, bufferSource, packedLight, packedOverlay);
		}
		if (tile.crafting) {
			drawHalo(tile, ticks, poseStack, bufferSource);
		}
	}

	private void renderGlow(TileInfusionMatrix tile, float ticks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		float startup = Math.min(1.0f, tile.getActivatedTick() / 40.0f);
		VertexConsumer vc = bufferSource.getBuffer(RenderType.eyes(TEXTURE));
		for (int i = 0; i < CORNERS.length; i++) {
			int[] c = CORNERS[i];
			float alpha = (Mth.sin((ticks + c[0] * 0.2f + c[1] * 0.3f + c[2] * 0.4f) / 4.0f) * 0.1f + 0.2f) * startup;
			int color = FastColor.ARGB32.color((int) (alpha * 255.0f), 204, 26, 255);
			poseStack.pushPose();
			poseStack.translate(c[0] / 16.0, c[1] / 16.0, c[2] / 16.0);
			glowCorner.render(poseStack, vc, packedLight, packedOverlay, color);
			poseStack.popPose();
		}
	}

	/** Direct port of the original's own {@code drawHalo}, rebuilt on {@link RenderType#dragonRays()} - see
	 * this class's own doc comment for why that's a real, exact-match vanilla precedent, not an approximation. */
	private void drawHalo(TileInfusionMatrix tile, float ticks, PoseStack poseStack, MultiBufferSource bufferSource) {
		int craftTick = tile.getCraftTick();
		boolean fancy = Minecraft.getInstance().options.graphicsMode().get() != GraphicsStatus.FAST;
		int spikes = fancy ? 20 : 10;
		float fade = craftTick / 500.0f;
		float scaleRamp = Math.min(craftTick, 50) / 50.0f;

		RandomSource random = RandomSource.create(245L);
		VertexConsumer vc = bufferSource.getBuffer(RenderType.dragonRays());
		int apexColor = FastColor.ARGB32.color((int) (255.0f * (1.0f - fade)), 255, 255, 255);
		Vector3f apex = new Vector3f(0.0f, 0.0f, 0.0f);
		Vector3f v1 = new Vector3f();
		Vector3f v2 = new Vector3f();
		Vector3f v3 = new Vector3f();
		Quaternionf rotation = new Quaternionf();

		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		for (int i = 0; i < spikes; i++) {
			rotation.rotationXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI)
					.rotateXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI + fade * Mth.TWO_PI);
			poseStack.pushPose();
			poseStack.mulPose(rotation);
			float length = (random.nextFloat() * 20.0f + 5.0f) * scaleRamp / 20.0f;
			float width = (random.nextFloat() * 2.0f + 1.0f) * scaleRamp / 20.0f;
			v1.set(-0.866f * width, length, -0.5f * width);
			v2.set(0.866f * width, length, -0.5f * width);
			v3.set(0.0f, length, width);
			PoseStack.Pose pose = poseStack.last();
			vc.addVertex(pose, apex).setColor(apexColor);
			vc.addVertex(pose, v1).setColor(0x00FF00FF);
			vc.addVertex(pose, v2).setColor(0x00FF00FF);
			vc.addVertex(pose, apex).setColor(apexColor);
			vc.addVertex(pose, v2).setColor(0x00FF00FF);
			vc.addVertex(pose, v3).setColor(0x00FF00FF);
			vc.addVertex(pose, apex).setColor(apexColor);
			vc.addVertex(pose, v3).setColor(0x00FF00FF);
			vc.addVertex(pose, v1).setColor(0x00FF00FF);
			poseStack.popPose();
		}
		poseStack.popPose();
	}
}

package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;

/**
 * Real, faithful geometry+animation translation of the original's own {@code ModelTaintSeed} (backed by its
 * own {@code ModelRendererTaintSeed} - a real recursive parent-child chain, same shape {@link ModelTaintacle}
 * uses, but with a real per-segment PULSING scale instead of a flat taper, and the chain narrows from
 * 8x8x8 cuboids down to 4x4x4 ones two-thirds of the way along). Real, fixed 8-segment chain (the original
 * never parameterizes this model's own length, unlike {@code ModelTaintacle}) shared by both {@link
 * EntityTaintSeed} and {@code EntityTaintSeedPrime} - only the real entity-driven outer scale differs
 * between the two, matching the original's own real one-model-two-registrations shape.
 *
 * The original's own per-segment pulse used a real {@code static} mutable counter ({@code q}, 0-7, wrapping)
 * incremented once per recursive call to stagger each segment's own sine phase - real, but fragile (shared
 * mutable state across every instance and every nested call). Since the counter always advances in lockstep
 * with the real recursion order (one call per segment, always resetting after exactly 8 calls per model),
 * its value at segment {@code k} is always exactly {@code k} - so this port computes that same real phase
 * directly from the loop index instead, the same real visual result with no shared mutable state.
 *
 * Real, documented simplification: {@code attackAnim} (a real synced float the original bumped via a status
 * event on a successful attack, then let decay) isn't ported on {@link EntityTaintSeed} yet, same real gap
 * {@code EntityTaintacle}'s own {@code flailIntensity} carries - modeled here as a constant 0, so the real
 * idle sway/pulse/rise animation is still fully real, just without the extra post-attack twitch.
 */
public class ModelTaintSeed extends EntityModel<EntityTaintSeed> {

	private static final int LENGTH = 8;
	private static final float BASE_HEIGHT = 1.6f;

	private final ModelPart segmentRoot;
	private final ModelPart segmentBig;
	private final ModelPart segmentTransition;
	private final ModelPart segmentSmall;

	private final float[] segRotX = new float[LENGTH - 1];
	private final float[] segRotZ = new float[LENGTH - 1];
	private float ageInTicks;
	private float riseTranslateY;
	private float bodyScale;

	public ModelTaintSeed() {
		super(RenderType::entityCutout);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("segment_root", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -4.0f, -4.0f, 8, 8, 8), PartPose.ZERO);
		root.addOrReplaceChild("segment_big", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0f, -4.0f, -4.0f, 8, 8, 8), PartPose.ZERO);
		root.addOrReplaceChild("segment_transition", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0f, -2.0f, -2.0f, 4, 4, 4), PartPose.ZERO);
		root.addOrReplaceChild("segment_small", CubeListBuilder.create().texOffs(0, 56).addBox(-2.0f, -2.0f, -2.0f, 4, 4, 4), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
		segmentRoot = baked.getChild("segment_root");
		segmentBig = baked.getChild("segment_big");
		segmentTransition = baked.getChild("segment_transition");
		segmentSmall = baked.getChild("segment_small");
	}

	@Override
	public void setupAnim(EntityTaintSeed entity, float limbSwing, float limbSwingAmount, float rawAgeInTicks, float netHeadYaw, float headPitch) {
		this.ageInTicks = rawAgeInTicks;
		float ht = entity.hurtTime / 200.0f;
		float flail = 0.1f;
		float mod = 0.0125f; // same real near-constant simplification ModelTaintacle uses - see its own doc comment.
		float fi = (flail + (ht > 0.0f ? mod : -mod)) * 3.0f;
		for (int k = 0; k < LENGTH - 1; k++) {
			segRotX[k] = 0.1f / fi * Mth.sin(rawAgeInTicks * 0.06f - k / 2.0f) / 5.0f + ht;
			segRotZ[k] = 0.1f / fi * Mth.sin(rawAgeInTicks * 0.05f - k / 2.0f) / 5.0f;
		}

		float bbHeight = entity.getBbHeight();
		float hc = bbHeight * 10.0f;
		float rise = 0.0f;
		if (entity.tickCount < hc) {
			rise = (hc - entity.tickCount) / hc * bbHeight;
		}
		riseTranslateY = (bbHeight == 3.0f ? 0.6f : 1.2f) + rise;
		bodyScale = bbHeight / 2.0f;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		poseStack.pushPose();
		poseStack.translate(0.0, riseTranslateY, 0.0);
		poseStack.scale(bodyScale, bodyScale, bodyScale);

		poseStack.pushPose();
		poseStack.translate(0.0, 12.0 / 16.0, 0.0);
		segmentRoot.render(poseStack, buffer, packedLight, packedOverlay, color);
		for (int k = 0; k < LENGTH - 1; k++) {
			float qq = Mth.PI * (k / 7.0f);
			float pulse = Mth.sin(ageInTicks / 12.0f - qq) * 0.33f;
			pulse *= pulse;
			float scale = BASE_HEIGHT - Mth.sin(qq);
			poseStack.scale(scale + pulse, scale * 0.9f, scale + pulse);

			boolean big = k < LENGTH - 4;
			double pivot = big ? -8.0 / 16.0 : (k == LENGTH - 4 ? -8.0 / 16.0 : -4.0 / 16.0);
			poseStack.translate(0.0, pivot, 0.0);
			poseStack.mulPose(Axis.ZP.rotation(segRotZ[k]));
			poseStack.mulPose(Axis.XP.rotation(segRotX[k]));

			ModelPart segment = big ? segmentBig : (k == LENGTH - 4 ? segmentTransition : segmentSmall);
			segment.render(poseStack, buffer, packedLight, packedOverlay, color);
		}
		poseStack.popPose();
		poseStack.popPose();
	}
}

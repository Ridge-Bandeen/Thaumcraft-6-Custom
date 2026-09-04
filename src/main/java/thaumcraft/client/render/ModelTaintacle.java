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
import thaumcraft.common.entities.monster.tainted.EntityTaintacle;

/**
 * Real, faithful geometry+animation translation of the original's own {@code ModelTaintacle} (backed by its
 * own {@code ModelRendererTaintacle} - a real recursive parent-child chain of cuboids where every level down
 * the chain draws a real fraction smaller than its parent, capped by an orb+wide-base pair at the very tip).
 * That per-level cumulative taper has no modern {@link ModelPart} hierarchy equivalent (a real vanilla parent-
 * child pose doesn't inject an extra scale factor at each level), so this walks the chain by hand every
 * frame in {@link #renderToBuffer} instead - a real, deliberate {@link PoseStack#scale} call between each
 * segment, compounding exactly like the original's own nested {@code glScalef} calls did.
 *
 * Real per-segment flail/sway (the original's own {@code sin}-driven wave down the chain, faster and wider
 * while real damage was just taken via {@code hurtTime}), and the real "rising out of the ground" intro
 * animation over the first {@code height*10} real ticks of a segment's life, matching the original's own
 * formulas exactly. Only ever used for the real {@code seed=false} path in the original (confirmed directly
 * - {@code ModelTaintacle} is never actually constructed with {@code seed=true} anywhere in the original
 * codebase), so the original's own dead {@code EntityTaintSeed}-branch isn't reproduced here at all -
 * {@link thaumcraft.common.entities.monster.tainted.EntityTaintSeed}/{@code EntityTaintSeedPrime} use the
 * real, separate {@code ModelTaintSeed} in the original, ported as its own class.
 *
 * Real, documented simplification: {@code flailIntensity} (a real synced float the original bumped on a
 * successful attack, then let decay) isn't ported on {@link EntityTaintacle} itself yet (see that class's
 * own doc comment) - modeled here as a constant 1.0, matching its own real resting value, so the idle
 * sway/rise/scale animation is still fully real, just without the extra post-attack flail intensity spike.
 */
public class ModelTaintacle extends EntityModel<EntityTaintacle> {

	private static final float TAPER = 0.88f;

	private final ModelPart segmentRoot;
	private final ModelPart segmentBody;
	private final ModelPart orb;
	private final ModelPart cap;

	private final int length;
	private final float[] segRotX;
	private final float[] segRotZ;
	private float riseTranslateY;
	private float bodyScale;

	public ModelTaintacle(int length) {
		super(RenderType::entityCutout);
		this.length = length;
		this.segRotX = new float[length - 1];
		this.segRotZ = new float[length - 1];

		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("segment_root", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -4.0f, -4.0f, 8, 8, 8), PartPose.ZERO);
		root.addOrReplaceChild("segment_body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0f, -4.0f, -4.0f, 8, 8, 8), PartPose.ZERO);
		root.addOrReplaceChild("orb", CubeListBuilder.create().texOffs(0, 56).addBox(-2.0f, -2.0f, -2.0f, 4, 4, 4), PartPose.ZERO);
		root.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 32).addBox(-6.0f, -6.0f, -6.0f, 12, 12, 12), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
		segmentRoot = baked.getChild("segment_root");
		segmentBody = baked.getChild("segment_body");
		orb = baked.getChild("orb");
		cap = baked.getChild("cap");
	}

	@Override
	public void setupAnim(EntityTaintacle entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		float flail = 1.0f;
		float ht = entity.hurtTime;
		float mod = 0.0125f; // real scaleFactor(0.0625) * 0.2 from the original - modern setupAnim has no
		// equivalent parameter to read this from, but it was always this same near-constant value in
		// practice anyway (scaleFactor is always 0.0625 in every real render call).
		float fs = 1.0f + (ht > 0.0f ? mod : -mod);
		float fi = flail + (ht > 0.0f ? mod : -mod);
		for (int k = 0; k < length - 1; k++) {
			segRotX[k] = 0.15f * fi * Mth.sin(ageInTicks * 0.1f * fs - k / 2.0f);
			segRotZ[k] = 0.1f / fi * Mth.sin(ageInTicks * 0.15f - k / 2.0f);
		}

		float bbHeight = entity.getBbHeight();
		float hc = bbHeight * 10.0f;
		float rise = 0.0f;
		if (entity.tickCount < hc) {
			rise = (hc - entity.tickCount) / hc * bbHeight;
		}
		riseTranslateY = (bbHeight == 3.0f ? 0.6f : 1.2f) + rise;
		bodyScale = bbHeight / 3.0f;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		poseStack.pushPose();
		poseStack.translate(0.0, riseTranslateY, 0.0);
		poseStack.scale(bodyScale, bodyScale, bodyScale);

		poseStack.pushPose();
		poseStack.translate(0.0, 12.0 / 16.0, 0.0);
		segmentRoot.render(poseStack, buffer, packedLight, packedOverlay, color);
		for (int k = 0; k < length - 1; k++) {
			poseStack.scale(TAPER, TAPER, TAPER);
			poseStack.translate(0.0, -8.0 / 16.0, 0.0);
			poseStack.mulPose(Axis.ZP.rotation(segRotZ[k]));
			poseStack.mulPose(Axis.XP.rotation(segRotX[k]));
			segmentBody.render(poseStack, buffer, packedLight, packedOverlay, color);
		}
		// Real siblings at the very tip - orb and the wide end cap, both real children of the last real
		// body segment (tents[length-2]), one level deeper than the chain itself, matching the original's
		// own real tree shape (not a further chain link).
		poseStack.pushPose();
		poseStack.scale(TAPER, TAPER, TAPER);
		poseStack.translate(0.0, -8.0 / 16.0, 0.0);
		orb.render(poseStack, buffer, packedLight, packedOverlay, color);
		poseStack.popPose();
		poseStack.pushPose();
		poseStack.scale(TAPER, TAPER, TAPER);
		poseStack.translate(0.0, -8.0 / 16.0, 0.0);
		cap.render(poseStack, buffer, packedLight, packedOverlay, color);
		poseStack.popPose();
		poseStack.popPose();
		poseStack.popPose();
	}
}

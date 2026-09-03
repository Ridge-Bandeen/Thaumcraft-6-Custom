package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.golems.IGolemProperties;
import thaumcraft.api.golems.parts.GolemMaterial;
import thaumcraft.api.golems.parts.PartModel;
import thaumcraft.common.golems.EntityThaumcraftGolem;

/**
 * Replaces the old (1.12.2) {@code RenderThaumcraftGolem} - the original loaded each golem part's own
 * {@code .obj} file through a bundled {@code AdvancedModelLoader}/{@code IModelCustom} pair (a from-scratch
 * OBJ reader/renderer, not CodeChicken-lib - the earlier Explore sweep's "CodeChicken blocker" flag turns
 * out to have been about a *different* piece of golem rendering than the OBJ loading itself) and issued one
 * draw call per named sub-group with manual GL matrix pushes for limb swing/rotation.
 *
 * This port renders {@code golem_base.obj} - the material-colored trunk/limbs silhouette every golem has
 * regardless of its assembled parts - plus, as of this chunk, each of the golem's own assembled head/arm/
 * leg/addon {@link PartModel} overlays, all through NeoForge's own real {@code neoforge:obj} model loader
 * instead: one small JSON model per part variant ({@code models/golem/base_<material>.json} for the trunk,
 * {@code models/golem/part_<category>_<key>[_<material>].json} for the overlays - one fixed model if the
 * part has its own {@link PartModel#getTexture()}, one per {@link GolemMaterial} if it doesn't and paints
 * with the golem's own material texture instead), all registered as "additional" models via
 * {@code ThaumcraftClient#onRegisterAdditionalModels} so they bake through the exact same pipeline as any
 * block/item model - same texture-atlas stitching, no bespoke OBJ reader needed - then drawn directly via
 * {@link ModelBlockRenderer#renderModel}, each part its own independent draw call, never merged into one
 * mesh - exactly what sidesteps the original sweep's real "can't dynamically merge geometry without
 * CodeChicken" blocker.
 *
 * <p>Real, documented cuts for this pass: every part is drawn at a single static attach-point offset
 * (matching the original's own resting-pose translate values for BODY/HEAD/ARMS/LEGS), with no walk/swing
 * limb animation, no head look pitch/yaw, no held-item rendering, and no golem-bell dye-color "flag" tint -
 * all real, separately-scoped follow-up work. The original also picked textures per-OBJ-sub-group (any
 * group named {@code "bm*"} used the material texture, everything else used the part's own fixed texture);
 * this port simplifies that to a single whole-part choice - {@link PartModel#getTexture()} if non-null,
 * else the golem's own {@link GolemMaterial#texture} - which is why parts with a null texture need one
 * baked model per material instead of one shared model with per-face texture switching.</p>
 */
public class RenderThaumcraftGolem extends EntityRenderer<EntityThaumcraftGolem> {

	private final ModelBlockRenderer modelRenderer;
	private final ModelManager modelManager;

	public RenderThaumcraftGolem(EntityRendererProvider.Context context) {
		super(context);
		this.modelRenderer = context.getBlockRenderDispatcher().getModelRenderer();
		this.modelManager = context.getModelManager();
	}

	@Override
	public ResourceLocation getTextureLocation(EntityThaumcraftGolem entity) {
		ResourceLocation base = entity.getProperties().getMaterial().texture;
		return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), base.getPath() + ".png");
	}

	@Override
	public void render(EntityThaumcraftGolem entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);

		IGolemProperties props = entity.getProperties();
		GolemMaterial material = props.getMaterial();
		VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.solid());

		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));

		BakedModel baseModel = modelManager.getModel(baseModelLocation(material));
		if (baseModel != modelManager.getMissingModel()) {
			// golem_base.obj's own origin sits at the waist, not the feet - matches the original's own
			// "translate up 0.5 before drawing chest/waist/etc" convention.
			drawAt(baseModel, poseStack, vertexConsumer, packedLight, 0.0, 0.5, 0.0, 0.0f);
		}

		renderAttachedPart("head", props.getHead() == null ? null : props.getHead().key, props.getHead() == null ? null : props.getHead().model,
				material, poseStack, vertexConsumer, packedLight);
		renderAttachedPart("arm", props.getArms() == null ? null : props.getArms().key, props.getArms() == null ? null : props.getArms().model,
				material, poseStack, vertexConsumer, packedLight);
		renderAttachedPart("leg", props.getLegs() == null ? null : props.getLegs().key, props.getLegs() == null ? null : props.getLegs().model,
				material, poseStack, vertexConsumer, packedLight);
		renderAttachedPart("addon", props.getAddon() == null ? null : props.getAddon().key, props.getAddon() == null ? null : props.getAddon().model,
				material, poseStack, vertexConsumer, packedLight);

		poseStack.popPose();
	}

	/**
	 * Draws one assembled part's overlay at its own {@link PartModel.EnumAttachPoint}'s resting-pose
	 * transform. {@code part} is null for slots with no real geometry (the NONE/FIGHTER addons), which is
	 * silently skipped, same as the original's own "nothing to render" case.
	 */
	private void renderAttachedPart(String category, String key, PartModel part, GolemMaterial material,
			PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight) {
		if (key == null || part == null) {
			return;
		}
		BakedModel model = modelManager.getModel(partModelLocation(category, key, part, material));
		if (model == modelManager.getMissingModel()) {
			return;
		}
		switch (part.getAttachPoint()) {
			case HEAD -> drawAt(model, poseStack, vertexConsumer, packedLight, 0.0, 0.75, -0.03125, 0.0f);
			case BODY -> drawAt(model, poseStack, vertexConsumer, packedLight, 0.0, 0.5, 0.0, 0.0f);
			case ARMS -> {
				drawAt(model, poseStack, vertexConsumer, packedLight, 0.20625, 0.6875, 0.0, 0.0f);
				drawAt(model, poseStack, vertexConsumer, packedLight, -0.20625, 0.6875, 0.0, 180.0f);
			}
			case LEGS -> {
				drawAt(model, poseStack, vertexConsumer, packedLight, 0.09375, 0.375, 0.0, 0.0f);
				drawAt(model, poseStack, vertexConsumer, packedLight, -0.09375, 0.375, 0.0, 0.0f);
			}
		}
	}

	private void drawAt(BakedModel model, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
			double x, double y, double z, float extraYRotationDegrees) {
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		if (extraYRotationDegrees != 0.0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(extraYRotationDegrees));
		}
		modelRenderer.renderModel(poseStack.last(), vertexConsumer, null, model, 1.0f, 1.0f, 1.0f, packedLight, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
	}

	private static ModelResourceLocation baseModelLocation(GolemMaterial material) {
		return golemModelLocation("golem/base_" + material.key.toLowerCase());
	}

	/**
	 * Mirrors the naming scheme the {@code part_*.json} models were generated with: {@code
	 * golem/part_<category>_<key>} for a part with its own fixed {@link PartModel#getTexture()}, or {@code
	 * golem/part_<category>_<key>_<material>} for a part that paints with the golem's own material texture
	 * instead (one baked model per {@link GolemMaterial}, since this port's per-part texture choice is
	 * whole-part rather than per-OBJ-group - see this class's own doc comment).
	 */
	private static ModelResourceLocation partModelLocation(String category, String key, PartModel part, GolemMaterial material) {
		String path = "golem/part_" + category + "_" + key.toLowerCase();
		if (part.getTexture() == null) {
			path += "_" + material.key.toLowerCase();
		}
		return golemModelLocation(path);
	}

	private static ModelResourceLocation golemModelLocation(String path) {
		return new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("thaumcraft", path), "standalone");
	}
}

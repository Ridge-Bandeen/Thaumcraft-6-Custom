package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
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
import thaumcraft.api.golems.parts.GolemMaterial;
import thaumcraft.common.golems.EntityThaumcraftGolem;

/**
 * Replaces the old (1.12.2) {@code RenderThaumcraftGolem} - the original loaded each golem part's own
 * {@code .obj} file through a bundled {@code AdvancedModelLoader}/{@code IModelCustom} pair (a from-scratch
 * OBJ reader/renderer, not CodeChicken-lib - the earlier Explore sweep's "CodeChicken blocker" flag turns
 * out to have been about a *different* piece of golem rendering than the OBJ loading itself) and issued one
 * draw call per named sub-group with manual GL matrix pushes for limb swing/rotation.
 *
 * This port renders {@code golem_base.obj} - the material-colored trunk/limbs silhouette every golem has
 * regardless of its assembled parts - through NeoForge's own real {@code neoforge:obj} model loader
 * instead: one small JSON model per {@link GolemMaterial} ({@code models/golem/base_<material>.json},
 * registered as an "additional" model via {@code ThaumcraftClient#onRegisterAdditionalModels} so it bakes
 * through the exact same pipeline as any block/item model - same texture-atlas stitching, no bespoke OBJ
 * reader needed), then draws the resulting real {@link BakedModel} directly via
 * {@link ModelBlockRenderer#renderModel} - each part is its own independent draw call, never merged into
 * one mesh, which is exactly what sidesteps the original sweep's real "can't dynamically merge geometry
 * without CodeChicken" blocker.
 *
 * <p>Real, documented cuts for this first pass (this is "does the OBJ pipeline work at all for golems",
 * not a full recreation of the original's own renderer): only {@code golem_base.obj} is drawn - the
 * assembled head/arm/leg/addon parts' own OBJ overlays ({@link thaumcraft.api.golems.parts.PartModel}) are
 * real, separately-scoped follow-up work, along with the golem-bell dye-color "flag" tint, walk/swing limb
 * animation, and held-item rendering the original had. A golem is now a real, correctly material-colored,
 * statically-posed silhouette instead of being invisible - a solid, verifiable base to build the rest on.
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

		GolemMaterial material = entity.getProperties().getMaterial();
		BakedModel model = modelManager.getModel(baseModelLocation(material));
		if (model == modelManager.getMissingModel()) {
			// The additional model failed to register/bake (a real, non-fatal fallback) - nothing to draw.
			return;
		}

		poseStack.pushPose();
		// golem_base.obj's own origin sits at the waist, not the feet - matches the original's own
		// "translate up 0.5 before drawing chest/waist/etc" convention.
		poseStack.translate(0.0, 0.5, 0.0);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));

		var vertexConsumer = buffer.getBuffer(RenderType.solid());
		modelRenderer.renderModel(poseStack.last(), vertexConsumer, null, model, 1.0f, 1.0f, 1.0f, packedLight, OverlayTexture.NO_OVERLAY);

		poseStack.popPose();
	}

	private static ModelResourceLocation baseModelLocation(GolemMaterial material) {
		return new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("thaumcraft", "golem/base_" + material.key.toLowerCase()), "standalone");
	}
}

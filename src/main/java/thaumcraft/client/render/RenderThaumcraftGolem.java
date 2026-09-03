package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
 * <p>ARMS/LEGS-attach overlay parts get a real walk-swing rotation, matching the original's own
 * {@code cos(limbSwing * 0.6662) * swingAmount} formula (right leg and left arm share one phase, left leg
 * and right arm share the same phase shifted by half a cycle) - driven by {@link
 * net.minecraft.world.entity.LivingEntity#walkAnimation}, the modern equivalent of the original's raw
 * {@code limbSwing}/{@code limbSwingAmount} fields. HEAD/BODY-attach parts, and the base trunk silhouette
 * itself, stay static.
 *
 * <p>The golem-bell dye-color "flag" is a real, separate {@code golem_base_flag.obj} model (the same two
 * faces {@code golem_base.obj} used to include unconditionally in its "flag" group - split out because
 * NeoForge's JSON {@code neoforge:obj} loader has no per-OBJ-group visibility/tint control, so a face this
 * class wants to tint independently needs its own standalone model), drawn only when {@link
 * EntityThaumcraftGolem#getGolemColor()} is set, tinted via this class's own {@code drawAt} tint overload -
 * the same {@code (r,g,b)} multiply {@link ModelBlockRenderer#renderModel} already takes, no vanilla tint-
 * index machinery needed since there's exactly one solid color per draw.
 *
 * <p>A carried main-hand item (real state - {@link EntityThaumcraftGolem}'s own hauler/carry slots, already
 * used by the tasks/seals system to move items between containers) renders above the chest via {@link
 * ItemRenderer#renderStatic}, matching the original's own {@code drawHeldItem} transform stack with its
 * small idle-sway rotation term omitted (see this class's own {@code SWING_PHASE_SCALE} note below for the
 * broader "no idle sway" cut) - which happens to cancel the original's two opposed X-axis rotations
 * entirely, leaving a plain translate/scale/translate.
 *
 * <p>Real, documented cuts for this pass: no head look pitch/yaw, no idle sway/attack wiggle, and no
 * wheeled/flyer alternate arm motion or holding-item arm pose (the arms don't re-pose around a held item,
 * unlike the original) - all real, separately-scoped follow-up work. The original also drew its base
 * model's own "arm" sub-group a second and third time (once per side, swung the same way) regardless of
 * whether an arm part was assembled at all, as a stub limb under any arm overlay; this port's base trunk is
 * one single merged model (see chunk 15's own note above) with no isolated "arm" sub-group to redraw, so
 * only assembled overlay parts swing - a golem with no arm/leg part assigned simply has nothing there to
 * animate, same as it has nothing there to draw at rest. The original also picked textures per-OBJ-sub-group
 * (any group named {@code "bm*"} used the material texture, everything else used the part's own fixed
 * texture); this port simplifies that to a single whole-part choice - {@link PartModel#getTexture()} if
 * non-null, else the golem's own {@link GolemMaterial#texture} - which is why parts with a null texture need
 * one baked model per material instead of one shared model with per-face texture switching.</p>
 */
public class RenderThaumcraftGolem extends EntityRenderer<EntityThaumcraftGolem> {

	private final ModelBlockRenderer modelRenderer;
	private final ModelManager modelManager;
	private final ItemRenderer itemRenderer;

	public RenderThaumcraftGolem(EntityRendererProvider.Context context) {
		super(context);
		this.modelRenderer = context.getBlockRenderDispatcher().getModelRenderer();
		this.modelManager = context.getModelManager();
		this.itemRenderer = context.getItemRenderer();
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
			drawAt(baseModel, poseStack, vertexConsumer, packedLight, 0.0, 0.5, 0.0, 0.0f, 0.0f);
		}

		if (entity.getGolemColor() > 0) {
			BakedModel flagModel = modelManager.getModel(flagModelLocation(material));
			if (flagModel != modelManager.getMissingModel()) {
				// Inverts EntityThaumcraftGolem#mobInteract's own "16 - dye.getDyeColor().getId()" storage
				// mapping back into a real DyeColor.
				DyeColor dye = DyeColor.byId(16 - entity.getGolemColor());
				int packedColor = dye.getTextureDiffuseColor();
				float r = ((packedColor >> 16) & 0xFF) / 255.0f;
				float g = ((packedColor >> 8) & 0xFF) / 255.0f;
				float b = (packedColor & 0xFF) / 255.0f;
				drawAt(flagModel, poseStack, vertexConsumer, packedLight, 0.0, 0.5, 0.0, 0.0f, 0.0f, r, g, b);
			}
		}

		// Modern equivalent of the original's raw limbSwing/limbSwingAmount fields - already interpolated
		// and clamped by the engine, see this class's own doc comment for the swing formula this drives.
		float limbSwing = entity.walkAnimation.position(partialTick);
		float swingAmount = Math.min(entity.walkAnimation.speed(partialTick), 1.0f);

		renderAttachedPart("head", props.getHead() == null ? null : props.getHead().key, props.getHead() == null ? null : props.getHead().model,
				material, poseStack, vertexConsumer, packedLight, limbSwing, swingAmount);
		renderAttachedPart("arm", props.getArms() == null ? null : props.getArms().key, props.getArms() == null ? null : props.getArms().model,
				material, poseStack, vertexConsumer, packedLight, limbSwing, swingAmount);
		renderAttachedPart("leg", props.getLegs() == null ? null : props.getLegs().key, props.getLegs() == null ? null : props.getLegs().model,
				material, poseStack, vertexConsumer, packedLight, limbSwing, swingAmount);
		renderAttachedPart("addon", props.getAddon() == null ? null : props.getAddon().key, props.getAddon() == null ? null : props.getAddon().model,
				material, poseStack, vertexConsumer, packedLight, limbSwing, swingAmount);

		drawHeldItem(entity, poseStack, buffer, packedLight);

		poseStack.popPose();
	}

	/** Matches the original's own {@code drawHeldItem} - see this class's own doc comment for the transform. */
	private void drawHeldItem(EntityThaumcraftGolem entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		ItemStack stack = entity.getMainHandItem();
		if (stack.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.0, 0.625, 0.0);
		poseStack.scale(0.375f, 0.375f, 0.375f);
		poseStack.translate(0.0, 0.25, -1.5);
		if (!(stack.getItem() instanceof BlockItem)) {
			poseStack.translate(0.0, -0.6, 0.0);
		}
		itemRenderer.renderStatic(stack, ItemDisplayContext.HEAD, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), 0);
		poseStack.popPose();
	}

	/** Matches the original's own {@code cos(limbSwing * 0.6662) * swingAmount} swing-phase scale. */
	private static final double SWING_PHASE_SCALE = 0.6662;

	/**
	 * Draws one assembled part's overlay at its own {@link PartModel.EnumAttachPoint}'s resting-pose
	 * transform, swinging ARMS/LEGS parts back and forth as the golem walks. {@code part} is null for slots
	 * with no real geometry (the NONE/FIGHTER addons), which is silently skipped, same as the original's own
	 * "nothing to render" case.
	 */
	private void renderAttachedPart(String category, String key, PartModel part, GolemMaterial material,
			PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, float limbSwing, float swingAmount) {
		if (key == null || part == null) {
			return;
		}
		BakedModel model = modelManager.getModel(partModelLocation(category, key, part, material));
		if (model == modelManager.getMissingModel()) {
			return;
		}
		switch (part.getAttachPoint()) {
			case HEAD -> drawAt(model, poseStack, vertexConsumer, packedLight, 0.0, 0.75, -0.03125, 0.0f, 0.0f);
			case BODY -> drawAt(model, poseStack, vertexConsumer, packedLight, 0.0, 0.5, 0.0, 0.0f, 0.0f);
			case ARMS -> {
				// Right arm and left leg share one swing phase, left arm and right leg share the phase
				// shifted by half a cycle (Math.PI) - same pairing the original's own formula used.
				float rightSwing = (float) Math.toDegrees(Math.cos(limbSwing * SWING_PHASE_SCALE + Math.PI) * swingAmount);
				float leftSwing = (float) Math.toDegrees(Math.cos(limbSwing * SWING_PHASE_SCALE) * swingAmount);
				drawAt(model, poseStack, vertexConsumer, packedLight, 0.20625, 0.6875, 0.0, rightSwing, 0.0f);
				drawAt(model, poseStack, vertexConsumer, packedLight, -0.20625, 0.6875, 0.0, leftSwing, 180.0f);
			}
			case LEGS -> {
				float rightSwing = (float) Math.toDegrees(Math.cos(limbSwing * SWING_PHASE_SCALE) * swingAmount);
				float leftSwing = (float) Math.toDegrees(Math.cos(limbSwing * SWING_PHASE_SCALE + Math.PI) * swingAmount);
				drawAt(model, poseStack, vertexConsumer, packedLight, 0.09375, 0.375, 0.0, rightSwing, 0.0f);
				drawAt(model, poseStack, vertexConsumer, packedLight, -0.09375, 0.375, 0.0, leftSwing, 0.0f);
			}
		}
	}

	private void drawAt(BakedModel model, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
			double x, double y, double z, float swingXRotationDegrees, float extraYRotationDegrees) {
		drawAt(model, poseStack, vertexConsumer, packedLight, x, y, z, swingXRotationDegrees, extraYRotationDegrees, 1.0f, 1.0f, 1.0f);
	}

	/** Tinted overload - used only by the dye-color flag draw above; every other part draws untinted (white). */
	private void drawAt(BakedModel model, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
			double x, double y, double z, float swingXRotationDegrees, float extraYRotationDegrees, float r, float g, float b) {
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		// X-axis swing first, then the per-side Y mirror - matches the original's own rotate-call order.
		if (swingXRotationDegrees != 0.0f) {
			poseStack.mulPose(Axis.XP.rotationDegrees(swingXRotationDegrees));
		}
		if (extraYRotationDegrees != 0.0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(extraYRotationDegrees));
		}
		modelRenderer.renderModel(poseStack.last(), vertexConsumer, null, model, r, g, b, packedLight, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
	}

	private static ModelResourceLocation baseModelLocation(GolemMaterial material) {
		return golemModelLocation("golem/base_" + material.key.toLowerCase());
	}

	private static ModelResourceLocation flagModelLocation(GolemMaterial material) {
		return golemModelLocation("golem/flag_" + material.key.toLowerCase());
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

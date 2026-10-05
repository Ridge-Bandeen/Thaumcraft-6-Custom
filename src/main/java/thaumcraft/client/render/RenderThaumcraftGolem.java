package thaumcraft.client.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemProperties;
import thaumcraft.api.golems.parts.GolemPartAnimators;
import thaumcraft.api.golems.parts.PartModel;
import thaumcraft.common.golems.EntityThaumcraftGolem;

/**
 * Replaces the old (1.12.2) {@code RenderThaumcraftGolem}, as the original: every golem is drawn from OBJ groups
 * ({@link ObjModels}) - the base model's chest, waist, dye flag and stub arms in the golem's material, and each assembled
 * part's groups in its own texture or, for groups named {@code bm*}, the material's - with culling off and alpha
 * blending.
 *
 * <p>The pose is the original's: the body turns with the golem's body yaw (tipping over when it dies), sways with a
 * slow breathing roll, leans into its speed (three times as much for wheeled and flying golems) and into turns; the
 * head follows the look direction; the arms swing opposite the legs while walking (or sway, for wheeled and flying
 * golems), lift to carry when it holds an item, and jerk with an attack; a part may re-aim the arms (dart launchers
 * point along the golem's pitch in combat). Animated parts move their own groups ({@link GolemPartAnimators}: claws,
 * breaker grinders, wheels, the hauler's carried stack). The held item is drawn in front of the chest.
 *
 * <p>Sneaking with a seal or the Golem Bell shows golems you can't see through walls, a faint grey silhouette; an
 * invisible golem shows as a faint ghost to players who can see invisible things.
 */
public class RenderThaumcraftGolem extends EntityRenderer<EntityThaumcraftGolem> {

	private static final ResourceLocation BASE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_base.obj");

	private final ItemRenderer itemRenderer;

	public RenderThaumcraftGolem(EntityRendererProvider.Context context) {
		super(context);
		this.itemRenderer = context.getItemRenderer();
		this.shadowRadius = 0.3f;
	}

	private static ResourceLocation png(ResourceLocation texture) {
		return texture.getPath().endsWith(".png") ? texture : ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), texture.getPath() + ".png");
	}

	@Override
	public ResourceLocation getTextureLocation(EntityThaumcraftGolem entity) {
		return png(entity.getProperties().getMaterial().texture);
	}

	/** How a draw pass gets its vertex consumer for a texture, and the colour it multiplies in. */
	private record Pass(Function<ResourceLocation, VertexConsumer> buffers, float r, float g, float b, float a, boolean withItems) {
	}

	@Override
	public void render(EntityThaumcraftGolem entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		var mc = Minecraft.getInstance();
		boolean visible = !entity.isInvisible();
		boolean ghost = !visible && mc.player != null && !entity.isInvisibleTo(mc.player);
		poseStack.pushPose();
		applyRotations(entity, partialTick, poseStack);
		if (visible) {
			renderParts(entity, partialTick, poseStack, buffer, packedLight, new Pass(t -> buffer.getBuffer(RenderType.entityTranslucent(t)), 1, 1, 1, 1, true));
		} else if (ghost) {
			renderParts(entity, partialTick, poseStack, buffer, packedLight, new Pass(t -> buffer.getBuffer(RenderType.entityTranslucent(t)), 1, 1, 1, 0.15f, true));
		}
		// the original's see-through silhouette for a sneaking seal / Golem Bell holder
		var player = mc.player;
		if (player != null && player.isShiftKeyDown()
				&& (player.getMainHandItem().getItem() instanceof thaumcraft.api.golems.ISealDisplayer || player.getOffhandItem().getItem() instanceof thaumcraft.api.golems.ISealDisplayer)
				&& !player.hasLineOfSight(entity)) {
			renderParts(entity, partialTick, poseStack, buffer, packedLight, new Pass(t -> buffer.getBuffer(GhostType.ghost(t)), 0.25f, 0.25f, 0.25f, 0.25f, false));
		}
		poseStack.popPose();
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}

	/** RenderLivingBase#applyRotations: turn to the body's yaw, and tip over while dying. */
	private static void applyRotations(EntityThaumcraftGolem entity, float pt, PoseStack pose) {
		float bodyYaw = Mth.rotLerp(pt, entity.yBodyRotO, entity.yBodyRot);
		pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
		if (entity.deathTime > 0) {
			float f = Math.min(1.0f, Mth.sqrt((entity.deathTime + pt - 1.0f) / 20.0f * 1.6f));
			pose.mulPose(Axis.ZP.rotationDegrees(f * 90.0f));
		}
	}

	private static EnumMap<PartModel.EnumAttachPoint, List<PartModel>> parts(IGolemProperties props) {
		EnumMap<PartModel.EnumAttachPoint, List<PartModel>> pl = new EnumMap<>(PartModel.EnumAttachPoint.class);
		for (PartModel.EnumAttachPoint p : PartModel.EnumAttachPoint.values()) pl.put(p, new ArrayList<>());
		if (props.getHead() != null && props.getHead().model != null) pl.get(props.getHead().model.getAttachPoint()).add(props.getHead().model);
		if (props.getArms() != null && props.getArms().model != null) pl.get(props.getArms().model.getAttachPoint()).add(props.getArms().model);
		if (props.getLegs() != null && props.getLegs().model != null) pl.get(props.getLegs().model.getAttachPoint()).add(props.getLegs().model);
		if (props.getAddon() != null && props.getAddon().model != null) pl.get(props.getAddon().model.getAttachPoint()).add(props.getAddon().model);
		return pl;
	}

	/** The original's renderParts. */
	private void renderParts(EntityThaumcraftGolem entity, float pt, PoseStack pose, MultiBufferSource buffer, int light, Pass pass) {
		IGolemProperties props = entity.getProperties();
		ResourceLocation matTexture = png(props.getMaterial().texture);
		var pl = parts(props);
		float limbSwing = entity.walkAnimation.position(pt);
		float limbAmount = Math.min(1.0f, entity.walkAnimation.speed(pt));
		float rotFloat = entity.tickCount + pt;
		float headYaw = Mth.rotLerp(pt, entity.yHeadRotO, entity.yHeadRot) - Mth.rotLerp(pt, entity.yBodyRotO, entity.yBodyRot);
		float headPitch = Mth.lerp(pt, entity.xRotO, entity.getXRot());
		float swingProgress = entity.getAttackAnim(pt);
		boolean holding = !entity.getMainHandItem().isEmpty();
		boolean aflag = props.hasTrait(EnumGolemTrait.WHEELED) || props.hasTrait(EnumGolemTrait.FLYER);
		double speed = (entity.getX() - entity.xo) * (entity.getX() - entity.xo) + (entity.getZ() - entity.zo) * (entity.getZ() - entity.zo);

		float f1;
		float bry = 0.0f;
		float rx = (float) Math.toDegrees(Mth.sin(rotFloat * 0.067f) * 0.03f);
		float rz = (float) Math.toDegrees(Mth.cos(rotFloat * 0.09f) * 0.05f + 0.05f);
		float rrx = 0.0f, rry = 0.0f, rrz = 0.0f, rlx = 0.0f, rly = 0.0f, rlz = 0.0f;
		if (holding) {
			rrx = 90.0f - rz / 2.0f;
			rrz = -2.0f;
			rlx = 90.0f - rz / 2.0f;
			rlz = 2.0f;
		} else {
			if (aflag) {
				rrx = rx * 2.0f;
				rlx = -rx * 2.0f;
			} else {
				f1 = Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * 2.0f * limbAmount * 0.5f;
				rrx = (float) (Math.toDegrees(f1) + rx);
				f1 = Mth.cos(limbSwing * 0.6662f) * 2.0f * limbAmount * 0.5f;
				rlx = (float) (Math.toDegrees(f1) - rx);
			}
			rrz += rz + 2.0f;
			rlz -= rz + 2.0f;
		}
		if (swingProgress > 0.0f) {
			float wiggle = -Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI * 2.0f) * 0.2f;
			bry = (float) Math.toDegrees(wiggle);
			rrz = -(float) Math.toDegrees(Mth.sin(wiggle) * 3.0f);
			rrx = (float) Math.toDegrees(-Mth.cos(wiggle) * 5.0f);
			rry += bry;
		}

		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(bry));
		float lean = aflag ? 75.0f : 25.0f;
		pose.mulPose(Axis.XN.rotationDegrees((float) (speed * lean)));
		pose.mulPose(Axis.ZN.rotationDegrees((float) (speed * lean * 0.06 * (entity.getYRot() - entity.yRotO))));

		ObjModels.Model base = ObjModels.get(BASE);
		// body
		pose.pushPose();
		pose.translate(0.0, 0.5, 0.0);
		base.renderGroup("chest", pose.last(), pass.buffers().apply(matTexture), light, OverlayTexture.NO_OVERLAY, pass.r(), pass.g(), pass.b(), pass.a());
		base.renderGroup("waist", pose.last(), pass.buffers().apply(matTexture), light, OverlayTexture.NO_OVERLAY, pass.r(), pass.g(), pass.b(), pass.a());
		if (entity.getGolemColor() > 0) {
			int c = DyeColor.byId(Mth.clamp(entity.getGolemColor() - 1, 0, 15)).getTextureDiffuseColor();
			base.renderGroup("flag", pose.last(), pass.buffers().apply(matTexture), light, OverlayTexture.NO_OVERLAY,
					pass.r() * ((c >> 16) & 255) / 255.0f, pass.g() * ((c >> 8) & 255) / 255.0f, pass.b() * (c & 255) / 255.0f, pass.a());
		}
		for (PartModel part : pl.get(PartModel.EnumAttachPoint.BODY)) renderPart(entity, part, matTexture, pt, PartModel.EnumLimbSide.MIDDLE, pose, buffer, light, pass);
		pose.popPose();
		// head
		pose.pushPose();
		pose.translate(0.0, 0.75, -0.03125);
		pose.mulPose(Axis.YN.rotationDegrees(headYaw));
		pose.mulPose(Axis.XN.rotationDegrees(headPitch));
		for (PartModel part : pl.get(PartModel.EnumAttachPoint.HEAD)) renderPart(entity, part, matTexture, pt, PartModel.EnumLimbSide.MIDDLE, pose, buffer, light, pass);
		base.renderGroup("head", pose.last(), pass.buffers().apply(matTexture), light, OverlayTexture.NO_OVERLAY, pass.r(), pass.g(), pass.b(), pass.a());
		pose.popPose();
		// right arm
		pose.pushPose();
		pose.translate(0.20625, 0.6875, 0.0);
		if (!pl.get(PartModel.EnumAttachPoint.ARMS).isEmpty()) {
			PartModel part = pl.get(PartModel.EnumAttachPoint.ARMS).get(0);
			rrx = part.preRenderArmRotationX(entity, pt, PartModel.EnumLimbSide.RIGHT, rrx);
			rry = part.preRenderArmRotationY(entity, pt, PartModel.EnumLimbSide.RIGHT, rry);
			rrz = part.preRenderArmRotationZ(entity, pt, PartModel.EnumLimbSide.RIGHT, rrz);
		}
		pose.mulPose(Axis.XP.rotationDegrees(rrx));
		pose.mulPose(Axis.YP.rotationDegrees(rry));
		pose.mulPose(Axis.ZP.rotationDegrees(rrz));
		base.renderGroup("arm", pose.last(), pass.buffers().apply(matTexture), light, OverlayTexture.NO_OVERLAY, pass.r(), pass.g(), pass.b(), pass.a());
		for (PartModel part : pl.get(PartModel.EnumAttachPoint.ARMS)) renderPart(entity, part, matTexture, pt, PartModel.EnumLimbSide.RIGHT, pose, buffer, light, pass);
		pose.popPose();
		// left arm
		pose.pushPose();
		pose.translate(-0.20625, 0.6875, 0.0);
		if (!pl.get(PartModel.EnumAttachPoint.ARMS).isEmpty()) {
			PartModel part = pl.get(PartModel.EnumAttachPoint.ARMS).get(0);
			rlx = part.preRenderArmRotationX(entity, pt, PartModel.EnumLimbSide.LEFT, rlx);
			rly = part.preRenderArmRotationY(entity, pt, PartModel.EnumLimbSide.LEFT, rly);
			rlz = part.preRenderArmRotationZ(entity, pt, PartModel.EnumLimbSide.LEFT, rlz);
		}
		pose.mulPose(Axis.XP.rotationDegrees(rlx));
		pose.mulPose(Axis.YP.rotationDegrees(rly + 180.0f));
		pose.mulPose(Axis.ZN.rotationDegrees(rlz));
		base.renderGroup("arm", pose.last(), pass.buffers().apply(matTexture), light, OverlayTexture.NO_OVERLAY, pass.r(), pass.g(), pass.b(), pass.a());
		for (PartModel part : pl.get(PartModel.EnumAttachPoint.ARMS)) renderPart(entity, part, matTexture, pt, PartModel.EnumLimbSide.LEFT, pose, buffer, light, pass);
		pose.popPose();
		// right leg
		pose.pushPose();
		pose.translate(0.09375, 0.375, 0.0);
		pose.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(Mth.cos(limbSwing * 0.6662f) * limbAmount)));
		for (PartModel part : pl.get(PartModel.EnumAttachPoint.LEGS)) renderPart(entity, part, matTexture, pt, PartModel.EnumLimbSide.RIGHT, pose, buffer, light, pass);
		pose.popPose();
		// left leg
		pose.pushPose();
		pose.translate(-0.09375, 0.375, 0.0);
		pose.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * limbAmount)));
		for (PartModel part : pl.get(PartModel.EnumAttachPoint.LEGS)) renderPart(entity, part, matTexture, pt, PartModel.EnumLimbSide.LEFT, pose, buffer, light, pass);
		pose.popPose();
		// held item
		if (pass.withItems()) {
			pose.pushPose();
			pose.translate(0.0, 0.625, 0.0);
			pose.mulPose(Axis.XP.rotationDegrees(90.0f - rz * 0.5f));
			drawHeldItem(entity, pose, buffer, light);
			pose.popPose();
		}
		pose.popPose();
	}

	/** The original's drawHeldItem. */
	private void drawHeldItem(EntityThaumcraftGolem entity, PoseStack pose, MultiBufferSource buffer, int light) {
		ItemStack stack = entity.getMainHandItem();
		if (stack.isEmpty()) return;
		pose.pushPose();
		pose.mulPose(Axis.XN.rotationDegrees(90.0f));
		pose.scale(0.375f, 0.375f, 0.375f);
		pose.translate(0.0f, 0.25f, -1.5f);
		if (!(stack.getItem() instanceof BlockItem)) pose.translate(0.0f, -0.6f, 0.0f);
		itemRenderer.renderStatic(stack, ItemDisplayContext.HEAD, light, OverlayTexture.NO_OVERLAY, pose, buffer, entity.level(), 0);
		pose.popPose();
	}

	/** The original's renderPart: each OBJ group in its own texture, with the part's animation around it. */
	private void renderPart(EntityThaumcraftGolem golem, PartModel part, ResourceLocation matTexture, float pt, PartModel.EnumLimbSide side,
			PoseStack pose, MultiBufferSource buffer, int light, Pass pass) {
		ObjModels.Model model = ObjModels.get(part.getObjModel());
		GolemPartAnimators.Animator animator = GolemPartAnimators.get(part.getObjModel());
		for (String group : model.groupNames()) {
			pose.pushPose();
			ResourceLocation tex = part.useMaterialTextureForObjectPart(group) || part.getTexture() == null ? matTexture : png(part.getTexture());
			if (animator != null) animator.pre(group, golem, pt, side, pose);
			model.renderGroup(group, pose.last(), pass.buffers().apply(tex), light, OverlayTexture.NO_OVERLAY, pass.r(), pass.g(), pass.b(), pass.a());
			if (animator != null && pass.withItems()) animator.post(group, golem, pt, side, pose, buffer, light);
			pose.popPose();
		}
	}

	/** Entity geometry drawn translucent with no depth test - the original's glDisable(GL_DEPTH_TEST) silhouette pass. */
	private static final class GhostType extends RenderType {
		private static final Function<ResourceLocation, RenderType> GHOST = Util.memoize(texture -> create("thaumcraft_golem_ghost",
				DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
				CompositeState.builder()
						.setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
						.setTextureState(new TextureStateShard(texture, false, false))
						.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
						.setCullState(NO_CULL)
						.setDepthTestState(NO_DEPTH_TEST)
						.setWriteMaskState(COLOR_WRITE)
						.setLightmapState(LIGHTMAP)
						.setOverlayState(OVERLAY)
						.createCompositeState(false)));

		static RenderType ghost(ResourceLocation texture) {
			return GHOST.apply(texture);
		}

		private GhostType() {
			super("", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
		}
	}
}

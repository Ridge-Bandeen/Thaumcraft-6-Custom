package thaumcraft.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.EntityPech;

/**
 * Replaces the old (1.12.2) {@code RenderPech} - see {@link EntityPech}'s own doc comment for why this
 * reuses vanilla's own {@link HumanoidModel} (baked from {@link ModelLayers#PLAYER}) rather than porting
 * the original's custom {@code ModelPech} geometry, the same technique {@code RenderCultistKnight} already
 * established. Three real bundled textures, one per {@link EntityPech#getPechType()} (0 = forage/melee,
 * 1 = thaum/wand, 2 = stalker/bow) - matching the original's own three skins exactly.
 */
public class RenderPech extends HumanoidMobRenderer<EntityPech, ModelPech> {

	private static final ResourceLocation FORAGE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/pech_forage.png");
	private static final ResourceLocation THAUM = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/pech_thaum.png");
	private static final ResourceLocation STALKER = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/pech_stalker.png");

	public RenderPech(EntityRendererProvider.Context context) {
		super(context, new ModelPech(ModelPech.createBodyLayer().bakeRoot()), 0.4f);
		addLayer(new HumanoidArmorLayer<>(this,
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
				context.getModelManager()));
		// HumanoidMobRenderer adds its own ItemInHandLayer (the port drew held items twice): swap it for the Pech's own
		layers.removeIf(layer -> layer instanceof ItemInHandLayer);
		addLayer(new PechItemInHandLayer(this, context.getItemInHandRenderer()));
	}

	/** The original's LayerHeldItemPech: vanilla's held-item placement nudged 0.1 down and 1/16 forward into the Pech's
	 * small hands, a bow a further 0.075 inward and 0.1 down. */
	private static final class PechItemInHandLayer extends ItemInHandLayer<EntityPech, ModelPech> {
		private final net.minecraft.client.renderer.ItemInHandRenderer itemRenderer;

		PechItemInHandLayer(RenderPech parent, net.minecraft.client.renderer.ItemInHandRenderer itemRenderer) {
			super(parent, itemRenderer);
			this.itemRenderer = itemRenderer;
		}

		@Override
		protected void renderArmWithItem(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack,
				net.minecraft.world.item.ItemDisplayContext context, net.minecraft.world.entity.HumanoidArm arm,
				com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int light) {
			if (stack.isEmpty()) return;
			poseStack.pushPose();
			getParentModel().translateToHand(arm, poseStack);
			poseStack.translate(0.0f, -0.1f, 0.0625f);
			if (stack.getItem() instanceof net.minecraft.world.item.BowItem) {
				poseStack.translate(-0.075f, -0.1f, 0.0f);
			}
			poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0f));
			poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f));
			boolean left = arm == net.minecraft.world.entity.HumanoidArm.LEFT;
			poseStack.translate((left ? -1 : 1) / 16.0f, 0.125f, -0.625f);
			itemRenderer.renderItem(entity, stack, context, left, poseStack, buffer, light);
			poseStack.popPose();
		}
	}

	@Override
	public ResourceLocation getTextureLocation(EntityPech entity) {
		return switch (entity.getPechType()) {
			case 1 -> THAUM;
			case 2 -> STALKER;
			default -> FORAGE;
		};
	}
}

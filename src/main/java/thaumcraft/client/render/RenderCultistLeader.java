package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.boss.EntityCultistLeader;

/**
 * Renders {@link EntityCultistLeader} - same real shape as {@link RenderCultistKnight}'s own doc comment
 * (plain vanilla-proportioned humanoid, {@code entity/cultist.png} skin reused, real {@link
 * HumanoidArmorLayer} auto-deriving the Crimson Praetor texture from the equipped {@code ArmorItem}'s
 * material). One real difference from the original: a 1.15x render scale (matches the original's own
 * {@code preRenderCallback}), via the real {@code scale(T, PoseStack, float)} override point {@code
 * MobRenderer} exposes (same technique {@code RenderGiantBrainyZombie} already established for its own
 * dynamic scaling, here just a fixed constant).
 *
 * Real, documented cut: the original's own ritualist floaty-line effect field existed in this same
 * renderer class too, but was never actually wired into {@code doRender} for the leader either (dead code
 * in the original, not something this port is dropping) - matches this project's own established "no
 * client FX" simplification regardless.
 */
public class RenderCultistLeader extends HumanoidMobRenderer<EntityCultistLeader, HumanoidModel<EntityCultistLeader>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/cultist.png");

	public RenderCultistLeader(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new HumanoidArmorLayer<>(this,
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
				context.getModelManager()));
		addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
	}

	@Override
	public ResourceLocation getTextureLocation(EntityCultistLeader entity) {
		return TEXTURE;
	}

	@Override
	protected void scale(EntityCultistLeader entity, PoseStack poseStack, float partialTickTime) {
		super.scale(entity, poseStack, partialTickTime);
		poseStack.scale(1.15f, 1.15f, 1.15f);
	}
}

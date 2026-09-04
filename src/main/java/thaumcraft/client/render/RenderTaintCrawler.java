package thaumcraft.client.render;

import net.minecraft.client.model.SilverfishModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import thaumcraft.common.entities.monster.tainted.EntityTaintCrawler;

/**
 * Replaces the old (1.12.2) {@code RenderTaintCrawler} - same real substance, ported to the modern renderer
 * shape: a plain vanilla {@link SilverfishModel} (generic over any {@link net.minecraft.world.entity.Entity},
 * not hardcoded to vanilla's own {@code Silverfish}, so it's reused directly with no custom geometry needed -
 * same technique as {@code net.minecraft.client.renderer.entity.SpiderRenderer} being reused unchanged for
 * {@code EntityMindSpider}, except {@code SilverfishRenderer} itself isn't generic so this thin subclass is
 * still needed) baked from the real vanilla {@link ModelLayers#SILVERFISH} layer, drawn with the original's
 * own bundled {@code textures/entity/crawler.png} (not vanilla's silverfish skin). {@link #getFlipDegrees}
 * (180, real modern replacement for the original's own {@code func_180584_a}) and {@link #scale} (real
 * modern replacement for {@code preRenderCallback}'s {@code 0.7x} shrink) match the original exactly.
 */
public class RenderTaintCrawler extends MobRenderer<EntityTaintCrawler, SilverfishModel<EntityTaintCrawler>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/crawler.png");

	public RenderTaintCrawler(EntityRendererProvider.Context context) {
		super(context, new SilverfishModel<>(context.bakeLayer(ModelLayers.SILVERFISH)), 0.2f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityTaintCrawler entity) {
		return TEXTURE;
	}

	@Override
	protected float getFlipDegrees(EntityTaintCrawler entity) {
		return 180.0f;
	}

	@Override
	protected void scale(EntityTaintCrawler entity, PoseStack poseStack, float partialTick) {
		poseStack.scale(0.7f, 0.7f, 0.7f);
	}
}

package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.EntityEldritchCrab;

/**
 * Renders {@link EntityEldritchCrab} via the real, faithful {@link ModelEldritchCrab} - see that class's own
 * doc comment. Real 0.8x scale, matching the original's own real {@code preRenderCallback}, folded into
 * {@link #scale} (same technique {@code RenderTaintCrawler}/{@code RenderFireBat} already established).
 */
public class RenderEldritchCrab extends MobRenderer<EntityEldritchCrab, ModelEldritchCrab> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/crab.png");

	public RenderEldritchCrab(EntityRendererProvider.Context context) {
		super(context, new ModelEldritchCrab(), 0.5f);
	}

	@Override
	protected void scale(EntityEldritchCrab entity, PoseStack poseStack, float partialTick) {
		poseStack.scale(0.8f, 0.8f, 0.8f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityEldritchCrab entity) {
		return TEXTURE;
	}
}

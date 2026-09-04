package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.construct.EntityTurretCrossbow;

/**
 * Renders {@link EntityTurretCrossbow} via the real, faithful {@link ModelCrossbow} - see that class's own
 * doc comment. Real 0.5x scale, matching the original's own {@code RenderLiving} constructor call exactly.
 */
public class RenderTurretCrossbow extends MobRenderer<EntityTurretCrossbow, ModelCrossbow> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/crossbow.png");

	public RenderTurretCrossbow(EntityRendererProvider.Context context) {
		super(context, new ModelCrossbow(), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityTurretCrossbow entity) {
		return TEXTURE;
	}
}

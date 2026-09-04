package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.construct.EntityArcaneBore;

/**
 * Renders {@link EntityArcaneBore} via the real, faithful {@link ModelArcaneBore} - see that class's own doc
 * comment (including the real, documented mining-beam VFX cut). Real 0.5x scale, matching the original's own
 * {@code RenderLiving} constructor call exactly.
 */
public class RenderArcaneBore extends MobRenderer<EntityArcaneBore, ModelArcaneBore> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/arcanebore.png");

	public RenderArcaneBore(EntityRendererProvider.Context context) {
		super(context, new ModelArcaneBore(), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityArcaneBore entity) {
		return TEXTURE;
	}
}

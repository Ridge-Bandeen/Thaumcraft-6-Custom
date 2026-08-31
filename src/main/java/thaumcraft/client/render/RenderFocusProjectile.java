package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.projectile.EntityFocusProjectile;

/**
 * No visible model, matching this project's established "no client FX" simplification (the original had
 * a particle trail, not a real model either). {@link EntityRenderer#render} still has to be registered for
 * something or the client throws trying to look one up the first time this entity type appears in view -
 * the default {@code render()} implementation already does nothing without a model, so this only needs to
 * supply the one truly abstract method, whose return value is never actually drawn.
 */
public class RenderFocusProjectile extends EntityRenderer<EntityFocusProjectile> {

	public RenderFocusProjectile(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityFocusProjectile entity) {
		return ResourceLocation.withDefaultNamespace("missingno");
	}
}

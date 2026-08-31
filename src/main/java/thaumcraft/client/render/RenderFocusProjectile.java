package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * No visible model for any of the wand's focus-carrier entities (EntityFocusProjectile, EntityFocusMine),
 * matching this project's established "no client FX" simplification (the originals had a particle trail,
 * not a real model either). {@link EntityRenderer#render} still has to be registered for something or the
 * client throws trying to look one up the first time an entity of that type appears in view - the default
 * {@code render()} implementation already does nothing without a model, so this only needs to supply the
 * one truly abstract method, whose return value is never actually drawn. Generic over {@code Entity} since
 * neither of these entity types needs anything type-specific here - one instance covers both.
 */
public class RenderFocusProjectile extends EntityRenderer<Entity> {

	public RenderFocusProjectile(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(Entity entity) {
		return ResourceLocation.withDefaultNamespace("missingno");
	}
}

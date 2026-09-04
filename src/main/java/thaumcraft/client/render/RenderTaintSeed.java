package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;

/**
 * Renders {@link EntityTaintSeed} (and, since {@code EntityTaintSeedPrime} is a real subtype, that too) via
 * the real {@link ModelTaintSeed} - see that class's own doc comment. Real per-registration shadow size,
 * matching the original's own real two-registration shape (0.4 default for the base seed, 0.6 for Prime);
 * the real entity-driven outer scale difference between the two lives inside the shared model itself.
 */
public class RenderTaintSeed extends MobRenderer<EntityTaintSeed, ModelTaintSeed> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/taintseed.png");

	public RenderTaintSeed(EntityRendererProvider.Context context, float shadow) {
		super(context, new ModelTaintSeed(), shadow);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityTaintSeed entity) {
		return TEXTURE;
	}
}

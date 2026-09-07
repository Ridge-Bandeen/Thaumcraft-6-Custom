package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.boss.EntityEldritchWarden;

/**
 * Renders {@link EntityEldritchWarden} via the temporary {@link ModelEldritchPlaceholder} - see that class's
 * own doc comment for why this isn't the real mesh yet (shared with {@code ModelEldritchGuardian} in the
 * original - the Warden had no model of its own). Sized to the entity's own real 0.8x2.25 hitbox, its own
 * real texture keeps it visually distinct from the Guardian despite the shared placeholder shape.
 */
public class RenderEldritchWarden extends MobRenderer<EntityEldritchWarden, ModelEldritchPlaceholder<EntityEldritchWarden>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/eldritch_warden.png");

	public RenderEldritchWarden(EntityRendererProvider.Context context) {
		super(context, new ModelEldritchPlaceholder<>(0.8f, 2.25f), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityEldritchWarden entity) {
		return TEXTURE;
	}
}

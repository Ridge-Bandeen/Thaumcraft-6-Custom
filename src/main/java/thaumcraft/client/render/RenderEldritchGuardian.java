package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;

/**
 * Renders {@link EntityEldritchGuardian} via the temporary {@link ModelEldritchPlaceholder} - see that
 * class's own doc comment for why this isn't the real mesh yet. Sized to the entity's own real 0.8x2.25
 * hitbox.
 */
public class RenderEldritchGuardian extends MobRenderer<EntityEldritchGuardian, ModelEldritchPlaceholder<EntityEldritchGuardian>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/eldritch_guardian.png");

	public RenderEldritchGuardian(EntityRendererProvider.Context context) {
		super(context, new ModelEldritchPlaceholder<>(0.8f, 2.25f), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityEldritchGuardian entity) {
		return TEXTURE;
	}
}

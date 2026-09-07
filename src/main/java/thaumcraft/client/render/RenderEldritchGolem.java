package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.boss.EntityEldritchGolem;

/**
 * Renders {@link EntityEldritchGolem} via the temporary {@link ModelEldritchPlaceholder} - see that class's
 * own doc comment for why this isn't the real mesh yet. Sized to the entity's own real 1.75x3.5 hitbox
 * (matching the original's own much bigger frame vs. the Guardian/Warden). The original's headless variant
 * (a real, separate model swap - see {@link EntityEldritchGolem#isHeadless()}) has no visual distinction
 * here, a real, documented cut alongside the mesh itself.
 */
public class RenderEldritchGolem extends MobRenderer<EntityEldritchGolem, ModelEldritchPlaceholder<EntityEldritchGolem>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/eldritch_golem.png");

	public RenderEldritchGolem(EntityRendererProvider.Context context) {
		super(context, new ModelEldritchPlaceholder<>(1.75f, 3.5f), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityEldritchGolem entity) {
		return TEXTURE;
	}
}

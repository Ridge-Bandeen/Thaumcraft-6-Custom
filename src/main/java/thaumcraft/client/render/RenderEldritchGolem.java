package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.models.entity.ModelEldritchGolem;
import thaumcraft.common.entities.monster.boss.EntityEldritchGolem;

/**
 * Renders {@link EntityEldritchGolem} via the real {@link ModelEldritchGolem} mesh (restored 2026-09-24,
 * replacing the temporary {@code ModelEldritchPlaceholder}, since deleted). Sized to the entity's own real 1.75x3.5 hitbox
 * (matching the original's own much bigger frame vs. the Guardian/Warden). The real headless-vs-normal head
 * swap (see {@link EntityEldritchGolem#isHeadless()}) is handled inside the model itself.
 */
public class RenderEldritchGolem extends MobRenderer<EntityEldritchGolem, ModelEldritchGolem> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/eldritch_golem.png");

	public RenderEldritchGolem(EntityRendererProvider.Context context) {
		super(context, new ModelEldritchGolem(), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityEldritchGolem entity) {
		return TEXTURE;
	}
}

package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.tainted.EntityTaintacle;

/**
 * Renders {@link EntityTaintacle} (and, since {@link thaumcraft.common.entities.monster.tainted.EntityTaintacleSmall}
 * is a real subtype, that too) via the real {@link ModelTaintacle} - see that class's own doc comment. Real
 * per-registration shadow size/chain length, matching the original's own real two-registration shape
 * (length 10/shadow 0.6 for the full tentacle, length 6/shadow 0.2 for the small one).
 */
public class RenderTaintacle extends MobRenderer<EntityTaintacle, ModelTaintacle> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/taintacle.png");

	public RenderTaintacle(EntityRendererProvider.Context context, float shadow, int length) {
		super(context, new ModelTaintacle(length), shadow);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityTaintacle entity) {
		return TEXTURE;
	}
}

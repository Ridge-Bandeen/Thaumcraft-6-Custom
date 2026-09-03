package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;
import thaumcraft.common.entities.monster.EntityThaumicSlime;

/**
 * Renders {@link EntityThaumicSlime} - a thin subclass of vanilla's own {@link SlimeRenderer}, reusing its
 * real model/animation/scale logic entirely (baked from vanilla's own already-registered slime model
 * layers, no new layer definition needed) and overriding only {@link #getTextureLocation} to point at the
 * original mod's own bundled {@code entity/tslime.png} instead of vanilla's slime texture. Every entity type
 * needs a registered renderer or the client throws the first time one comes into view - unlike
 * {@code EntityWisp}/{@code EntityFluxRift}'s own still-real gap (both rare/short-lived), a hostile mob a
 * player will actually run into in normal play needs a real one from the start.
 */
public class RenderThaumicSlime extends SlimeRenderer {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/tslime.png");

	public RenderThaumicSlime(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(Slime entity) {
		return TEXTURE;
	}
}

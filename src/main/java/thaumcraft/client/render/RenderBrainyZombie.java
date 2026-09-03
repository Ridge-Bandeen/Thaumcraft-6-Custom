package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import thaumcraft.common.entities.monster.EntityBrainyZombie;

/**
 * Renders {@link EntityBrainyZombie} - a thin subclass of vanilla's own {@link ZombieRenderer}, same real
 * "reuse vanilla's model/layers, only swap the texture" pattern {@code RenderThaumicSlime} already
 * established, pointing at the original mod's own bundled {@code entity/bzombie.png} instead of vanilla's
 * zombie texture.
 */
public class RenderBrainyZombie extends ZombieRenderer {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/bzombie.png");

	public RenderBrainyZombie(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(Zombie entity) {
		return TEXTURE;
	}
}

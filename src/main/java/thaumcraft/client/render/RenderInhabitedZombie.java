package thaumcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import thaumcraft.common.entities.monster.EntityInhabitedZombie;

/**
 * Renders {@link EntityInhabitedZombie} - same real "thin {@link ZombieRenderer} subclass, texture swap
 * only" pattern {@code RenderBrainyZombie} already established, pointing at the original's own bundled
 * {@code entity/czombie.png} instead of vanilla's zombie texture.
 */
public class RenderInhabitedZombie extends ZombieRenderer {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/czombie.png");

	public RenderInhabitedZombie(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(Zombie entity) {
		return TEXTURE;
	}
}

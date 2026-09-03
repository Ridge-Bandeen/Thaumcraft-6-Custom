package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import thaumcraft.common.entities.monster.EntityGiantBrainyZombie;

/**
 * Renders {@link EntityGiantBrainyZombie} - same {@code ZombieRenderer} texture-swap pattern as
 * {@code RenderBrainyZombie} (pointing at the original mod's own bundled {@code entity/bzombievil.png}
 * instead), plus a real {@link #scale} override matching the entity's own real dynamic hitbox growth
 * ({@link EntityGiantBrainyZombie#getDimensions}) - without it the model would stay normal-sized while the
 * real hitbox grew with anger, a real visual/collision mismatch worth the one extra override.
 */
public class RenderGiantBrainyZombie extends ZombieRenderer {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/bzombievil.png");

	public RenderGiantBrainyZombie(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(Zombie entity) {
		return TEXTURE;
	}

	@Override
	protected void scale(Zombie entity, PoseStack poseStack, float partialTick) {
		super.scale(entity, poseStack, partialTick);
		if (entity instanceof EntityGiantBrainyZombie giant) {
			float factor = 1.0f + giant.getAnger();
			poseStack.scale(factor, factor, factor);
		}
	}
}

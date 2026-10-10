package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import thaumcraft.common.entities.monster.EntityGiantBrainyZombie;

/**
 * Renders {@link EntityGiantBrainyZombie} - the original drew it with {@code RenderBrainyZombie} itself, so the same
 * {@code bzombie.png} (the original's {@code bzombievil.png} field was never used), tinted redder as it angers
 * ({@code 1, 1 - q, 1 - q}, q = min(1, anger) / 2), plus a real {@link #scale} override matching the entity's own real dynamic hitbox growth
 * ({@link EntityGiantBrainyZombie#getDimensions}) - without it the model would stay normal-sized while the
 * real hitbox grew with anger, a real visual/collision mismatch worth the one extra override.
 */
public class RenderGiantBrainyZombie extends ZombieRenderer {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/bzombie.png");

	public RenderGiantBrainyZombie(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(Zombie entity) {
		return TEXTURE;
	}

	@Override
	public void render(Zombie entity, float entityYaw, float partialTick, PoseStack poseStack,
			net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
		if (entity instanceof EntityGiantBrainyZombie giant && giant.getAnger() > 0.0f) {
			float q = Math.min(1.0f, giant.getAnger()) / 2.0f;
			var body = getModel().renderType(getTextureLocation(entity));
			net.minecraft.client.renderer.MultiBufferSource tinted = type -> type == body
					? new ShieldRunesFx.Tint(bufferSource.getBuffer(type), 1.0f, 1.0f - q, 1.0f - q, 1.0f) : bufferSource.getBuffer(type);
			super.render(entity, entityYaw, partialTick, poseStack, tinted, packedLight);
			return;
		}
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
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

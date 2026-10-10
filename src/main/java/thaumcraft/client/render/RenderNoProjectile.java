package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import thaumcraft.common.entities.projectile.EntityFocusProjectile;

/**
 * The original's {@code RenderNoProjectile}, for the focus projectile, the thrown Alumentum and the Causality Collapser:
 * nothing drawn but a 0.1 shadow; they show only as their trails. The focus projectile's trail is spawned from here, as
 * the original: whenever the partial tick has moved on by more than 0.2 since its last mote.
 */
public class RenderNoProjectile extends EntityRenderer<Entity> {

	public RenderNoProjectile(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.1f;
	}

	@Override
	public void render(Entity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		if (entity instanceof EntityFocusProjectile projectile) {
			float since = partialTick - projectile.lastRenderTick;
			if (since < 0.0f) since += 1.0f;
			if (since > 0.2f) projectile.renderParticle(partialTick);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(Entity entity) {
		return ResourceLocation.withDefaultNamespace("missingno");
	}
}

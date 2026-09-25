package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import thaumcraft.common.entities.monster.EntityMindSpider;

/**
 * Real port of the true original's {@code RenderMindSpider} - added 2026-09-25 after the first playtest found
 * the mind spider looked exactly like a normal spider (the port had registered vanilla's own
 * {@link SpiderRenderer} unchanged). The mob is an illusion, so the original drew it as:
 * <ul>
 * <li>a small ghost - scaled to 0.6 - and translucent (alpha faded in up to 0.1, i.e. nearly see-through);
 * modern rendering only offers vanilla's fixed 0.15 alpha "invisible but visible to you" ghost mode
 * ({@code translucent == !isBodyVisible}), used here, which is very close;</li>
 * <li>with the vanilla spider glowing-eyes layer (already part of {@link SpiderRenderer});</li>
 * <li>and only for its "viewer" - the original skipped drawing it entirely unless the entity's synced viewer
 * name was empty or matched the local player (see {@link EntityMindSpider#getViewer}).</li>
 * </ul>
 */
public class RenderMindSpider extends SpiderRenderer<EntityMindSpider> {

	public RenderMindSpider(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5f;
	}

	@Override
	protected void scale(EntityMindSpider entity, PoseStack poseStack, float partialTick) {
		poseStack.scale(0.6f, 0.6f, 0.6f);
	}

	/** {@code false} switches vanilla's living-entity renderer into its translucent ghost mode. */
	@Override
	protected boolean isBodyVisible(EntityMindSpider entity) {
		return false;
	}

	@Override
	public void render(EntityMindSpider entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		String viewer = entity.getViewer();
		var player = Minecraft.getInstance().player;
		if (viewer.isEmpty() || (player != null && viewer.equals(player.getName().getString()))) {
			super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
		}
	}
}

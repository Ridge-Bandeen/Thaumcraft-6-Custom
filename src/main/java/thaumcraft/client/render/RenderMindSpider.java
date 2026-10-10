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

	/** As the original: the body is drawn faintly (alpha up to 0.1, reached over its first 10 ticks) without writing
	 * depth; the eyes layer draws as usual. */
	@Override
	protected net.minecraft.client.renderer.RenderType getRenderType(EntityMindSpider entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		return Faint.get(getTextureLocation(entity));
	}

	@Override
	public void render(EntityMindSpider entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		String viewer = entity.getViewer();
		var player = Minecraft.getInstance().player;
		if (viewer.isEmpty() || (player != null && viewer.equals(player.getName().getString()))) {
			float alpha = Math.min(0.1f, entity.tickCount / 100.0f);
			var body = Faint.get(getTextureLocation(entity));
			MultiBufferSource faint = type -> type == body
					? new ShieldRunesFx.Tint(buffer.getBuffer(type), 1.0f, 1.0f, 1.0f, alpha) : buffer.getBuffer(type);
			super.render(entity, entityYaw, partialTick, poseStack, faint, packedLight);
		}
	}

	/** Entity translucency without depth writes (the original's glDepthMask(false)). */
	private static final class Faint extends net.minecraft.client.renderer.RenderType {
		private static final java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.client.renderer.RenderType> CACHE = new java.util.HashMap<>();

		static net.minecraft.client.renderer.RenderType get(net.minecraft.resources.ResourceLocation texture) {
			return CACHE.computeIfAbsent(texture, tex -> create("thaumcraft_mind_spider", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
					com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, true, true, CompositeState.builder()
							.setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
							.setTextureState(new TextureStateShard(tex, false, false))
							.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
							.setCullState(NO_CULL)
							.setWriteMaskState(COLOR_WRITE)
							.setLightmapState(LIGHTMAP)
							.setOverlayState(OVERLAY)
							.createCompositeState(true)));
		}

		private Faint() {
			super("", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 0, false, false,
					() -> {}, () -> {});
		}
	}
}

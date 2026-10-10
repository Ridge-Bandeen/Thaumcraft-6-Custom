package thaumcraft.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.monster.cult.EntityCultistCleric;
import thaumcraft.common.entities.monster.cult.EntityCultistKnight;

/**
 * Renders both {@link EntityCultistKnight} and {@link EntityCultistCleric} - the original's own single
 * {@code RenderCultist} class covered both ranks too (same {@code entity/cultist.png} skin either way,
 * differentiated only by whichever armor happens to be worn), so this stays generic over {@link
 * EntityCultist} rather than splitting into two near-identical renderer classes. Kept its original
 * "Knight"-only name since it shipped that way first (see this arc's own earlier commit) - purely
 * historical, not a scope limitation any more.
 *
 * The original's own {@code RenderCultist} used a plain vanilla {@code ModelBiped} (no custom geometry)
 * with a {@code LayerBipedArmor} also built from plain {@code ModelBiped}s, i.e. a completely ordinary
 * humanoid renderer. The modern equivalent of "plain humanoid, wide arms" is {@link ModelLayers#PLAYER}
 * (not {@code PLAYER_SLIM} - that's the thin-armed "Alex" variant) for both the body and the {@link
 * HumanoidArmorLayer}'s inner/outer models, plus a real {@link ItemInHandLayer} for the held item.
 * {@link HumanoidArmorLayer} auto-derives the worn texture from whatever {@code ArmorMaterial} the
 * equipped {@code ArmorItem} declares (see {@code ThaumcraftArmorMaterials#CULTIST_PLATE}/{@code
 * #CULTIST_ROBE}/{@code #CULTIST_BOOTS}) - no per-item texture override needed, unlike the original's own
 * {@code getArmorTexture}.
 *
 * A ritualist cleric, as the original: it floats, bobbing 0.11 to 0.31 above the ground on its own phase, and two
 * dark ribbons ({@code wispy.png}, colour 0x110011 at 0.8 alpha, one upright and one flat, 0.5 wide) run from the
 * altar point (home + 0.5, 1.5, 0.5) to its eyes, waving and scrolling, growing out over its first 10 ticks. Drawn
 * blended without writing depth, culled (each ribbon shows one face, as the original's culling left it).
 */
public class RenderCultistKnight<T extends EntityCultist> extends HumanoidMobRenderer<T, HumanoidModel<T>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/cultist.png");

	public RenderCultistKnight(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new HumanoidArmorLayer<>(this,
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
				context.getModelManager()));
		addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return TEXTURE;
	}

	private static final ResourceLocation WISPY = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/wispy.png");

	@Override
	public void render(T entity, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
			net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
		if (!(entity instanceof EntityCultistCleric cleric) || !cleric.getIsRitualist()) {
			super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
			return;
		}
		int phase = new java.util.Random(entity.getId()).nextInt(1000);
		float bob = net.minecraft.util.Mth.sin((entity.tickCount + partialTick + phase) / 9.0f) * 0.1f + 0.21f;
		poseStack.pushPose();
		poseStack.translate(0.0, bob, 0.0);
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
		var home = cleric.getRestrictCenter();
		double ex = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
		double ey = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY());
		double ez = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());
		double hx = home.getX() + 0.5;
		double hy = home.getY() + 1.5 - bob;
		double hz = home.getZ() + 0.5;
		poseStack.translate(hx - ex, hy - ey, hz - ez);
		drawFloatyLine(poseStack, entity.getX(), entity.getY() + entity.getEyeHeight() * 1.2f, entity.getZ(), hx, hy, hz,
				-0.03f, Math.min(entity.tickCount, 10) / 10.0f, 0.25f);
		poseStack.popPose();
	}

	/** The original's drawFloatyLine, with the pose already at the line's start ({@code x2, y2, z2}). */
	private static void drawFloatyLine(com.mojang.blaze3d.vertex.PoseStack poseStack, double x, double y, double z, double x2, double y2, double z2,
			float speed, float distance, float width) {
		float time = (float) (System.nanoTime() / 30000000L);
		float r = 0x11 / 255.0f;
		float g = 0.0f;
		float b = 0x11 / 255.0f;
		double dcx = (float) (x - x2);
		double dcy = (float) (y - y2);
		double dcz = (float) (z - z2);
		float dist = (float) Math.sqrt(dcx * dcx + dcy * dcy + dcz * dcz);
		float length = Math.round(dist) * 6.0f;
		if (length * distance < 1.0f) return;
		var m = poseStack.last().pose();
		com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
		com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, WISPY);
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
		com.mojang.blaze3d.systems.RenderSystem.enableCull();
		com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
		for (int pass = 0; pass < 2; pass++) {
			var builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLE_STRIP,
					com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
			for (int i = 0; i <= length * distance; i++) {
				float f11 = i / length;
				float f12 = 1.0f - Math.abs(i - length / 2.0f) / (length / 2.0f);
				float wave = dist * (1.0f - f11) * 6.0f - time % 32767.0f / 5.0f;
				double dx = dcx + net.minecraft.util.Mth.sin((float) ((z % 16.0 + wave) / 4.0)) * 0.5f * f12;
				double dy = dcy + net.minecraft.util.Mth.sin((float) ((x % 16.0 + wave) / 3.0)) * 0.5f * f12;
				double dz = dcz + net.minecraft.util.Mth.sin((float) ((y % 16.0 + wave) / 2.0)) * 0.5f * f12;
				float u = (1.0f - f11) * dist - time * speed;
				float px = (float) (dx * f11);
				float py = (float) (dy * f11);
				float pz = (float) (dz * f11);
				if (pass == 0) {
					builder.addVertex(m, px, py - width, pz).setUv(u, 1.0f).setColor(r, g, b, 0.8f);
					builder.addVertex(m, px, py + width, pz).setUv(u, 0.0f).setColor(r, g, b, 0.8f);
				} else {
					builder.addVertex(m, px - width, py, pz).setUv(u, 1.0f).setColor(r, g, b, 0.8f);
					builder.addVertex(m, px + width, py, pz).setUv(u, 0.0f).setColor(r, g, b, 0.8f);
				}
			}
			com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.buildOrThrow());
		}
		com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
		com.mojang.blaze3d.systems.RenderSystem.disableBlend();
	}

	@Override
	public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ) {
		// the ritual lines reach back to the altar
		return super.shouldRender(entity, frustum, camX, camY, camZ)
				|| entity instanceof EntityCultistCleric cleric && cleric.getIsRitualist()
						&& frustum.isVisible(entity.getBoundingBox().minmax(new net.minecraft.world.phys.AABB(cleric.getRestrictCenter())));
	}
}

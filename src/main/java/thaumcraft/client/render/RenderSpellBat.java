package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.EntitySpellBat;

/**
 * Renders {@link EntitySpellBat} via the real, shared {@link ModelFireBat} - see that class's own doc
 * comment. Real 0.35x scale and a fixed vertical offset, matching the original's own real {@code
 * preRenderCallback}/{@code applyRotations} (folded into {@link #scale}, same technique {@link
 * RenderFireBat} uses).
 *
 * As the original it is drawn half see-through in its spell's colour ({@link EntitySpellBat#getColor}), at full
 * light.
 */
public class RenderSpellBat extends MobRenderer<EntitySpellBat, ModelFireBat<EntitySpellBat>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/spellbat.png");

	public RenderSpellBat(EntityRendererProvider.Context context) {
		super(context, new ModelFireBat<>() {
			@Override
			public void renderToBuffer(PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
				super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, tint);
			}
		}, 0.25f);
	}

	/** The colour the model is drawn in this frame: the bat's, at half alpha. */
	private static int tint = 0x80FFFFFF;

	@Override
	public void render(EntitySpellBat entity, float entityYaw, float partialTicks, PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
		tint = 0x80000000 | (entity.getColor() & 0xFFFFFF);
		super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
	}

	@javax.annotation.Nullable
	@Override
	protected net.minecraft.client.renderer.RenderType getRenderType(EntitySpellBat entity, boolean bodyVisible, boolean translucent, boolean glowing) {
		return net.minecraft.client.renderer.RenderType.entityTranslucent(getTextureLocation(entity));
	}

	@Override
	protected int getBlockLightLevel(EntitySpellBat entity, net.minecraft.core.BlockPos pos) {
		return 15;
	}

	@Override
	protected void scale(EntitySpellBat entity, PoseStack poseStack, float partialTick) {
		poseStack.translate(0.0, -0.1, 0.0);
		poseStack.scale(0.35f, 0.35f, 0.35f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntitySpellBat entity) {
		return TEXTURE;
	}
}

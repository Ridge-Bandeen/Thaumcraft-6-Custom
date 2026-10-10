package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.monster.EntityFireBat;

/**
 * Renders {@link EntityFireBat} via the real, shared {@link ModelFireBat} - see that class's own doc
 * comment. Real 0.35x scale and real per-tick body bob (or a fixed hanging offset while really hanging),
 * matching the original's own real {@code preRenderCallback}/{@code applyRotations} exactly, both folded
 * into {@link #scale} - the modern replacement for that same original pre-render hook, same technique
 * {@code RenderTaintCrawler} already established this session.
 */
public class RenderFireBat extends MobRenderer<EntityFireBat, ModelFireBat<EntityFireBat>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/firebat.png");

	public RenderFireBat(EntityRendererProvider.Context context) {
		super(context, new ModelFireBat<>(), 0.25f);
	}

	/** As the original's applyRotations: bobbing in flight, or 0.1 lower while hanging, before the body turn (and so
	 * before the model's upside-down flip). */
	@Override
	protected void setupRotations(EntityFireBat entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
		if (entity.isHanging()) {
			poseStack.translate(0.0, -0.1, 0.0);
		} else {
			poseStack.translate(0.0, Mth.cos(bob * 0.3f) * 0.1, 0.0);
		}
		super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
	}

	@Override
	protected void scale(EntityFireBat entity, PoseStack poseStack, float partialTick) {
		poseStack.scale(0.35f, 0.35f, 0.35f);
	}

	@Override
	public ResourceLocation getTextureLocation(EntityFireBat entity) {
		return TEXTURE;
	}

	/** As the original, a fire bat glows: always drawn at full light. */
	@Override
	protected int getBlockLightLevel(EntityFireBat entity, net.minecraft.core.BlockPos pos) {
		return 15;
	}
}

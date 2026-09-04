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
 * Real, documented cut: no translucent per-cast color tint - the original's own version averaged the real
 * focus effects' colors client-side into a synced field, but {@code EntitySpellBat} itself never ported
 * that field (see its own doc comment - the color computation only ever fed the also-unported particle
 * dispatcher, so there was nothing left for it to drive). Renders with a plain, untinted texture instead.
 */
public class RenderSpellBat extends MobRenderer<EntitySpellBat, ModelFireBat<EntitySpellBat>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/spellbat.png");

	public RenderSpellBat(EntityRendererProvider.Context context) {
		super(context, new ModelFireBat<>(), 0.25f);
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

package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.tiles.crafting.TileThaumatorium;

/**
 * Renders {@link TileThaumatorium}'s own real crafting-result preview - a floating item showing what the
 * currently-inserted catalyst will craft into, via the real {@link TileThaumatorium#getPreviewResult()}
 * accessor added alongside this renderer. Real, documented simplification vs. the original's own real
 * renderer: the original cycled through every recipe the player had curated for this device (its own real
 * {@code recipeHash} list, one entry visible at a time, swapping every 20 ticks); this port's own real
 * {@code TileThaumatorium} only ever has one real candidate recipe at a time (deterministic first-match
 * instead of player curation - see that class's own doc comment), so there's nothing to cycle through, just
 * a real static preview. Also no facing-based offset - the real modern {@code BlockThaumatorium} is
 * non-directional, so the preview renders centered above the block instead of toward one side.
 */
public class TileThaumatoriumRenderer implements BlockEntityRenderer<TileThaumatorium> {

	public TileThaumatoriumRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileThaumatorium tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		ItemStack preview = tile.getPreviewResult();
		if (preview.isEmpty()) return;

		poseStack.pushPose();
		poseStack.translate(0.5, 1.125, 0.5);
		poseStack.scale(0.75f, 0.75f, 0.75f);
		Minecraft.getInstance().getItemRenderer().renderStatic(preview, ItemDisplayContext.GROUND, packedLight, packedOverlay,
				poseStack, bufferSource, tile.getLevel(), 0);
		poseStack.popPose();
	}
}

package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;
import thaumcraft.common.tiles.crafting.TilePedestal;

/**
 * Renders {@link TilePedestal}'s real held item floating and slowly spinning above it - real port of the
 * original's own {@code TilePedestalRenderer}. The original spawned a throwaway real {@code EntityItem}
 * purely to reuse its own render code; the modern real {@link
 * net.minecraft.client.renderer.entity.ItemRenderer#renderStatic} does the same real "draw an item stack in
 * the world" job directly, no fake entity needed - real, deliberate modernization, not a visual change (same
 * 1.25x scale, same real spin rate as the original).
 */
public class TilePedestalRenderer implements BlockEntityRenderer<TilePedestal> {

	public TilePedestalRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TilePedestal tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		ItemStack held = tile.getItem();
		if (held.isEmpty()) return;

		Minecraft mc = Minecraft.getInstance();
		float ticks = (mc.player != null ? mc.player.tickCount : 0) + partialTick;

		poseStack.pushPose();
		poseStack.translate(0.5, 0.75, 0.5);
		poseStack.scale(1.25f, 1.25f, 1.25f);
		poseStack.mulPose(Axis.YP.rotationDegrees(ticks % 360.0f));
		// as the original, drawn as a fresh dropped item (one of the stack): lifted by its bob and a quarter of its ground scale
		ItemStack one = held.copyWithCount(1);
		TileFocalManipulatorRenderer.renderAsDropped(mc, tile.getLevel(), one, 0.0f, poseStack, bufferSource, packedLight, packedOverlay);
		poseStack.popPose();
	}
}

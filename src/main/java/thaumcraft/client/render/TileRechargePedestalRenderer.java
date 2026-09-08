package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;
import thaumcraft.common.tiles.devices.TileRechargePedestal;

/**
 * Renders {@link TileRechargePedestal}'s real held item floating and slowly spinning above it - {@link
 * TilePedestalRenderer}'s own real 1.5x-scale sibling (matches the original's own real
 * {@code TileRechargePedestalRenderer}, identical to the plain Pedestal's renderer but for the scale).
 */
public class TileRechargePedestalRenderer implements BlockEntityRenderer<TileRechargePedestal> {

	public TileRechargePedestalRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileRechargePedestal tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		ItemStack held = tile.getItem();
		if (held.isEmpty()) return;

		Minecraft mc = Minecraft.getInstance();
		float ticks = (mc.player != null ? mc.player.tickCount : 0) + partialTick;

		poseStack.pushPose();
		poseStack.translate(0.5, 0.75, 0.5);
		poseStack.scale(1.5f, 1.5f, 1.5f);
		poseStack.mulPose(Axis.YP.rotationDegrees(ticks % 360.0f));
		mc.getItemRenderer().renderStatic(held, ItemDisplayContext.GROUND, packedLight, packedOverlay,
				poseStack, bufferSource, tile.getLevel(), 0);
		poseStack.popPose();
	}
}

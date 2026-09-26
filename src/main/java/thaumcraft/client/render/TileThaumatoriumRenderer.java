package thaumcraft.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.common.blocks.crafting.BlockThaumatorium;
import thaumcraft.common.tiles.crafting.TileThaumatorium;

/**
 * Renders {@link TileThaumatorium}'s floating preview above its front (the side it faces): the output of each recipe
 * the player has selected, one at a time, swapping every second - as in the original (restored 2026-09-25 along with
 * the facing and the curated-recipe list; it used to show a single static item centred above the block).
 */
public class TileThaumatoriumRenderer implements BlockEntityRenderer<TileThaumatorium> {

	public TileThaumatoriumRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileThaumatorium tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (tile.getLevel() == null || Minecraft.getInstance().player == null) return;
		List<ResourceLocation> selected = tile.getSelected();
		if (selected.isEmpty()) return;
		int index = Minecraft.getInstance().player.tickCount / 20 % selected.size();
		ItemStack preview = tile.getLevel().getRecipeManager().byKey(selected.get(index))
				.map(holder -> holder.value() instanceof CrucibleRecipe recipe ? recipe.getRecipeOutput().copyWithCount(1) : ItemStack.EMPTY)
				.orElse(ItemStack.EMPTY);
		if (preview.isEmpty()) return;

		Direction facing = tile.getBlockState().hasProperty(BlockThaumatorium.FACING) ? tile.getBlockState().getValue(BlockThaumatorium.FACING) : Direction.NORTH;
		poseStack.pushPose();
		// 1.125 is the original height, but it drew the item through the old entity-item renderer, which lifted it by its ground
		// offset plus a hover bob (about 0.25); renderStatic does neither, so the lift is added here (playtest 2026-09-25).
		poseStack.translate(0.5 + facing.getStepX() / 1.99, 1.125 + 0.25, 0.5 + facing.getStepZ() / 1.99);
		switch (facing) {
			case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
			case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(270.0f));
			case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
			default -> {
			}
		}
		poseStack.scale(0.75f, 0.75f, 0.75f);
		Minecraft.getInstance().getItemRenderer().renderStatic(preview, ItemDisplayContext.GROUND, packedLight, packedOverlay,
				poseStack, bufferSource, tile.getLevel(), 0);
		poseStack.popPose();
	}
}

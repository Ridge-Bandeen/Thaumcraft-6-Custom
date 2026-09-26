package thaumcraft.common.blocks.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.tiles.misc.TileBanner;

/**
 * Port of the original's {@code BlockBannerTCItem#onItemUse}: banners couldn't be placed on a block's underside,
 * a floor banner turned to face the player (16 steps of yaw), and a wall banner set itself against the wall it
 * was placed on. The port had registered plain block items that never touched the tile, so every banner ended up
 * facing the same way (found in the devices pass of the first playtest, 2026-09-25).
 */
public class BannerBlockItem extends BlockItem {

	public BannerBlockItem(BlockBannerTC block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		if (context.getClickedFace() == Direction.DOWN) {
			return InteractionResult.FAIL;
		}
		InteractionResult result = super.place(context);
		if (result.consumesAction() && !context.getLevel().isClientSide) {
			BlockPos pos = context.getClickedPos();
			if (context.getLevel().getBlockEntity(pos) instanceof TileBanner tile) {
				Direction side = context.getClickedFace();
				if (side == Direction.UP) {
					float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() : 0.0f;
					tile.setBannerFacing((byte) (Mth.floor((yaw + 180.0f) * 16.0f / 360.0f + 0.5) & 15));
				} else {
					tile.setWall(true);
					tile.setBannerFacing((byte) switch (side) {
						case NORTH -> 8;
						case WEST -> 4;
						case EAST -> 12;
						default -> 0;
					});
				}
				AspectList aspects = context.getItemInHand().get(ThaumcraftDataComponents.ASPECTS.get());
				if (aspects != null && aspects.getAspects().length > 0) {
					tile.setAspect(aspects.getAspects()[0]);
				}
				tile.setChanged();
				context.getLevel().sendBlockUpdated(pos, tile.getBlockState(), tile.getBlockState(), 3);
			}
		}
		return result;
	}
}
